package expo.modules.mirrorview

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The front camera filling the view. Pinching zooms the image itself (see
 * [PinchZoom]) while the camera stays at 1×, and one finger drags the image
 * around. The only thing ever drawn on top is the zoom level, during a pinch.
 */
@SuppressLint("ViewConstructor")
class MirrorView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val previewView = PreviewView(context).apply {
    // TextureView instead of SurfaceView: only it follows the view's scale and
    // translation, which is what the zoom moves.
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    scaleType = PreviewView.ScaleType.FILL_CENTER
  }
  private val zoom = PinchZoom(previewView)
  private val indicator = ZoomIndicator(context)
  private var cameraProvider: ProcessCameraProvider? = null
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  private var lastFocusX = 0f
  private var lastFocusY = 0f
  private var lastDragX = 0f
  private var lastDragY = 0f
  private val scaleDetector = ScaleGestureDetector(
    context,
    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
      override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        lastFocusX = detector.focusX
        lastFocusY = detector.focusY
        indicator.show(zoom.scale)
        return true
      }

      override fun onScale(detector: ScaleGestureDetector): Boolean {
        zoom.zoomBy(
          detector.scaleFactor,
          detector.focusX,
          detector.focusY,
          detector.focusX - lastFocusX,
          detector.focusY - lastFocusY
        )
        lastFocusX = detector.focusX
        lastFocusY = detector.focusY
        indicator.update(zoom.scale)
        return true
      }

      override fun onScaleEnd(detector: ScaleGestureDetector) {
        indicator.hide()
      }
    }
  ).apply {
    // Double-tap-and-drag would also zoom with one finger, which is now a drag.
    isQuickScaleEnabled = false
  }

  init {
    // React Native does not lay out views added natively, so the TextureView
    // PreviewView creates later has to be measured and placed by hand.
    previewView.setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
      override fun onChildViewRemoved(parent: View?, child: View?) = Unit
      override fun onChildViewAdded(parent: View?, child: View?) {
        parent?.measure(
          MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
        parent?.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
      }
    })
    addView(
      previewView,
      ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    )
    // A sibling of the preview, so the zoom never scales or moves it.
    addView(indicator, ViewGroup.LayoutParams(indicator.diameter, indicator.diameter))
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    measureChild(previewView, widthMeasureSpec, heightMeasureSpec)
    setMeasuredDimension(
      resolveSize(previewView.measuredWidth, widthMeasureSpec),
      resolveSize(previewView.measuredHeight, heightMeasureSpec)
    )
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    val width = right - left
    val height = bottom - top
    if (previewView.width != width || previewView.height != height) {
      previewView.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
      )
      previewView.layout(0, 0, width, height)
      zoom.refresh()

      val size = indicator.diameter
      val centreY = (height * (1 - INDICATOR_FROM_BOTTOM)).toInt()
      indicator.measure(
        MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
      )
      val indicatorLeft = (width - size) / 2
      val indicatorTop = centreY - size / 2
      indicator.layout(indicatorLeft, indicatorTop, indicatorLeft + size, indicatorTop + size)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    bindCamera()
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    scaleDetector.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        lastDragX = event.x
        lastDragY = event.y
      }
      // Back to one finger after a pinch: drag on from where that finger is,
      // or the image would jump by the distance between the two.
      MotionEvent.ACTION_POINTER_UP -> if (event.pointerCount == 2) {
        val remaining = 1 - event.actionIndex
        lastDragX = event.getX(remaining)
        lastDragY = event.getY(remaining)
      }
      MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
        zoom.panBy(event.x - lastDragX, event.y - lastDragY)
        lastDragX = event.x
        lastDragY = event.y
      }
    }
    return true
  }

  private fun bindCamera() {
    val owner = appContext.currentActivity as? LifecycleOwner ?: return
    scope.launch {
      val provider = ProcessCameraProvider.awaitInstance(context)
      val preview = Preview.Builder()
        .setResolutionSelector(
          ResolutionSelector.Builder()
            // Taller than 4:3, so a portrait screen crops less of the face.
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
        )
        .build()
      preview.surfaceProvider = previewView.surfaceProvider
      try {
        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
          CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
          CameraSelector.DEFAULT_BACK_CAMERA
        }
        provider.unbindAll()
        // Tied to the activity: the camera stops in the background and comes
        // back on its own.
        provider.bindToLifecycle(owner, selector, preview)
        cameraProvider = provider
      } catch (e: Exception) {
        Log.e(TAG, "Could not start the camera", e)
      }
    }
  }

  fun release() {
    scope.cancel()
    cameraProvider?.unbindAll()
    cameraProvider = null
  }

  companion object {
    private const val TAG = "MirrorView"

    /** Height of the indicator's centre, as a fraction measured from the bottom. */
    private const val INDICATOR_FROM_BOTTOM = 0.27f
  }
}
