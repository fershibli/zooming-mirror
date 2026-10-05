package expo.modules.mirrorview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor

/** Mean brightness of a small copy of the picture. */
object Luma {
  /** Width of the copy the brightness is read from; enough for an average. */
  const val SAMPLE_WIDTH = 72

  fun mean(bitmap: Bitmap, area: RectF? = null): Float? {
    val left = floor(area?.left ?: 0f).toInt().coerceIn(0, bitmap.width - 1)
    val top = floor(area?.top ?: 0f).toInt().coerceIn(0, bitmap.height - 1)
    val right = ceil(area?.right ?: bitmap.width.toFloat()).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ceil(area?.bottom ?: bitmap.height.toFloat()).toInt().coerceIn(top + 1, bitmap.height)
    val width = right - left
    val height = bottom - top
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, left, top, width, height)
    var sum = 0f
    for (pixel in pixels) {
      sum += 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
    }
    return if (pixels.isEmpty()) null else sum / (pixels.size * 255f)
  }
}
