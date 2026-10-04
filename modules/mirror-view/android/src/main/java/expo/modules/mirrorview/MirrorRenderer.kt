package expo.modules.mirrorview

import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview

/**
 * Turns the camera stream and the [PinchZoom] into pixels. Two of them:
 * [PreviewRenderer], CameraX's PreviewView moved by a view transform, and
 * [GlRenderer], an OpenGL pass that upscales and sharpens.
 */
interface MirrorRenderer {
  val view: View

  /** "preview" or "gpu", reported to JS for the settings screen. */
  val kind: String

  val isFrozen: Boolean

  fun connect(preview: Preview)

  fun layout(width: Int, height: Int)

  fun onZoomChanged(zoom: PinchZoom)

  /** Holds the current picture on screen; false when there is none yet. */
  fun freeze(): Boolean

  fun unfreeze()

  /** Exposure point for the area on screen, sized for the current zoom. */
  fun meteringPoint(zoom: PinchZoom, camera: Camera): MeteringPoint?

  /** Mean brightness (0..1) of the area on screen, or null when unknown. */
  fun visibleLuma(zoom: PinchZoom): Float?

  fun release()
}
