package expo.modules.mirrorview

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
import kotlin.math.sign

/**
 * The front camera filling the view, with nothing on top of it except a value
 * while it changes. See [MirrorGestures] for what the fingers do, [PinchZoom]
 * for the zoom, [Exposure] for the light and the renderers for the pixels.
 */
@SuppressLint("ViewConstructor")
class MirrorView(context: Context, appContext: AppContext) :
  ExpoView(context, appContext), MirrorGestures.Listener {
  private val onSettingsGesture by EventDispatcher()
  private val onCameraInfo by EventDispatcher()

  // Settings, set from JS. Applied together in applySettings().
  var autoLight = true
  var facePriority = true
  var highResolution = true
  var gpuSharpening = false
  var trueView = false

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

  /** The last zoomed state, which a double tap at 1× goes back to. */
  private var lastZoomed: FloatArray? = null
  private val meterSoon = Runnable { meter() }

  init {
    addView(renderer.view, matchParent())
    // A sibling of the image, so the zoom never scales or moves it.
    addView(indicator, ViewGroup.LayoutParams(indicator.diameter, indicator.diameter))
  }

  fun applySettings() {
    settingsApplied = true
    zoom.flipped = trueView
    exposure.autoLight = autoLight
    exposure.facePriority = facePriority
    val wantGpu = gpuSharpening && !gpuFailed
    if (boundGpu != null && boundGpu != wantGpu) {
      swapRenderer(wantGpu)
    }
    if (boundGpu != wantGpu || boundHighResolution != highResolution) {
      bindCamera()
    }
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
    renderer.layout(width, height)
    zoom.setSize(width.toFloat(), height.toFloat())

    val size = indicator.diameter
    val centreY = (height * (1 - INDICATOR_FROM_BOTTOM)).toInt()
    indicator.measure(
      MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
    )
    val indicatorLeft = (width - size) / 2
    val indicatorTop = centreY - size / 2
    indicator.layout(indicatorLeft, indicatorTop, indicatorLeft + size, indicatorTop + size)

    // With gesture navigation, an inward swipe from the side edges is "back".
    // Android lets an app keep up to 200dp per edge for itself: here, around
    // the height where the settings gear shows up.
    val density = resources.displayMetrics.density
    val exclusionHeight = (EXCLUSION_HEIGHT_DP * density).toInt()
    val exclusionWidth = (EXCLUSION_WIDTH_DP * density).toInt()
    ViewCompat.setSystemGestureExclusionRects(
      this,
      listOf(Rect(width - exclusionWidth, centreY - exclusionHeight / 2, width, centreY + exclusionHeight / 2))
    )
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (settingsApplied) {
      bindCamera()
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

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
      val preview = Preview.Builder().setResolutionSelector(resolutionSelector(wantHighResolution)).build()
      renderer.connect(preview)
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
        exposure.attach(camera, { renderer.visibleLuma(zoom) }, { zoom.isZoomed })
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
    removeView(old.view)
    old.release()
    renderer = if (gpu) GlRenderer(context) { message -> onGpuFailure(message) } else PreviewRenderer(context)
    addView(renderer.view, 0, matchParent())
    if (laidOutWidth > 0) {
      renderer.layout(laidOutWidth, laidOutHeight)
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
        "exposureCompensation" to exposure.compensationSupported
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

  private fun rememberZoom() {
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
    zoom.zoomBy(factor, focusX, focusY, panX, panY)
    indicator.update(ValueIndicator.zoom(zoom.scale))
  }

  override fun onPinchEnd() {
    indicator.hide()
    rememberZoom()
    scheduleMeter()
  }

  override fun onPan(dx: Float, dy: Float) {
    zoom.panBy(dx, dy)
  }

  override fun onPanEnd() {
    rememberZoom()
    scheduleMeter()
  }

  override fun onDoubleTap(x: Float, y: Float) {
    val target = when {
      zoom.isZoomed -> {
        rememberZoom()
        floatArrayOf(1f, 0f, 0f)
      }
      else -> lastZoomed ?: zoom.translationFor(DOUBLE_TAP_SCALE, x, y).let {
        floatArrayOf(DOUBLE_TAP_SCALE, it.x, it.y)
      }
    }
    animateTo(target)
  }

  override fun onLongPress() {
    when {
      renderer.isFrozen -> {
        renderer.unfreeze()
        indicator.flash(ValueIndicator.LIVE)
      }
      renderer.freeze() -> indicator.flash(ValueIndicator.FROZEN)
      else -> return
    }
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
  }

  override fun onSettingsSwipe() {
    onSettingsGesture(emptyMap())
  }

  private fun animateTo(target: FloatArray) {
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
          scheduleMeter()
        }
      })
      start()
    }
  }

  fun release() {
    animator?.cancel()
    removeCallbacks(meterSoon)
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
    private const val EXCLUSION_HEIGHT_DP = 200f
    private const val EXCLUSION_WIDTH_DP = 48f
    private const val DOUBLE_TAP_SCALE = 2.5f
    private const val DOUBLE_TAP_MS = 250L
    private const val METER_DELAY_MS = 150L
    private const val CAMERA_INFO_DELAY_MS = 1000L
  }
}
