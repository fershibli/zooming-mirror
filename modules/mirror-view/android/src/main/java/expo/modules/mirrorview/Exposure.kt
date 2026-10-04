package expo.modules.mirrorview

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Light that follows what is on screen.
 *
 * - Automatic: after a gesture, the camera meters exposure and white balance
 *   on the visible area only. Cameras that cannot take a metering area get a
 *   slow loop instead, which reads the visible brightness and nudges the
 *   exposure compensation.
 * - Face priority: the camera's own face-priority scene mode, when it has one.
 * - Manual: an exposure compensation the user slides, added on top.
 */
@OptIn(ExperimentalCamera2Interop::class)
class Exposure(private val host: View) {
  private var camera: Camera? = null
  private var range = Range(0, 0)
  private var step = 0f
  private var manualIndex = 0
  private var autoIndex = 0
  private var slideFrom = 0
  private var visibleLuma: () -> Float? = { null }
  private var zoomed: () -> Boolean = { false }

  var meteringSupported = false
    private set
  var facePrioritySupported = false
    private set
  var compensationSupported = false
    private set

  var autoLight = true
    set(value) {
      if (field == value) {
        return
      }
      field = value
      if (!value) {
        camera?.cameraControl?.cancelFocusAndMetering()
        autoIndex = 0
        applyCompensation()
      }
      updateLoop()
    }

  var facePriority = true
    set(value) {
      if (field != value) {
        field = value
        applyFacePriority()
      }
    }

  val manualEv
    get() = manualIndex * step

  fun attach(camera: Camera, visibleLuma: () -> Float?, zoomed: () -> Boolean) {
    this.camera = camera
    this.visibleLuma = visibleLuma
    this.zoomed = zoomed
    val info = camera.cameraInfo
    val state = info.exposureState
    compensationSupported = state.isExposureCompensationSupported
    range = state.exposureCompensationRange
    step = state.exposureCompensationStep.toFloat()
    val probe = FocusMeteringAction.Builder(
      SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f),
      FocusMeteringAction.FLAG_AE
    ).build()
    meteringSupported = info.isFocusMeteringSupported(probe)
    facePrioritySupported = Camera2CameraInfo.from(info)
      .getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
      ?.contains(CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY) == true
    autoIndex = 0
    applyFacePriority()
    applyCompensation()
    updateLoop()
  }

  fun detach() {
    host.removeCallbacks(loop)
    camera = null
  }

  /** Meters on [point] while zoomed, or on the whole frame back at 1×. */
  fun meter(point: MeteringPoint?) {
    val camera = camera ?: return
    if (!autoLight || !meteringSupported) {
      return
    }
    if (point == null) {
      camera.cameraControl.cancelFocusAndMetering()
      return
    }
    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB)
      .disableAutoCancel()
      .build()
    camera.cameraControl.startFocusAndMetering(action)
  }

  /** Starts a manual slide; false when the camera has no compensation. */
  fun beginSlide(): Boolean {
    if (camera == null || !compensationSupported) {
      return false
    }
    slideFrom = manualIndex
    return true
  }

  /**
   * [fraction] of the whole compensation range, from where the slide began:
   * positive is brighter. Returns the manual exposure in EV.
   */
  fun slide(fraction: Float): Float {
    val span = range.upper - range.lower
    val index = (slideFrom + (fraction * span).roundToInt())
      .coerceIn(range.lower - autoIndex, range.upper - autoIndex)
    if (index != manualIndex) {
      manualIndex = index
      applyCompensation()
    }
    return manualEv
  }

  private fun applyFacePriority() {
    val camera = camera ?: return
    val control = Camera2CameraControl.from(camera.cameraControl)
    if (facePriority && facePrioritySupported) {
      control.setCaptureRequestOptions(
        CaptureRequestOptions.Builder()
          .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
          .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY)
          .build()
      )
    } else {
      control.clearCaptureRequestOptions()
    }
  }

  private fun applyCompensation() {
    val camera = camera ?: return
    if (!compensationSupported) {
      return
    }
    camera.cameraControl.setExposureCompensationIndex((manualIndex + autoIndex).coerceIn(range.lower, range.upper))
  }

  // ---- Fallback for cameras without a metering area ----

  private val loop = object : Runnable {
    override fun run() {
      adjust()
      host.postDelayed(this, LOOP_MS)
    }
  }

  private fun updateLoop() {
    host.removeCallbacks(loop)
    if (camera != null && autoLight && !meteringSupported && compensationSupported) {
      host.postDelayed(loop, LOOP_MS)
    }
  }

  private fun adjust() {
    if (!zoomed()) {
      if (autoIndex != 0) {
        autoIndex = 0
        applyCompensation()
      }
      return
    }
    val luma = visibleLuma() ?: return
    val error = TARGET_LUMA - luma
    if (kotlin.math.abs(error) < DEADBAND) {
      return
    }
    val next = (autoIndex + error.sign.toInt())
      .coerceIn(range.lower - manualIndex, range.upper - manualIndex)
    if (next != autoIndex) {
      autoIndex = next
      applyCompensation()
    }
  }

  companion object {
    private const val LOOP_MS = 400L
    private const val TARGET_LUMA = 0.45f
    private const val DEADBAND = 0.07f
  }
}
