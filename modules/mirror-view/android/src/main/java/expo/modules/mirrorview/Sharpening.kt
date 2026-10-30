package expo.modules.mirrorview

/** What the GPU path's second pass does to the enlarged picture. */
enum class Sharpening(val prop: String) {
  /** Edge-preserving smoothing: fine texture and noise go, outlines stay. */
  SMOOTH("smooth"),

  /** Contrast-adaptive sharpening, the default. */
  NATURAL("natural"),

  /** Stronger sharpening plus local contrast, so short dark hairs stand out. */
  DETAIL("detail");

  companion object {
    fun fromProp(value: String) = entries.firstOrNull { it.prop == value } ?: NATURAL
  }
}
