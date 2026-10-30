package expo.modules.mirrorview

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.view.ViewCompat
import androidx.lifecycle.LifecycleOwner
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The front camera filling the view, with nothing on top of it except a value
 * while it changes. See [MirrorGestures] for what the fingers do, [PinchZoom]
 * for the zoom, [Exposure] for the light and the renderers for the pixels.
 */
@SuppressLint("ViewConstructor")
class MirrorView(context: Context, appContext: AppContext) :
  ExpoView(context, appContext), MirrorGestures.Listener {
  private val onShortcutsGesture by EventDispatcher()
  private val onShortcutsDrag by EventDispatcher()
  private val onFreezeChange by EventDispatcher()
  private val onHudFlip by EventDispatcher()
  private val onCameraInfo by EventDispatcher()

  // Settings, set from JS. Applied together in applySettings().
  var autoLight = true
  var lightSensitivity = Exposure.DEFAULT_SENSITIVITY
  var facePriority = true
  var highResolution = true
  var gpuSharpening = false
  var sharpening = Sharpening.NATURAL
  var trueView = false
  var zoomHaptics = true
  var dragInertia = true
  var restoresZoom = true
  var volumeZoom = true
  var fillLight = false

  /**
   * Holds the picture and clips it. With the fill light on it sits inside a
   * white border, and a zoomed picture must not spill over that border.
   */
  private val frame = object : ViewGroup(context) {
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit
  }
  private var imageLeft = 0
  private var imageTop = 0
  private var appliedFillLight = false

  private var renderer: MirrorRenderer = PreviewRenderer(context)
  private val zoom = PinchZoom { renderer.onZoomChanged(it) }
  private val indicator = ValueIndicator(context)
  private val gestures = MirrorGestures(context, this, this)
  private val exposure = Exposure(this)
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  private var cameraProvider: ProcessCameraProvider? = null
  private var camera: Camera? = null
  private var preview: Preview? = null
  private var settingsApplied = false
  private var boundGpu: Boolean? = null
  private var boundHighResolution: Boolean? = null
  private var gpuFailed = false

  private var laidOutWidth = 0
  private var laidOutHeight = 0
  private var animator: ValueAnimator? = null

  /**
   * The phone held upside down. The screen does not turn (most phones never
   * rotate an app by 180°), and it does not have to for the picture: camera and
   * screen turn together, so the face still shows the right way up. Only the
   * controls do: the zoom circle, the edge gestures and, on the JS side, the
   * shortcuts and the settings, so they stay where the thumb is.
   */
  private var hudFlipped = false
  private var pendingFlip: Boolean? = null
  private val confirmFlip = Runnable {
    pendingFlip?.let { setHudFlipped(it) }
    pendingFlip = null
  }
  private val orientationListener = object : OrientationEventListener(context) {
    override fun onOrientationChanged(degrees: Int) {
      if (degrees == ORIENTATION_UNKNOWN) {
        return
      }
      val upsideDown = when {
        abs(degrees - 180) <= FLIP_WITHIN_DEGREES -> true
        degrees <= FLIP_WITHIN_DEGREES || degrees >= 360 - FLIP_WITHIN_DEGREES -> false
        // On its side: keep whatever it was.
        else -> return
      }
      if (upsideDown == hudFlipped) {
        removeCallbacks(confirmFlip)
        pendingFlip = null
      } else if (pendingFlip != upsideDown) {
        // Held for a moment first, so a wobble does not flip the controls.
        pendingFlip = upsideDown
        removeCallbacks(confirmFlip)
        postDelayed(confirmFlip, FLIP_CONFIRM_MS)
      }
    }
  }

  /** The last zoomed state, which a double tap at 1× goes back to. */
  private var lastZoomed: FloatArray? = null
  private val meterSoon = Runnable { meter() }

  /** Where the zoom was left, as fractions of the view, to open there again. */
  private val savedZoom = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
  private var zoomRestored = false

  /** The volume keys zoom in steps; the circle stays up while they are pressed. */
  private val endVolumeZoom = Runnable {
    indicator.hide()
    afterMove()
  }

  /** Keeps a quick drag going after the finger leaves, slowing down. */
  private val scroller = OverScroller(context)
  private val flingStep = object : Runnable {
    override fun run() {
      if (scroller.computeScrollOffset()) {
        zoom.set(zoom.scale, scroller.currX.toFloat(), scroller.currY.toFloat())
        postOnAnimation(this)
      } else {
        afterMove()
      }
    }
  }

  init {
    // Focused, so the volume keys come here before the system turns them into
    // a volume change.
    isFocusable = true
    isFocusableInTouchMode = true
    // The focus is only for the keys: nothing to draw for it.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      defaultFocusHighlightEnabled = false
    }
    addView(frame, matchParent())
    frame.addView(renderer.view, matchParent())
    // A sibling of the image, so the zoom never scales or moves it.
    addView(indicator, ViewGroup.LayoutParams(indicator.diameter, indicator.diameter))
  }

  fun applySettings() {
    settingsApplied = true
    zoom.flipped = trueView
    exposure.configure(autoLight, lightSensitivity, facePriority)
    val wantGpu = gpuSharpening && !gpuFailed
    if (boundGpu != null && boundGpu != wantGpu) {
      swapRenderer(wantGpu)
    }
    renderer.setSharpening(sharpening)
    applyFillLight()
    if (boundGpu != wantGpu || boundHighResolution != highResolution) {
      bindCamera()
    }
    if (!restoresZoom && savedZoom.contains(KEY_SCALE)) {
      savedZoom.edit().clear().apply()
    }
    restoreZoom()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    val width = right - left
    val height = bottom - top
    if (width != laidOutWidth || height != laidOutHeight) {
      laidOutWidth = width
      laidOutHeight = height
      layoutContent(width, height)
    }
  }

  private fun layoutContent(width: Int, height: Int) {
    // The picture fills the view, or sits inside the fill light's border.
    val border = if (fillLight) (minOf(width, height) * FILL_LIGHT_BORDER).toInt() else 0
    val imageWidth = width - 2 * border
    val imageHeight = height - 2 * border
    imageLeft = border
    imageTop = border
    frame.measure(
      MeasureSpec.makeMeasureSpec(imageWidth, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(imageHeight, MeasureSpec.EXACTLY)
    )
    frame.layout(border, border, border + imageWidth, border + imageHeight)
    renderer.layout(imageWidth, imageHeight)
    zoom.setSize(imageWidth.toFloat(), imageHeight.toFloat())
    restoreZoom()
    layoutControls(width, height)
  }

  /** The zoom circle and the edge kept from the back gesture, which turn over with the phone. */
  private fun layoutControls(width: Int, height: Int) {
    val size = indicator.diameter
    // 27% up from the bottom as the phone is held, wherever that bottom is.
    val fromTop = if (hudFlipped) INDICATOR_FROM_BOTTOM else 1 - INDICATOR_FROM_BOTTOM
    val centreY = (height * fromTop).toInt()
    indicator.measure(
      MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
    )
    val indicatorLeft = (width - size) / 2
    val indicatorTop = centreY - size / 2
    indicator.layout(indicatorLeft, indicatorTop, indicatorLeft + size, indicatorTop + size)

    // With gesture navigation, an inward swipe from the side edges is "back".
    // Android lets an app keep up to 200dp per edge for itself: here, around
    // the height where the shortcut wheel opens.
    val density = resources.displayMetrics.density
    val exclusionHeight = (EXCLUSION_HEIGHT_DP * density).toInt()
    val exclusionWidth = (EXCLUSION_WIDTH_DP * density).toInt()
    val left = if (hudFlipped) 0 else width - exclusionWidth
    ViewCompat.setSystemGestureExclusionRects(
      this,
      listOf(Rect(left, centreY - exclusionHeight / 2, left + exclusionWidth, centreY + exclusionHeight / 2))
    )
  }

  private fun setHudFlipped(flipped: Boolean) {
    if (flipped == hudFlipped) {
      return
    }
    hudFlipped = flipped
    gestures.flipped = flipped
    indicator.rotation = if (flipped) 180f else 0f
    if (laidOutWidth > 0) {
      layoutControls(laidOutWidth, laidOutHeight)
    }
    onHudFlip(mapOf("flipped" to flipped))
  }

  // Only while the app is on screen: no sensor in the background.
  override fun onWindowVisibilityChanged(visibility: Int) {
    super.onWindowVisibilityChanged(visibility)
    if (visibility == View.VISIBLE && orientationListener.canDetectOrientation()) {
      orientationListener.enable()
    } else {
      orientationListener.disable()
      removeCallbacks(confirmFlip)
      pendingFlip = null
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    requestFocus()
    if (settingsApplied) {
      bindCamera()
    }
  }

  override fun onDetachedFromWindow() {
    orientationListener.disable()
    setWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    super.onDetachedFromWindow()
  }

  /**
   * The screen as a light: full brightness, for this window only, and a white
   * border around a slightly smaller picture.
   */
  private fun applyFillLight() {
    if (fillLight == appliedFillLight) {
      return
    }
    appliedFillLight = fillLight
    // A frozen copy is placed for the old size; the light wants a live face anyway.
    if (renderer.isFrozen) {
      renderer.unfreeze()
      onFreezeChange(mapOf("frozen" to false))
    }
    setBackgroundColor(if (fillLight) Color.WHITE else Color.BLACK)
    setWindowBrightness(if (fillLight) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    if (laidOutWidth > 0) {
      layoutContent(laidOutWidth, laidOutHeight)
    }
  }

  private fun setWindowBrightness(value: Float) {
    val window = appContext.currentActivity?.window ?: return
    if (window.attributes.screenBrightness != value) {
      window.attributes = window.attributes.apply { screenBrightness = value }
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      // Any new touch catches the image where it is.
      stopFling()
      if (!hasFocus()) {
        requestFocus()
      }
    }
    return gestures.onTouchEvent(event)
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val step = if (event.repeatCount > 0) VOLUME_REPEAT_STEP else VOLUME_STEP
    val factor = when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP -> step
      KeyEvent.KEYCODE_VOLUME_DOWN -> 1 / step
      else -> return super.onKeyDown(keyCode, event)
    }
    if (!volumeZoom) {
      return super.onKeyDown(keyCode, event)
    }
    stopFling()
    animator?.cancel()
    removeCallbacks(endVolumeZoom)
    val before = zoom.scale
    // Around the centre of what is on screen, as a one-handed pinch would.
    zoom.zoomBy(factor, zoom.width / 2, zoom.height / 2, 0f, 0f)
    indicator.show(ValueIndicator.zoom(zoom.scale))
    tickAtLimit(before)
    postDelayed(endVolumeZoom, VOLUME_HOLD_MS)
    return true
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    val volumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
    return if (volumeZoom && volumeKey) true else super.onKeyUp(keyCode, event)
  }

  // ---- Camera ----

  private fun bindCamera() {
    val owner = appContext.currentActivity as? LifecycleOwner ?: return
    if (!isAttachedToWindow) {
      return
    }
    val wantGpu = gpuSharpening && !gpuFailed
    val wantHighResolution = highResolution
    if (boundGpu == null && wantGpu && renderer !is GlRenderer) {
      swapRenderer(true)
    }
    boundGpu = wantGpu
    boundHighResolution = wantHighResolution
    scope.launch {
      val provider = ProcessCameraProvider.awaitInstance(context)
      val builder = Preview.Builder().setResolutionSelector(resolutionSelector(wantHighResolution))
      exposure.listenTo(builder)
      val preview = builder.build()
      val wasFrozen = renderer.isFrozen
      renderer.connect(preview)
      if (wasFrozen) {
        onFreezeChange(mapOf("frozen" to false))
      }
      try {
        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
          CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
          CameraSelector.DEFAULT_BACK_CAMERA
        }
        exposure.detach()
        provider.unbindAll()
        // Tied to the activity: the camera stops in the background and comes
        // back on its own.
        val camera = provider.bindToLifecycle(owner, selector, preview)
        cameraProvider = provider
        this@MirrorView.camera = camera
        this@MirrorView.preview = preview
        exposure.attach(
          camera,
          visibleLuma = { renderer.visibleLuma(zoom) },
          zoomed = { zoom.isZoomed },
          frozen = { renderer.isFrozen },
          onCapabilitiesChanged = { reportCameraInfo() }
        )
        reportCameraInfo()
        // The resolution can still be settling right after binding.
        postDelayed({ reportCameraInfo() }, CAMERA_INFO_DELAY_MS)
      } catch (e: Exception) {
        Log.e(TAG, "Could not start the camera", e)
      }
    }
  }

  /**
   * Up to 4K in 16:9 with [high], or CameraX's default cap otherwise (the
   * screen size or 1080p). Passing a resolution strategy is what lifts that
   * cap; the default preference for 30 fps sizes stays.
   */
  private fun resolutionSelector(high: Boolean): ResolutionSelector =
    ResolutionSelector.Builder()
      // Taller than 4:3, so a portrait screen crops less of the face.
      .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
      .apply {
        if (high) {
          setResolutionStrategy(
            ResolutionStrategy(Size(3840, 2160), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
          )
        }
      }
      .build()

  private fun swapRenderer(gpu: Boolean) {
    cameraProvider?.unbindAll()
    exposure.detach()
    val old = renderer
    val wasFrozen = old.isFrozen
    frame.removeView(old.view)
    old.release()
    if (wasFrozen) {
      onFreezeChange(mapOf("frozen" to false))
    }
    renderer = if (gpu) GlRenderer(context) { message -> onGpuFailure(message) } else PreviewRenderer(context)
    frame.addView(renderer.view, matchParent())
    renderer.setSharpening(sharpening)
    if (laidOutWidth > 0) {
      layoutContent(laidOutWidth, laidOutHeight)
    }
    renderer.onZoomChanged(zoom)
  }

  private fun onGpuFailure(message: String) {
    Log.w(TAG, "GPU path off: $message")
    gpuFailed = true
    applySettings()
  }

  private fun reportCameraInfo() {
    val resolution = preview?.resolutionInfo?.resolution
    onCameraInfo(
      mapOf(
        "width" to (resolution?.width ?: 0),
        "height" to (resolution?.height ?: 0),
        "renderer" to renderer.kind,
        "gpuFailed" to gpuFailed,
        "meteringArea" to exposure.meteringSupported,
        "facePriority" to exposure.facePrioritySupported,
        "exposureCompensation" to exposure.compensationSupported,
        "exposureLock" to exposure.holdsWithLock
      )
    )
  }

  private fun scheduleMeter() {
    removeCallbacks(meterSoon)
    postDelayed(meterSoon, METER_DELAY_MS)
  }

  private fun meter() {
    val camera = camera ?: return
    exposure.meter(if (zoom.isZoomed) renderer.meteringPoint(zoom, camera) else null)
  }

  /** After the image stops moving: the state to go back to, and the light for it. */
  private fun afterMove() {
    rememberLastZoomed()
    scheduleMeter()
    saveZoom()
  }

  private fun saveZoom() {
    if (!restoresZoom || zoom.width == 0f || zoom.height == 0f) {
      return
    }
    savedZoom.edit()
      .putFloat(KEY_SCALE, zoom.scale)
      .putFloat(KEY_X, zoom.translationX / zoom.width)
      .putFloat(KEY_Y, zoom.translationY / zoom.height)
      .apply()
  }

  /** Once, as soon as both the settings and the size are known. */
  private fun restoreZoom() {
    if (zoomRestored || !settingsApplied || zoom.width == 0f || zoom.height == 0f) {
      return
    }
    zoomRestored = true
    val scale = savedZoom.getFloat(KEY_SCALE, 1f)
    if (!restoresZoom || scale <= PinchZoom.MIN_SCALE) {
      return
    }
    zoom.set(scale, savedZoom.getFloat(KEY_X, 0f) * zoom.width, savedZoom.getFloat(KEY_Y, 0f) * zoom.height)
    rememberLastZoomed()
  }

  private fun rememberLastZoomed() {
    if (zoom.isZoomed) {
      lastZoomed = floatArrayOf(zoom.scale, zoom.translationX, zoom.translationY)
    }
  }

  // ---- Gestures ----

  override fun onPinchStart() {
    animator?.cancel()
    indicator.show(ValueIndicator.zoom(zoom.scale))
  }

  override fun onPinch(factor: Float, focusX: Float, focusY: Float, panX: Float, panY: Float) {
    val before = zoom.scale
    zoom.zoomBy(factor, focusX - imageLeft, focusY - imageTop, panX, panY)
    indicator.update(ValueIndicator.zoom(zoom.scale))
    tickAtLimit(before)
  }

  /** A light tick the moment the zoom reaches 1× or the top, to feel the limits. */
  private fun tickAtLimit(before: Float) {
    val after = zoom.scale
    val atLimit = after <= PinchZoom.MIN_SCALE || after >= PinchZoom.MAX_SCALE
    if (zoomHaptics && atLimit && after != before) {
      performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
  }

  override fun onPinchEnd() {
    indicator.hide()
    afterMove()
  }

  override fun onPan(dx: Float, dy: Float) {
    zoom.panBy(dx, dy)
  }

  override fun onPanEnd() {
    afterMove()
  }

  override fun onFling(velocityX: Float, velocityY: Float) {
    if (!dragInertia || !zoom.isZoomed) {
      afterMove()
      return
    }
    // Same limits as the pan: the image keeps covering the view.
    val maxX = ((zoom.scale - 1) * zoom.width / 2).toInt()
    val maxY = ((zoom.scale - 1) * zoom.height / 2).toInt()
    scroller.fling(
      zoom.translationX.roundToInt(),
      zoom.translationY.roundToInt(),
      velocityX.roundToInt(),
      velocityY.roundToInt(),
      -maxX,
      maxX,
      -maxY,
      maxY
    )
    removeCallbacks(flingStep)
    postOnAnimation(flingStep)
  }

  private fun stopFling() {
    if (!scroller.isFinished) {
      scroller.forceFinished(true)
      removeCallbacks(flingStep)
      afterMove()
    }
  }

  override fun onDoubleTap(x: Float, y: Float) {
    val target = when {
      zoom.isZoomed -> {
        rememberLastZoomed()
        floatArrayOf(1f, 0f, 0f)
      }
      else -> lastZoomed ?: zoom.translationFor(DOUBLE_TAP_SCALE, x - imageLeft, y - imageTop).let {
        floatArrayOf(DOUBLE_TAP_SCALE, it.x, it.y)
      }
    }
    animateTo(target)
  }

  override fun onLongPress() {
    if (toggleFreeze()) {
      performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
  }

  /** Freezes or unfreezes, from a long press or the shortcut; false when nothing changed. */
  fun toggleFreeze(): Boolean {
    when {
      renderer.isFrozen -> {
        renderer.unfreeze()
        indicator.flash(ValueIndicator.LIVE)
      }
      renderer.freeze() -> indicator.flash(ValueIndicator.FROZEN)
      else -> return false
    }
    onFreezeChange(mapOf("frozen" to renderer.isFrozen))
    return true
  }

  override fun onSlideStart(): Boolean {
    if (!exposure.beginSlide()) {
      return false
    }
    indicator.show(ValueIndicator.exposure(exposure.manualEv))
    return true
  }

  override fun onSlide(fraction: Float) {
    val before = exposure.manualEv
    val after = exposure.slide(fraction)
    indicator.update(ValueIndicator.exposure(after))
    // A tick when the slide lands on or crosses zero, to find it blind.
    if ((after == 0f && before != 0f) || before.sign * after.sign < 0f) {
      performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
  }

  override fun onSlideEnd() {
    indicator.hide()
    exposure.endSlide()
  }

  override fun onShortcutsSwipe() {
    onShortcutsGesture(emptyMap())
  }

  override fun onShortcutsDrag(x: Float, y: Float, end: Boolean, cancelled: Boolean) {
    // In dp, like the layout of the wheel on the JS side.
    val density = resources.displayMetrics.density
    onShortcutsDrag(mapOf("x" to x / density, "y" to y / density, "end" to end, "cancelled" to cancelled))
  }

  private fun animateTo(target: FloatArray) {
    stopFling()
    animator?.cancel()
    val from = floatArrayOf(zoom.scale, zoom.translationX, zoom.translationY)
    indicator.show(ValueIndicator.zoom(zoom.scale))
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = DOUBLE_TAP_MS
      interpolator = DecelerateInterpolator()
      addUpdateListener {
        val t = it.animatedValue as Float
        zoom.set(
          from[0] + (target[0] - from[0]) * t,
          from[1] + (target[1] - from[1]) * t,
          from[2] + (target[2] - from[2]) * t
        )
        indicator.update(ValueIndicator.zoom(zoom.scale))
      }
      addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
          indicator.hide()
          afterMove()
        }
      })
      start()
    }
  }

  fun release() {
    orientationListener.disable()
    removeCallbacks(confirmFlip)
    setWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    animator?.cancel()
    scroller.forceFinished(true)
    removeCallbacks(flingStep)
    removeCallbacks(meterSoon)
    removeCallbacks(endVolumeZoom)
    scope.cancel()
    exposure.detach()
    cameraProvider?.unbindAll()
    cameraProvider = null
    renderer.release()
  }

  private fun matchParent() =
    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

  companion object {
    private const val TAG = "MirrorView"

    /** Height of the indicator's centre, as a fraction measured from the bottom. */
    private const val INDICATOR_FROM_BOTTOM = 0.27f

    /** How close to upright or upside down the phone has to be to flip the controls. */
    private const val FLIP_WITHIN_DEGREES = 50
    private const val FLIP_CONFIRM_MS = 300L
    private const val EXCLUSION_HEIGHT_DP = 200f
    private const val EXCLUSION_WIDTH_DP = 48f
    private const val DOUBLE_TAP_SCALE = 2.5f
    private const val DOUBLE_TAP_MS = 250L
    private const val METER_DELAY_MS = 150L
    private const val CAMERA_INFO_DELAY_MS = 1000L

    /** Fill light border, as a part of the view's shorter side, on every side. */
    private const val FILL_LIGHT_BORDER = 0.14f

    /** Zoom per press of a volume key, and per repeat while it is held. */
    private const val VOLUME_STEP = 1.25f
    private const val VOLUME_REPEAT_STEP = 1.08f
    private const val VOLUME_HOLD_MS = 700L

    private const val PREFERENCES = "expo.modules.mirrorview"
    private const val KEY_SCALE = "zoomScale"
    private const val KEY_X = "zoomX"
    private const val KEY_Y = "zoomY"
  }
}
