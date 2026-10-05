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
import kotlin.math.abs

/**
 * A translucent circle low on the screen with a single value in it: the zoom
 * while it changes, the exposure while it is slid, or a brief freeze symbol.
 * Numbers follow the phone's format, so 2,35× in Portuguese.
 */
@SuppressLint("AppCompatCustomView", "ViewConstructor")
class ValueIndicator(context: Context) : TextView(context) {
  val diameter = dp(96f).toInt()
  private val hideLater = Runnable { hide() }

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

  fun show(value: String) {
    removeCallbacks(hideLater)
    text = value
    animate().cancel()
    animate().alpha(1f).setDuration(FADE_IN_MS).start()
  }

  fun update(value: String) {
    text = value
  }

  fun hide() {
    removeCallbacks(hideLater)
    animate().cancel()
    animate().alpha(0f).setDuration(FADE_OUT_MS).start()
  }

  /** Shows [value] and hides it on its own a moment later. */
  fun flash(value: String) {
    show(value)
    postDelayed(hideLater, FLASH_MS)
  }

  private fun dp(value: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

  companion object {
    private const val FADE_IN_MS = 80L
    private const val FADE_OUT_MS = 200L
    private const val FLASH_MS = 700L

    const val FROZEN = "❚❚"
    const val LIVE = "▶︎"

    fun zoom(scale: Float): String = String.format(Locale.getDefault(), "%.2f×", scale)

    fun exposure(ev: Float): String =
      if (abs(ev) < 0.05f) "0 EV" else String.format(Locale.getDefault(), "%+.1f EV", ev)
  }
}
