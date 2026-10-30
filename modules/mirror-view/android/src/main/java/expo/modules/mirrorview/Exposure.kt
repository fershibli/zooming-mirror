package expo.modules.mirrorview

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The light of the picture.
 *
 * Left alone, the camera re-exposes all the time: moving closer or further
 * away is enough to change the picture's brightness. So, unless the
 * sensitivity is at its highest, the exposure and white balance are *held*:
 *
 * - Automatic light: held, and metered again only when the brightness of
 *   what is on screen drifts past the sensitivity for a moment. While zoomed,
 *   the camera meters on the visible area only.
 * - Highest sensitivity: never held, the camera's own continuous metering.
 *   Cameras that cannot take a metering area get a slow loop instead, which
 *   reads the visible brightness and nudges the exposure compensation.
 * - Automatic light off: held from the moment the camera has found the
 *   light; only the manual slide changes it.
 * - Manual slide: an exposure compensation added on top, in every case. The
 *   camera still applies it while held.
 *
 * Holding uses the camera's AE and AWB locks. Every frame reports whether the
 * lock is really on; where it is not (or the camera has none), a loop holds
 * the brightness with the exposure compensation instead.
 */
@OptIn(ExperimentalCamera2Interop::class)
class Exposure(private val host: View) {
  private enum class Mode {
    /** The camera's own continuous metering. */
    FOLLOW,

    /** Held, metered again when the visible brightness drifts too far. */
    STEADY,

    /** Held; only the manual slide changes it. */
    MANUAL
  }

  private enum class Phase {
    /** Nothing to do: following, or the camera is not open. */
    IDLE,

    /** Unlocked, waiting for the camera to find the light. */
    SETTLING,

    /** The lock went out, waiting for frames to confirm it. */
    LOCKING,
    HOLDING
  }

  private var camera: Camera? = null
  private var range = Range(0, 0)
  private var step = 0f
  private var manualIndex = 0

  /** The fallback loop's own compensation, on top of the manual one. */
  private var autoIndex = 0
  private var slideFrom = 0
  private var sliding = false
  private var visibleLuma: () -> Float? = { null }
  private var zoomed: () -> Boolean = { false }
  private var frozen: () -> Boolean = { false }
  private var onCapabilitiesChanged: () -> Unit = {}

  private var autoLight = true
  private var sensitivity = DEFAULT_SENSITIVITY
  private var facePriority = true

  private var mode = Mode.STEADY
  private var phase = Phase.IDLE
  private var phaseStart = 0L
  private var phaseFrame = 0L
  private var cameraOpen = false
  private var locked = false

  /** Visible brightness right after the light was last set; null until read. */
  private var reference: Float? = null
  private var referenceAfter = 0L
  private var driftSince = 0L
  private var lastSample = 0L
  private var lastNudge = 0L

  var meteringSupported = false
    private set
  var facePrioritySupported = false
    private set
  var compensationSupported = false
    private set
  private var lockSupported = false
  private var awbLockSupported = false

  /** The camera took the lock request and kept metering anyway. */
  private var lockIgnored = false

  /** Holding with the camera's lock, rather than with the compensation loop. */
  val holdsWithLock
    get() = lockSupported && !lockIgnored

  val manualEv
    get() = manualIndex * step

  // ---- Frames, on the camera's thread ----

  private val frames = AtomicLong()

  @Volatile private var aeState: Int? = null

  @Volatile private var aeLocked: Boolean? = null

  private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureCompleted(
      session: CameraCaptureSession,
      request: CaptureRequest,
      result: TotalCaptureResult
    ) {
      aeState = result.get(CaptureResult.CONTROL_AE_STATE)
      aeLocked = result.get(CaptureResult.CONTROL_AE_LOCK)
      frames.incrementAndGet()
    }
  }

  /** Has [builder]'s frames report back how the exposure is doing. */
  fun listenTo(builder: Preview.Builder) {
    Camera2Interop.Extender(builder).setSessionCaptureCallback(captureCallback)
  }

  // ---- Setup ----

  fun configure(autoLight: Boolean, sensitivity: Int, facePriority: Boolean) {
    val faceChanged = facePriority != this.facePriority
    this.autoLight = autoLight
    this.sensitivity = sensitivity.coerceIn(0, MAX_SENSITIVITY)
    this.facePriority = facePriority
    if (faceChanged) {
      applyRequestOptions()
    }
    updateMode()
  }

  fun attach(
    camera: Camera,
    visibleLuma: () -> Float?,
    zoomed: () -> Boolean,
    frozen: () -> Boolean,
    onCapabilitiesChanged: () -> Unit
  ) {
    this.camera = camera
    this.visibleLuma = visibleLuma
    this.zoomed = zoomed
    this.frozen = frozen
    this.onCapabilitiesChanged = onCapabilitiesChanged
    val info = camera.cameraInfo
    val state = info.exposureState
    compensationSupported = state.isExposureCompensationSupported
    range = state.exposureCompensationRange
    step = state.exposureCompensationStep.toFloat()
    meteringSupported = info.isFocusMeteringSupported(meteringAction(WHOLE_FRAME))
    val characteristics = Camera2CameraInfo.from(info)
    facePrioritySupported = characteristics
      .getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
      ?.contains(CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY) == true
    lockSupported = characteristics.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
    awbLockSupported = characteristics.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true
    lockIgnored = false
    autoIndex = 0
    locked = false
    cameraOpen = false
    mode = currentMode()
    enter(Phase.IDLE)
    applyRequestOptions()
    applyCompensation()
    info.cameraState.observeForever(stateObserver)
    host.removeCallbacks(tick)
    host.post(tick)
  }

  fun detach() {
    host.removeCallbacks(tick)
    camera?.cameraInfo?.cameraState?.removeObserver(stateObserver)
    camera = null
    cameraOpen = false
    locked = false
    enter(Phase.IDLE)
  }

  // ---- What the view asks for ----

  /**
   * After a gesture: meters on [point] while zoomed, or on the whole frame
   * back at 1×. While held, the area waits for the next time it meters.
   */
  fun meter(point: MeteringPoint?) {
    val camera = camera ?: return
    if (mode == Mode.MANUAL && !holdsWithLock) {
      // The compensation loop holds the brightness of what is on screen, and
      // the gesture just changed what that is.
      retakeReference(VIEW_SETTLE_MS)
      return
    }
    if (!meteringSupported) {
      return
    }
    // Never cancelFocusAndMetering(): CameraX writes an AE unlock into every
    // request after it, and the lock would stop working.
    camera.cameraControl.startFocusAndMetering(meteringAction(point ?: WHOLE_FRAME))
  }

  /** Starts a manual slide; false when the camera has no compensation. */
  fun beginSlide(): Boolean {
    if (camera == null || !compensationSupported) {
      return false
    }
    slideFrom = manualIndex
    sliding = true
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

  fun endSlide() {
    sliding = false
    // The new brightness is the one to hold, not a change of light.
    retakeReference(SLIDE_SETTLE_MS)
  }

  // ---- Modes ----

  private fun currentMode() = when {
    !autoLight -> Mode.MANUAL
    sensitivity >= MAX_SENSITIVITY -> Mode.FOLLOW
    else -> Mode.STEADY
  }

  private fun updateMode() {
    val previous = mode
    mode = currentMode()
    driftSince = 0L
    if (camera == null || !cameraOpen || mode == previous) {
      return
    }
    when {
      mode == Mode.FOLLOW -> {
        enter(Phase.IDLE)
        setLocked(false)
        autoIndex = 0
        applyCompensation()
      }
      // Following until now, so the light is already right: keep it.
      previous == Mode.FOLLOW -> hold()
      // Turning the automatic light back on finds the light again at once.
      mode == Mode.STEADY -> remeter()
      // Turning it off keeps the light as it is.
      else -> Unit
    }
  }

  private val stateObserver = Observer<CameraState> { state ->
    val open = state.type == CameraState.Type.OPEN
    if (open == cameraOpen) {
      return@Observer
    }
    cameraOpen = open
    if (!open) {
      // Opens unlocked next time, so the camera finds the light first.
      enter(Phase.IDLE)
      setLocked(false)
    } else if (mode != Mode.FOLLOW) {
      remeter()
    }
  }

  /** Holds the light as it is now. */
  private fun hold() {
    if (holdsWithLock) {
      setLocked(true)
      enter(Phase.LOCKING)
    } else {
      retakeReference(0)
      enter(Phase.HOLDING)
    }
  }

  /** Lets the camera find the light again, then holds it. */
  private fun remeter() {
    setLocked(false)
    if (!holdsWithLock && autoIndex != 0) {
      autoIndex = 0
      applyCompensation()
    }
    enter(Phase.SETTLING)
  }

  private fun enter(next: Phase) {
    phase = next
    phaseStart = SystemClock.uptimeMillis()
    phaseFrame = frames.get()
    driftSince = 0L
  }

  private fun retakeReference(delay: Long) {
    reference = null
    referenceAfter = SystemClock.uptimeMillis() + delay
    driftSince = 0L
  }

  // ---- The loop, on the main thread ----

  private val tick = object : Runnable {
    override fun run() {
      update(SystemClock.uptimeMillis())
      host.postDelayed(this, TICK_MS)
    }
  }

  private fun update(now: Long) {
    if (camera == null || !cameraOpen) {
      return
    }
    if (mode == Mode.FOLLOW) {
      follow(now)
      return
    }
    when (phase) {
      Phase.IDLE -> Unit
      Phase.SETTLING -> settle(now)
      Phase.LOCKING -> confirmLock(now)
      Phase.HOLDING -> keep(now)
    }
  }

  private fun settle(now: Long) {
    val elapsed = now - phaseStart
    if (!holdsWithLock) {
      if (elapsed >= SETTLE_WITHOUT_LOCK_MS) {
        retakeReference(0)
        enter(Phase.HOLDING)
      }
      return
    }
    // A few frames from the unlocked requests first: the first ones still
    // carry the old lock and the old state.
    val unlockedFrames = frames.get() - phaseFrame >= SETTLE_MIN_FRAMES && aeLocked != true
    val converged = when (val state = aeState) {
      null -> elapsed >= SETTLE_WITHOUT_STATE_MS
      else -> state == CaptureResult.CONTROL_AE_STATE_CONVERGED || state == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
    }
    if ((unlockedFrames && converged) || elapsed >= SETTLE_TIMEOUT_MS) {
      setLocked(true)
      enter(Phase.LOCKING)
    }
  }

  private fun confirmLock(now: Long) {
    val elapsed = now - phaseStart
    when (aeLocked) {
      true, null -> if (elapsed >= LOCK_SETTLE_MS) {
        retakeReference(0)
        enter(Phase.HOLDING)
      }
      false -> if (frames.get() - phaseFrame >= LOCK_CONFIRM_FRAMES) {
        Log.w(TAG, "The camera ignores the AE lock; holding with the exposure compensation")
        lockIgnored = true
        setLocked(false)
        onCapabilitiesChanged()
        retakeReference(0)
        enter(Phase.HOLDING)
      }
    }
  }

  /** Held: watches the visible brightness against the one it was set at. */
  private fun keep(now: Long) {
    if (sliding || frozen()) {
      driftSince = 0L
      return
    }
    if (now - lastSample < SAMPLE_MS) {
      return
    }
    lastSample = now
    val luma = visibleLuma() ?: return
    val reference = reference
    if (reference == null) {
      if (now >= referenceAfter) {
        this.reference = luma
      }
      return
    }
    val drift = log2((luma + DARK_FLOOR) / (reference + DARK_FLOOR))
    if (mode == Mode.STEADY) {
      if (abs(drift) > DRIFT_LIMITS[sensitivity]) {
        if (driftSince == 0L) {
          driftSince = now
        } else if (now - driftSince >= DRIFT_DWELL_MS[sensitivity]) {
          remeter()
        }
        // Beyond the limit it is a change of light, not something to hold.
        return
      }
      driftSince = 0L
    }
    if (!holdsWithLock && abs(drift) > HOLD_DEADBAND && now - lastNudge >= NUDGE_MS) {
      lastNudge = now
      nudge(-drift.sign.toInt())
    }
  }

  // ---- Fallback while following, for cameras without a metering area ----

  private var lastFollow = 0L

  private fun follow(now: Long) {
    if (meteringSupported || !compensationSupported || now - lastFollow < FOLLOW_MS) {
      return
    }
    lastFollow = now
    if (!zoomed()) {
      if (autoIndex != 0) {
        autoIndex = 0
        applyCompensation()
      }
      return
    }
    val luma = visibleLuma() ?: return
    val error = TARGET_LUMA - luma
    if (abs(error) >= FOLLOW_DEADBAND) {
      nudge(error.sign.toInt())
    }
  }

  // ---- Camera requests ----

  private fun nudge(direction: Int) {
    if (!compensationSupported || direction == 0) {
      return
    }
    val next = (autoIndex + direction).coerceIn(range.lower - manualIndex, range.upper - manualIndex)
    if (next != autoIndex) {
      autoIndex = next
      applyCompensation()
    }
  }

  private fun setLocked(value: Boolean) {
    val next = value && holdsWithLock
    if (next != locked) {
      locked = next
      applyRequestOptions()
    }
  }

  /** Face priority and the locks, all in one set: each call replaces the last. */
  private fun applyRequestOptions() {
    val camera = camera ?: return
    val options = CaptureRequestOptions.Builder()
    if (facePriority && facePrioritySupported) {
      options
        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
        .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY)
    }
    if (lockSupported) {
      options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
      if (awbLockSupported) {
        options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, locked)
      }
    }
    Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(options.build())
  }

  private fun applyCompensation() {
    val camera = camera ?: return
    if (!compensationSupported) {
      return
    }
    camera.cameraControl.setExposureCompensationIndex((manualIndex + autoIndex).coerceIn(range.lower, range.upper))
  }

  private fun meteringAction(point: MeteringPoint) =
    FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB)
      .disableAutoCancel()
      .build()

  companion object {
    private const val TAG = "Exposure"

    /** Sensitivity levels run 0..MAX_SENSITIVITY; the highest never holds. */
    const val MAX_SENSITIVITY = 4
    const val DEFAULT_SENSITIVITY = 2

    /**
     * Per held level, lowest first: how far the visible brightness may drift
     * from where it was set (log2 of the ratio, so 1 is half or double) and
     * for how long, before the camera meters again.
     */
    private val DRIFT_LIMITS = floatArrayOf(1.0f, 0.7f, 0.45f, 0.25f)
    private val DRIFT_DWELL_MS = longArrayOf(2000L, 1400L, 900L, 500L)

    /** Keeps dark pictures, where noise is a large ratio, from drifting. */
    private const val DARK_FLOOR = 0.04f

    /** The compensation loop's tolerance while holding without a lock. */
    private const val HOLD_DEADBAND = 0.12f

    private val WHOLE_FRAME = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f, 1f)

    private const val TICK_MS = 100L
    private const val SAMPLE_MS = 250L
    private const val NUDGE_MS = 500L
    private const val SETTLE_MIN_FRAMES = 6
    private const val SETTLE_WITHOUT_STATE_MS = 800L
    private const val SETTLE_WITHOUT_LOCK_MS = 1000L
    private const val SETTLE_TIMEOUT_MS = 2500L
    private const val LOCK_SETTLE_MS = 300L
    private const val LOCK_CONFIRM_FRAMES = 20
    private const val SLIDE_SETTLE_MS = 400L
    private const val VIEW_SETTLE_MS = 300L

    private const val FOLLOW_MS = 400L
    private const val TARGET_LUMA = 0.45f
    private const val FOLLOW_DEADBAND = 0.07f
  }
}
