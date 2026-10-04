package expo.modules.mirrorview

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Every gesture of the mirror, from raw touches:
 *
 * - pinch: zoom (and pan with both fingers);
 * - one finger: pan;
 * - double tap: to 1× and back;
 * - long press: freeze;
 * - starting on the right edge, vertical: manual exposure;
 * - starting on the right edge, inwards: the settings gear.
 */
class MirrorGestures(context: Context, private val view: View, private val listener: Listener) {
  interface Listener {
    fun onPinchStart()
    fun onPinch(factor: Float, focusX: Float, focusY: Float, panX: Float, panY: Float)
    fun onPinchEnd()
    fun onPan(dx: Float, dy: Float)
    fun onPanEnd()
    fun onDoubleTap(x: Float, y: Float)
    fun onLongPress()

    /** False when there is no exposure to slide; the touch then pans. */
    fun onSlideStart(): Boolean
    fun onSlide(fraction: Float)
    fun onSlideEnd()
    fun onSettingsSwipe()
  }

  private enum class Mode { IDLE, TOUCH, EDGE, SLIDE, DONE }

  private val slop = ViewConfiguration.get(context).scaledTouchSlop
  private val edgeWidth = EDGE_WIDTH_DP * context.resources.displayMetrics.density
  private var mode = Mode.IDLE
  private var downX = 0f
  private var downY = 0f
  private var lastX = 0f
  private var lastY = 0f
  private var lastFocusX = 0f
  private var lastFocusY = 0f
  private var panned = false

  private val scaleDetector = ScaleGestureDetector(
    context,
    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
      override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        lastFocusX = detector.focusX
        lastFocusY = detector.focusY
        listener.onPinchStart()
        return true
      }

      override fun onScale(detector: ScaleGestureDetector): Boolean {
        listener.onPinch(
          detector.scaleFactor,
          detector.focusX,
          detector.focusY,
          detector.focusX - lastFocusX,
          detector.focusY - lastFocusY
        )
        lastFocusX = detector.focusX
        lastFocusY = detector.focusY
        return true
      }

      override fun onScaleEnd(detector: ScaleGestureDetector) {
        listener.onPinchEnd()
      }
    }
  ).apply {
    // Double-tap-and-drag would zoom with one finger, which is a pan here.
    isQuickScaleEnabled = false
  }

  private val tapDetector = GestureDetector(
    context,
    object : GestureDetector.SimpleOnGestureListener() {
      override fun onDoubleTap(e: MotionEvent): Boolean {
        listener.onDoubleTap(e.x, e.y)
        return true
      }

      override fun onLongPress(e: MotionEvent) {
        listener.onLongPress()
      }
    }
  )

  fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      mode = if (event.x >= view.width - edgeWidth) Mode.EDGE else Mode.TOUCH
      downX = event.x
      downY = event.y
      lastX = event.x
      lastY = event.y
      panned = false
    }
    when (mode) {
      Mode.SLIDE -> {
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
          listener.onSlide((downY - event.y) / (view.height * SLIDE_HEIGHT))
        }
      }
      Mode.DONE, Mode.IDLE -> Unit
      Mode.TOUCH, Mode.EDGE -> {
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)
        track(event)
      }
    }
    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
      finish()
    }
    return true
  }

  private fun track(event: MotionEvent) {
    when (event.actionMasked) {
      // A second finger makes it a pinch, wherever the first one started.
      MotionEvent.ACTION_POINTER_DOWN -> mode = Mode.TOUCH
      // Back to one finger after a pinch: pan on from where that finger is,
      // or the image would jump by the distance between the two.
      MotionEvent.ACTION_POINTER_UP -> if (event.pointerCount == 2) {
        val remaining = 1 - event.actionIndex
        lastX = event.getX(remaining)
        lastY = event.getY(remaining)
      }
      MotionEvent.ACTION_MOVE -> {
        if (event.pointerCount != 1) {
          return
        }
        if (mode == Mode.EDGE && !resolveEdge(event)) {
          return
        }
        if (mode == Mode.TOUCH && !scaleDetector.isInProgress) {
          listener.onPan(event.x - lastX, event.y - lastY)
          panned = panned || event.x != lastX || event.y != lastY
        }
        lastX = event.x
        lastY = event.y
      }
    }
  }

  /** Decides what a touch that began on the right edge is; true to pan it. */
  private fun resolveEdge(event: MotionEvent): Boolean {
    val dx = event.x - downX
    val dy = event.y - downY
    if (abs(dy) > slop && abs(dy) > abs(dx)) {
      mode = if (listener.onSlideStart()) Mode.SLIDE else Mode.TOUCH
      if (mode == Mode.SLIDE) {
        cancelTaps(event)
        return false
      }
      return true
    }
    if (dx < -slop && abs(dx) >= abs(dy)) {
      mode = Mode.DONE
      cancelTaps(event)
      listener.onSettingsSwipe()
    }
    return false
  }

  private fun finish() {
    if (mode == Mode.SLIDE) {
      listener.onSlideEnd()
    }
    if (panned) {
      listener.onPanEnd()
    }
    panned = false
    mode = Mode.IDLE
  }

  /** Stops a pending long press or double tap once the touch means something else. */
  private fun cancelTaps(event: MotionEvent) {
    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
    tapDetector.onTouchEvent(cancel)
    cancel.recycle()
  }

  companion object {
    /** Width of the strip along the right edge where edge gestures start. */
    const val EDGE_WIDTH_DP = 28f

    /** Part of the screen height a slide covers to cross the whole range. */
    private const val SLIDE_HEIGHT = 0.5f
  }
}
