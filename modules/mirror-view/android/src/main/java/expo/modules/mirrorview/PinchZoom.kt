package expo.modules.mirrorview

import android.graphics.PointF

/**
 * The zoom as plain numbers: a scale and a translation of the image over a
 * view of [width]×[height]. Renderers turn it into pixels, as a view
 * transform or as shader uniforms.
 *
 * Zooming works the same on every front camera, including the many that have
 * no zoom of their own: the camera stays at 1× and only the image is scaled.
 * The point under the fingers stays under the fingers, moving them pans, and
 * the image always covers the whole view, so at 1× there is nothing to pan.
 *
 * The screen point S shows the image point p = c + (S − c − t) / s, with c the
 * centre and t the translation. [flipped] makes s negative horizontally, which
 * the formulas below handle without special cases.
 */
class PinchZoom(private val onChange: (PinchZoom) -> Unit) {
  var width = 0f
    private set
  var height = 0f
    private set
  var scale = 1f
    private set
  var translationX = 0f
    private set
  var translationY = 0f
    private set

  /** Shows the image the other way round: how others see you. */
  var flipped = false
    set(value) {
      if (field != value) {
        field = value
        onChange(this)
      }
    }

  val isZoomed
    get() = scale > 1.001f

  val signedScaleX
    get() = if (flipped) -scale else scale

  fun setSize(width: Float, height: Float) {
    this.width = width
    this.height = height
    apply(scale, translationX, translationY)
  }

  fun zoomBy(factor: Float, focusX: Float, focusY: Float, panX: Float, panY: Float) {
    if (width == 0f || height == 0f) {
      return
    }
    val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
    val ratio = next / scale
    // Image point under the focus, kept there after scaling around the centre.
    val fromCentreX = focusX - width / 2
    val fromCentreY = focusY - height / 2
    apply(
      next,
      fromCentreX - ratio * (fromCentreX - translationX) + panX,
      fromCentreY - ratio * (fromCentreY - translationY) + panY
    )
  }

  /** Moves the image by the finger's travel, without changing the zoom. */
  fun panBy(dx: Float, dy: Float) {
    apply(scale, translationX + dx, translationY + dy)
  }

  /** Jumps to a state, as the double-tap animation does on every frame. */
  fun set(scale: Float, translationX: Float, translationY: Float) {
    apply(scale.coerceIn(MIN_SCALE, MAX_SCALE), translationX, translationY)
  }

  /** Translation that keeps the image point under [x], [y] there at [scale]. */
  fun translationFor(scale: Float, x: Float, y: Float): PointF {
    val ratio = scale / this.scale
    val fromCentreX = x - width / 2
    val fromCentreY = y - height / 2
    return PointF(
      fromCentreX - ratio * (fromCentreX - translationX),
      fromCentreY - ratio * (fromCentreY - translationY)
    )
  }

  /** Point of the unzoomed image shown at the screen point [x], [y]. */
  fun toImage(x: Float, y: Float): PointF =
    PointF(
      width / 2 + (x - width / 2 - translationX) / signedScaleX,
      height / 2 + (y - height / 2 - translationY) / scale
    )

  private fun apply(scale: Float, translationX: Float, translationY: Float) {
    val maxX = (scale - 1) * width / 2
    val maxY = (scale - 1) * height / 2
    this.scale = scale
    this.translationX = translationX.coerceIn(-maxX, maxX)
    this.translationY = translationY.coerceIn(-maxY, maxY)
    onChange(this)
  }

  companion object {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 10f
  }
}
