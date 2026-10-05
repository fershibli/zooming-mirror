package expo.modules.mirrorview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.camera.core.Camera
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView

/**
 * CameraX's PreviewView, with the zoom applied as a transform of the whole
 * view. The GPU composes the camera texture straight to the screen with that
 * transform, so a stream bigger than the screen still adds detail when zoomed.
 */
class PreviewRenderer(private val context: Context) : MirrorRenderer {
  private val previewView = PreviewView(context).apply {
    // TextureView instead of SurfaceView: only it follows the view's scale and
    // translation, which is what the zoom moves.
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    scaleType = PreviewView.ScaleType.FILL_CENTER
  }
  private var frozenImage: ImageView? = null
  private var lumaBitmap: Bitmap? = null

  override val view: View
    get() = previewView
  override val kind = "preview"
  override val isFrozen
    get() = frozenImage != null

  init {
    // React Native does not lay out views added natively, so the TextureView
    // PreviewView creates later has to be measured and placed by hand.
    previewView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
      override fun onChildViewRemoved(parent: View?, child: View?) = Unit
      override fun onChildViewAdded(parent: View?, child: View?) {
        if (child !is ImageView) {
          layout(previewView.width, previewView.height)
        }
      }
    })
  }

  override fun connect(preview: Preview) {
    unfreeze()
    preview.surfaceProvider = previewView.surfaceProvider
  }

  override fun layout(width: Int, height: Int) {
    previewView.measure(exactly(width), exactly(height))
    previewView.layout(0, 0, width, height)
  }

  override fun onZoomChanged(zoom: PinchZoom) {
    previewView.pivotX = zoom.width / 2
    previewView.pivotY = zoom.height / 2
    previewView.scaleX = zoom.signedScaleX
    previewView.scaleY = zoom.scale
    previewView.translationX = zoom.translationX
    previewView.translationY = zoom.translationY
  }

  override fun freeze(): Boolean {
    if (isFrozen) {
      return true
    }
    val texture = textureView() ?: return false
    // The TextureView is as big as the stream, so its bitmap keeps every pixel
    // the zoom can use; PreviewView.getBitmap() would shrink it to the screen.
    val bitmap = texture.bitmap ?: return false
    val image = ImageView(context).apply {
      scaleType = ImageView.ScaleType.MATRIX
      imageMatrix = texture.getTransform(null)
      setImageBitmap(bitmap)
      layoutParams = FrameLayout.LayoutParams(texture.width, texture.height)
    }
    previewView.addView(image)
    image.measure(exactly(texture.width), exactly(texture.height))
    image.layout(texture.left, texture.top, texture.right, texture.bottom)
    // Same placement PreviewView gave the TextureView, so the copy lines up.
    image.pivotX = texture.pivotX
    image.pivotY = texture.pivotY
    image.scaleX = texture.scaleX
    image.scaleY = texture.scaleY
    image.translationX = texture.translationX
    image.translationY = texture.translationY
    image.rotation = texture.rotation
    frozenImage = image
    return true
  }

  override fun unfreeze() {
    val image = frozenImage ?: return
    frozenImage = null
    previewView.removeView(image)
    image.setImageDrawable(null)
  }

  override fun meteringPoint(zoom: PinchZoom, camera: Camera): MeteringPoint? {
    if (previewView.width == 0) {
      return null
    }
    val centre = zoom.toImage(zoom.width / 2, zoom.height / 2)
    val size = (1f / zoom.scale).coerceIn(MIN_METERING_SIZE, 1f)
    return previewView.meteringPointFactory.createPoint(centre.x, centre.y, size)
  }

  override fun visibleLuma(zoom: PinchZoom): Float? {
    val texture = textureView() ?: return null
    if (texture.width == 0 || texture.height == 0) {
      return null
    }
    val bitmap = lumaBitmapFor(texture)
    texture.getBitmap(bitmap)
    // Screen corners → PreviewView → the TextureView's own coordinates.
    val topLeft = zoom.toImage(0f, 0f)
    val bottomRight = zoom.toImage(zoom.width, zoom.height)
    val points = floatArrayOf(
      topLeft.x - texture.left, topLeft.y - texture.top,
      bottomRight.x - texture.left, bottomRight.y - texture.top
    )
    val inverse = Matrix()
    if (!texture.matrix.invert(inverse)) {
      return null
    }
    inverse.mapPoints(points)
    val scaleX = bitmap.width / texture.width.toFloat()
    val scaleY = bitmap.height / texture.height.toFloat()
    val area = RectF(
      minOf(points[0], points[2]) * scaleX,
      minOf(points[1], points[3]) * scaleY,
      maxOf(points[0], points[2]) * scaleX,
      maxOf(points[1], points[3]) * scaleY
    )
    return Luma.mean(bitmap, area)
  }

  override fun release() {
    unfreeze()
    lumaBitmap?.recycle()
    lumaBitmap = null
  }

  private fun textureView(): TextureView? =
    (0 until previewView.childCount).map(previewView::getChildAt).firstOrNull { it is TextureView } as? TextureView

  private fun lumaBitmapFor(texture: TextureView): Bitmap {
    val height = (Luma.SAMPLE_WIDTH * texture.height / texture.width).coerceAtLeast(1)
    val current = lumaBitmap
    if (current != null && current.height == height) {
      return current
    }
    current?.recycle()
    return Bitmap.createBitmap(Luma.SAMPLE_WIDTH, height, Bitmap.Config.ARGB_8888).also { lumaBitmap = it }
  }

  private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

  companion object {
    /** Smallest metering area, as a fraction of the frame, at high zoom. */
    const val MIN_METERING_SIZE = 0.1f
  }
}
