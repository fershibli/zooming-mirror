package expo.modules.mirrorview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import java.util.Locale

/**
 * The zoom level in a translucent circle, visible only while a pinch is
 * changing it. Two decimals in the phone's number format, so 2,35× in
 * Portuguese.
 */
@SuppressLint("AppCompatCustomView", "ViewConstructor")
class ZoomIndicator(context: Context) : TextView(context) {
  val diameter = dp(96f).toInt()

  init {
    background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(Color.argb(115, 0, 0, 0))
      setStroke(dp(1.5f).toInt(), Color.argb(90, 255, 255, 255))
    }
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
    typeface = Typeface.DEFAULT_BOLD
    includeFontPadding = false
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    alpha = 0f
  }

  fun show(scale: Float) {
    update(scale)
    animate().cancel()
    animate().alpha(1f).setDuration(FADE_IN_MS).start()
  }

  fun update(scale: Float) {
    text = String.format(Locale.getDefault(), "%.2f×", scale)
  }

  fun hide() {
    animate().cancel()
    animate().alpha(0f).setDuration(FADE_OUT_MS).start()
  }

  private fun dp(value: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

  companion object {
    private const val FADE_IN_MS = 80L
    private const val FADE_OUT_MS = 200L
  }
}
