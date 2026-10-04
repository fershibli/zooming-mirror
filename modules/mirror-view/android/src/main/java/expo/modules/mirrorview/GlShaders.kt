package expo.modules.mirrorview

/**
 * Shaders of the GPU path. Coordinates in comments:
 * - screen: 0..1 over the view, y down.
 * - image: 0..1 over the unzoomed, full-screen picture (what 1× shows).
 * - frame: 0..1 over the whole camera frame, upright and mirrored as it is
 *   displayed, y down. The screen shows its centre, cropped to fill.
 */
object GlShaders {
  const val VERTEX = """
attribute vec2 aPosition;
varying vec2 vUv;
void main() {
  vUv = aPosition * 0.5 + 0.5;
  gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

  private const val PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

  /** Reads the live camera texture at a frame point. */
  private const val FETCH_CAMERA = """
uniform samplerExternalOES uFrame;
uniform mat4 uSt;
uniform mat3 uDisplayToBuffer;
vec4 fetch(vec2 f) {
  // uDisplayToBuffer is the identity when the camera already rotates and
  // mirrors the stream; the SurfaceTexture matrix wants y up.
  vec3 b = uDisplayToBuffer * vec3(f, 1.0);
  vec2 t = (uSt * vec4(b.x, 1.0 - b.y, 0.0, 1.0)).xy;
  return texture2D(uFrame, t);
}
"""

  /** Reads the frozen copy, stored upright with y up. */
  private const val FETCH_FROZEN = """
uniform sampler2D uFrame;
vec4 fetch(vec2 f) {
  return texture2D(uFrame, vec2(f.x, 1.0 - f.y));
}
"""

  /**
   * Pass 1: maps every screen pixel to the frame through the zoom and the
   * crop, then samples. When the zoom enlarges the frame it uses Catmull-Rom
   * (bicubic, 9 bilinear taps) instead of plain bilinear.
   */
  private const val UPSCALE = """
varying vec2 vUv;
uniform vec4 uZoom;      // signed scale x, scale y, translation x, y (screen units)
uniform vec2 uCrop;      // part of the frame the unzoomed screen shows
uniform vec2 uFrameSize; // frame size in pixels
uniform float uBicubic;

vec4 catmullRom(vec2 f) {
  vec2 pos = f * uFrameSize;
  vec2 p1 = floor(pos - 0.5) + 0.5;
  vec2 t = pos - p1;
  vec2 w0 = t * (-0.5 + t * (1.0 - 0.5 * t));
  vec2 w1 = 1.0 + t * t * (-2.5 + 1.5 * t);
  vec2 w2 = t * (0.5 + t * (2.0 - 1.5 * t));
  vec2 w3 = t * t * (-0.5 + 0.5 * t);
  vec2 w12 = w1 + w2;
  vec2 p0 = (p1 - 1.0) / uFrameSize;
  vec2 p3 = (p1 + 2.0) / uFrameSize;
  vec2 p12 = (p1 + w2 / w12) / uFrameSize;
  return fetch(vec2(p0.x, p0.y)) * w0.x * w0.y
       + fetch(vec2(p12.x, p0.y)) * w12.x * w0.y
       + fetch(vec2(p3.x, p0.y)) * w3.x * w0.y
       + fetch(vec2(p0.x, p12.y)) * w0.x * w12.y
       + fetch(vec2(p12.x, p12.y)) * w12.x * w12.y
       + fetch(vec2(p3.x, p12.y)) * w3.x * w12.y
       + fetch(vec2(p0.x, p3.y)) * w0.x * w3.y
       + fetch(vec2(p12.x, p3.y)) * w12.x * w3.y
       + fetch(vec2(p3.x, p3.y)) * w3.x * w3.y;
}

void main() {
  vec2 screen = vec2(vUv.x, 1.0 - vUv.y);
  vec2 image = 0.5 + (screen - 0.5 - uZoom.zw) / uZoom.xy;
  vec2 f = 0.5 + (image - 0.5) * uCrop;
  vec4 c = uBicubic > 0.5 ? catmullRom(f) : fetch(f);
  gl_FragColor = vec4(clamp(c.rgb, 0.0, 1.0), 1.0);
}
"""

  val UPSCALE_CAMERA =
    "#extension GL_OES_EGL_image_external : require\n$PRECISION$FETCH_CAMERA$UPSCALE"

  val UPSCALE_FROZEN = "$PRECISION$FETCH_FROZEN$UPSCALE"

  /**
   * Pass 2: contrast-adaptive sharpening, after AMD's CAS. Each pixel is
   * pushed away from its four neighbours, less so where contrast is already
   * high, which keeps halos down.
   */
  val SHARPEN = """
$PRECISION
varying vec2 vUv;
uniform sampler2D uImage;
uniform vec2 uStep;       // neighbour distance in texture units
uniform float uSharpness; // 0..1
void main() {
  vec3 b = texture2D(uImage, vUv + vec2(0.0, uStep.y)).rgb;
  vec3 d = texture2D(uImage, vUv - vec2(uStep.x, 0.0)).rgb;
  vec3 e = texture2D(uImage, vUv).rgb;
  vec3 f = texture2D(uImage, vUv + vec2(uStep.x, 0.0)).rgb;
  vec3 h = texture2D(uImage, vUv - vec2(0.0, uStep.y)).rgb;
  vec3 mn = min(min(min(d, e), min(f, b)), h);
  vec3 mx = max(max(max(d, e), max(f, b)), h);
  vec3 amp = sqrt(clamp(min(mn, 2.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0));
  vec3 w = amp * (-1.0 / mix(8.0, 5.0, uSharpness));
  vec3 c = (b * w + d * w + f * w + h * w + e) / (1.0 + 4.0 * w);
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
}
