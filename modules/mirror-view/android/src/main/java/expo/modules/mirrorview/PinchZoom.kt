package expo.modules.mirrorview

import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Zooms by scaling the view the camera renders into, so it works the same on
 * every front camera, including the many that have no zoom of their own.
 *
 * The point under the fingers stays under the fingers, moving them pans, and
 * the image always covers the whole screen.
 */
class PinchZoom(private val target: View) {
  var scale = 1f
    private set

  fun zoomBy(factor: Float, focusX: Float, focusY: Float, panX: Float, panY: Float) {
    val width = target.width.toFloat()
    val height = target.height.toFloat()
    if (width == 0f || height == 0f) {
      return
    }
    val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
    val ratio = next / scale
    // Content point under the focus, kept there after scaling around the centre.
    val fromCentreX = focusX - width / 2
    val fromCentreY = focusY - height / 2
    val translationX = fromCentreX - ratio * (fromCentreX - target.translationX) + panX
    val translationY = fromCentreY - ratio * (fromCentreY - target.translationY) + panY

    scale = next
    apply(translationX, translationY)
  }

  /** Re-applies the transform after a resize, keeping the image on screen. */
  fun refresh() {
    apply(target.translationX, target.translationY)
  }

  private fun apply(translationX: Float, translationY: Float) {
    val maxX = (scale - 1) * target.width / 2
    val maxY = (scale - 1) * target.height / 2
    target.pivotX = target.width / 2f
    target.pivotY = target.height / 2f
    target.scaleX = scale
    target.scaleY = scale
    target.translationX = min(maxX, max(-maxX, translationX))
    target.translationY = min(maxY, max(-maxY, translationY))
  }

  companion object {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 10f
  }
}
