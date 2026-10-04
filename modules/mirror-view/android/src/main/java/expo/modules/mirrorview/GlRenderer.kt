package expo.modules.mirrorview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

/**
 * The GPU path: the camera writes into a texture, and every frame is drawn in
 * two passes into a TextureView the size of the screen. Pass 1 crops, zooms
 * and upscales (Catmull-Rom), pass 2 sharpens (see [GlShaders]).
 *
 * All GL work runs on one thread. The main thread only posts the zoom and the
 * freeze to it, so nothing GL is ever shared between threads.
 */
class GlRenderer(
  private val context: Context,
  private val onFailure: (String) -> Unit
) : MirrorRenderer, TextureView.SurfaceTextureListener {
  private val textureView = TextureView(context).apply {
    isOpaque = true
    surfaceTextureListener = this@GlRenderer
  }
  private val thread = HandlerThread("MirrorGl").apply { start() }
  private val gl = Handler(thread.looper)
  private val glExecutor = Executor { gl.post(it) }
  private val main = Handler(Looper.getMainLooper())

  override val view: View
    get() = textureView
  override val kind = "gpu"

  @Volatile private var frozen = false
  @Volatile private var hasFrame = false
  @Volatile private var released = false

  /** Part of the frame the unzoomed screen shows, for metering on main. */
  @Volatile private var cropX = 1f
  @Volatile private var cropY = 1f
  private var lumaBitmap: Bitmap? = null

  override val isFrozen
    get() = frozen

  // Everything below belongs to the GL thread.
  private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
  private var eglConfig: EGLConfig? = null
  private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
  private var window: EGLSurface = EGL14.EGL_NO_SURFACE
  private var outputTexture: SurfaceTexture? = null
  private var outWidth = 0
  private var outHeight = 0

  private var upscaleCamera = 0
  private var upscaleFrozen = 0
  private var sharpen = 0
  private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
    .asFloatBuffer().apply {
      put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
      position(0)
    }

  private var cameraTexture = 0
  private var cameraSurfaceTexture: SurfaceTexture? = null
  private val st = FloatArray(16)
  private var displayToBuffer = IDENTITY
  private var bufferWidth = 0
  private var bufferHeight = 0
  private var rotationDegrees = 0

  private var passTexture = 0
  private var passFramebuffer = 0
  private var frozenTexture = 0
  private var frozenFramebuffer = 0
  private var frozenWidth = 0
  private var frozenHeight = 0

  private var zoomScaleX = 1f
  private var zoomScaleY = 1f
  private var zoomTranslationX = 0f
  private var zoomTranslationY = 0f

  init {
    gl.post {
      try {
        setUpEgl()
        upscaleCamera = program(GlShaders.UPSCALE_CAMERA)
        upscaleFrozen = program(GlShaders.UPSCALE_FROZEN)
        sharpen = program(GlShaders.SHARPEN)
      } catch (e: Exception) {
        fail("GPU setup failed", e)
      }
    }
  }

  // ---- MirrorRenderer, main thread ----

  override fun connect(preview: Preview) {
    unfreeze()
    preview.setSurfaceProvider(glExecutor) { request -> provideSurface(request) }
  }

  override fun layout(width: Int, height: Int) {
    textureView.measure(exactly(width), exactly(height))
    textureView.layout(0, 0, width, height)
  }

  override fun onZoomChanged(zoom: PinchZoom) {
    if (zoom.width == 0f || zoom.height == 0f) {
      return
    }
    val scaleX = zoom.signedScaleX
    val scaleY = zoom.scale
    val translationX = zoom.translationX / zoom.width
    val translationY = zoom.translationY / zoom.height
    gl.post {
      zoomScaleX = scaleX
      zoomScaleY = scaleY
      zoomTranslationX = translationX
      zoomTranslationY = translationY
      render()
    }
  }

  override fun freeze(): Boolean {
    if (frozen) {
      return true
    }
    if (!hasFrame) {
      return false
    }
    frozen = true
    gl.post {
      copyFrame()
      render()
    }
    return true
  }

  override fun unfreeze() {
    if (!frozen) {
      return
    }
    frozen = false
    gl.post {
      deleteFrozen()
      render()
    }
  }

  override fun meteringPoint(zoom: PinchZoom, camera: Camera): MeteringPoint? {
    val display = textureView.display ?: return null
    if (zoom.width == 0f || zoom.height == 0f) {
      return null
    }
    // Same mapping as the shader: screen centre → image → frame, which is the
    // upright, mirrored picture this factory expects.
    val centre = zoom.toImage(zoom.width / 2, zoom.height / 2)
    val frameX = 0.5f + (centre.x / zoom.width - 0.5f) * cropX
    val frameY = 0.5f + (centre.y / zoom.height - 0.5f) * cropY
    val size = (minOf(cropX, cropY) / zoom.scale).coerceIn(PreviewRenderer.MIN_METERING_SIZE, 1f)
    return DisplayOrientedMeteringPointFactory(display, camera.cameraInfo, 1f, 1f)
      .createPoint(frameX, frameY, size)
  }

  override fun visibleLuma(zoom: PinchZoom): Float? {
    if (!textureView.isAvailable || textureView.width == 0) {
      return null
    }
    // The view shows exactly the area on screen, so the whole copy counts.
    val height = (Luma.SAMPLE_WIDTH * textureView.height / textureView.width).coerceAtLeast(1)
    val bitmap = lumaBitmap?.takeIf { it.height == height }
      ?: Bitmap.createBitmap(Luma.SAMPLE_WIDTH, height, Bitmap.Config.ARGB_8888).also {
        lumaBitmap?.recycle()
        lumaBitmap = it
      }
    textureView.getBitmap(bitmap)
    return Luma.mean(bitmap)
  }

  override fun release() {
    released = true
    textureView.surfaceTextureListener = null
    lumaBitmap?.recycle()
    lumaBitmap = null
    gl.post { tearDown() }
    // Leaves the thread alive a little longer, so CameraX can still hand back
    // the surface it was writing to and have it released here.
    gl.postDelayed({ thread.quitSafely() }, QUIT_DELAY_MS)
  }

  // ---- TextureView, main thread ----

  override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    gl.post {
      outputTexture = surface
      resize(width, height)
    }
  }

  override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    gl.post { resize(width, height) }
  }

  override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
    // Released on the GL thread, after the EGL surface on top of it is gone.
    gl.post {
      destroyWindow()
      outputTexture = null
      surface.release()
    }
    return false
  }

  override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

  // ---- GL thread ----

  private fun provideSurface(request: SurfaceRequest) {
    if (released || eglContext == EGL14.EGL_NO_CONTEXT) {
      request.willNotProvideSurface()
      return
    }
    makeCurrent()
    val texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

    val resolution = request.resolution
    val surfaceTexture = SurfaceTexture(texture).apply {
      setDefaultBufferSize(resolution.width, resolution.height)
      setOnFrameAvailableListener({ onFrame(it) }, gl)
    }
    val surface = Surface(surfaceTexture)
    cameraTexture = texture
    cameraSurfaceTexture = surfaceTexture
    bufferWidth = resolution.width
    bufferHeight = resolution.height
    hasFrame = false

    request.setTransformationInfoListener(glExecutor) { info ->
      rotationDegrees = info.rotationDegrees
      displayToBuffer =
        if (info.hasCameraTransform()) IDENTITY else displayToBuffer(info.rotationDegrees, info.isMirroring)
      updateCrop()
      render()
    }
    request.provideSurface(surface, glExecutor) {
      surface.release()
      surfaceTexture.release()
      if (eglContext != EGL14.EGL_NO_CONTEXT) {
        makeCurrent()
        GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
      }
      if (cameraSurfaceTexture === surfaceTexture) {
        cameraSurfaceTexture = null
        cameraTexture = 0
        hasFrame = false
      }
    }
  }

  private fun onFrame(surfaceTexture: SurfaceTexture) {
    if (surfaceTexture !== cameraSurfaceTexture || eglContext == EGL14.EGL_NO_CONTEXT) {
      return
    }
    makeCurrent()
    // Always taken, frozen or not: a camera whose frames are left waiting can
    // stall on some phones.
    surfaceTexture.updateTexImage()
    surfaceTexture.getTransformMatrix(st)
    hasFrame = true
    if (!frozen) {
      render()
    }
  }

  private fun resize(width: Int, height: Int) {
    if (eglContext == EGL14.EGL_NO_CONTEXT) {
      return
    }
    outWidth = width
    outHeight = height
    val output = outputTexture ?: return
    if (window == EGL14.EGL_NO_SURFACE) {
      window = EGL14.eglCreateWindowSurface(display, eglConfig, output, intArrayOf(EGL14.EGL_NONE), 0)
      if (window == EGL14.EGL_NO_SURFACE) {
        fail("eglCreateWindowSurface failed: ${EGL14.eglGetError()}", null)
        return
      }
    }
    makeCurrent()
    passTexture = recreateTexture(passTexture, width, height)
    passFramebuffer = framebufferFor(passFramebuffer, passTexture)
    updateCrop()
    render()
  }

  private fun updateCrop() {
    if (bufferWidth == 0 || outWidth == 0) {
      return
    }
    val (frameWidth, frameHeight) = frameSize()
    val frameAspect = frameWidth / frameHeight
    val viewAspect = outWidth.toFloat() / outHeight
    if (frameAspect > viewAspect) {
      cropX = viewAspect / frameAspect
      cropY = 1f
    } else {
      cropX = 1f
      cropY = frameAspect / viewAspect
    }
  }

  /** Frame size as displayed: the buffer turned upright. */
  private fun frameSize(): Pair<Float, Float> =
    if (rotationDegrees % 180 == 90) {
      bufferHeight.toFloat() to bufferWidth.toFloat()
    } else {
      bufferWidth.toFloat() to bufferHeight.toFloat()
    }

  private fun render() {
    if (window == EGL14.EGL_NO_SURFACE || passFramebuffer == 0 || !(hasFrame || frozen)) {
      return
    }
    if (frozen && frozenTexture == 0) {
      return
    }
    makeCurrent()
    val (frameWidth, frameHeight) = frameSize()
    // Source pixels per screen pixel: below one, the zoom is enlarging.
    val sourcePerScreen = cropX * frameWidth / (zoomScaleY * outWidth)
    val texelOnScreen = 1f / sourcePerScreen

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, passFramebuffer)
    GLES20.glViewport(0, 0, outWidth, outHeight)
    val upscale = if (frozen) upscaleFrozen else upscaleCamera
    GLES20.glUseProgram(upscale)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    if (frozen) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frozenTexture)
    } else {
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
      GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(upscale, "uSt"), 1, false, st, 0)
      GLES20.glUniformMatrix3fv(
        GLES20.glGetUniformLocation(upscale, "uDisplayToBuffer"), 1, false, displayToBuffer, 0
      )
    }
    GLES20.glUniform1i(GLES20.glGetUniformLocation(upscale, "uFrame"), 0)
    GLES20.glUniform4f(
      GLES20.glGetUniformLocation(upscale, "uZoom"),
      zoomScaleX, zoomScaleY, zoomTranslationX, zoomTranslationY
    )
    GLES20.glUniform2f(GLES20.glGetUniformLocation(upscale, "uCrop"), cropX, cropY)
    GLES20.glUniform2f(GLES20.glGetUniformLocation(upscale, "uFrameSize"), frameWidth, frameHeight)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(upscale, "uBicubic"), if (sourcePerScreen < 1f) 1f else 0f)
    drawQuad(upscale)

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    GLES20.glViewport(0, 0, outWidth, outHeight)
    GLES20.glUseProgram(sharpen)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, passTexture)
    GLES20.glUniform1i(GLES20.glGetUniformLocation(sharpen, "uImage"), 0)
    // Neighbours half a camera pixel apart, so it sharpens the picture's own
    // edges rather than the smooth inside of an enlarged pixel.
    val distance = (texelOnScreen * 0.5f).coerceIn(1f, 4f)
    GLES20.glUniform2f(GLES20.glGetUniformLocation(sharpen, "uStep"), distance / outWidth, distance / outHeight)
    val sharpness = (0.2f + 0.12f * (zoomScaleY - 1f)).coerceIn(0.2f, 0.8f)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(sharpen, "uSharpness"), sharpness)
    drawQuad(sharpen)

    EGL14.eglSwapBuffers(display, window)
  }

  /** Copies the current frame, upright, into a texture of its own. */
  private fun copyFrame() {
    if (!hasFrame || cameraTexture == 0) {
      frozen = false
      return
    }
    makeCurrent()
    val (frameWidth, frameHeight) = frameSize()
    frozenWidth = frameWidth.toInt()
    frozenHeight = frameHeight.toInt()
    frozenTexture = recreateTexture(frozenTexture, frozenWidth, frozenHeight)
    frozenFramebuffer = framebufferFor(frozenFramebuffer, frozenTexture)

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frozenFramebuffer)
    GLES20.glViewport(0, 0, frozenWidth, frozenHeight)
    GLES20.glUseProgram(upscaleCamera)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
    GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(upscaleCamera, "uSt"), 1, false, st, 0)
    GLES20.glUniformMatrix3fv(
      GLES20.glGetUniformLocation(upscaleCamera, "uDisplayToBuffer"), 1, false, displayToBuffer, 0
    )
    GLES20.glUniform1i(GLES20.glGetUniformLocation(upscaleCamera, "uFrame"), 0)
    // No zoom and no crop: one frame pixel per texel, stored with y up.
    GLES20.glUniform4f(GLES20.glGetUniformLocation(upscaleCamera, "uZoom"), 1f, 1f, 0f, 0f)
    GLES20.glUniform2f(GLES20.glGetUniformLocation(upscaleCamera, "uCrop"), 1f, 1f)
    GLES20.glUniform2f(GLES20.glGetUniformLocation(upscaleCamera, "uFrameSize"), frameWidth, frameHeight)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(upscaleCamera, "uBicubic"), 0f)
    drawQuad(upscaleCamera)
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
  }

  private fun deleteFrozen() {
    if (frozenTexture == 0) {
      return
    }
    makeCurrent()
    GLES20.glDeleteFramebuffers(1, intArrayOf(frozenFramebuffer), 0)
    GLES20.glDeleteTextures(1, intArrayOf(frozenTexture), 0)
    frozenFramebuffer = 0
    frozenTexture = 0
  }

  private fun drawQuad(program: Int) {
    val position = GLES20.glGetAttribLocation(program, "aPosition")
    GLES20.glEnableVertexAttribArray(position)
    GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    GLES20.glDisableVertexAttribArray(position)
  }

  private fun recreateTexture(existing: Int, width: Int, height: Int): Int {
    if (existing != 0) {
      GLES20.glDeleteTextures(1, intArrayOf(existing), 0)
    }
    val texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
    GLES20.glTexImage2D(
      GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
    )
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    return texture
  }

  private fun framebufferFor(existing: Int, texture: Int): Int {
    val framebuffer = if (existing != 0) existing else IntArray(1).also { GLES20.glGenFramebuffers(1, it, 0) }[0]
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
    GLES20.glFramebufferTexture2D(
      GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0
    )
    val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      fail("Framebuffer incomplete: $status", null)
    }
    return framebuffer
  }

  private fun setUpEgl() {
    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    val version = IntArray(2)
    check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
    val configs = arrayOfNulls<EGLConfig>(1)
    val count = IntArray(1)
    val attributes = intArrayOf(
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
      EGL14.EGL_NONE
    )
    check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
      "eglChooseConfig found nothing"
    }
    eglConfig = configs[0]
    eglContext = EGL14.eglCreateContext(
      display, eglConfig, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
    )
    check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
    // A 1×1 surface keeps the context usable before the TextureView exists.
    pbuffer = EGL14.eglCreatePbufferSurface(
      display, eglConfig, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
    )
    makeCurrent()
  }

  private fun makeCurrent() {
    val surface = if (window != EGL14.EGL_NO_SURFACE) window else pbuffer
    EGL14.eglMakeCurrent(display, surface, surface, eglContext)
  }

  private fun destroyWindow() {
    if (window == EGL14.EGL_NO_SURFACE) {
      return
    }
    EGL14.eglMakeCurrent(display, pbuffer, pbuffer, eglContext)
    EGL14.eglDestroySurface(display, window)
    window = EGL14.EGL_NO_SURFACE
  }

  private fun tearDown() {
    if (eglContext == EGL14.EGL_NO_CONTEXT) {
      return
    }
    makeCurrent()
    deleteFrozen()
    if (passFramebuffer != 0) {
      GLES20.glDeleteFramebuffers(1, intArrayOf(passFramebuffer), 0)
      GLES20.glDeleteTextures(1, intArrayOf(passTexture), 0)
    }
    listOf(upscaleCamera, upscaleFrozen, sharpen).filter { it != 0 }.forEach(GLES20::glDeleteProgram)
    destroyWindow()
    outputTexture?.release()
    outputTexture = null
    // The camera's SurfaceTexture is released by the request's result listener.
    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    EGL14.eglDestroySurface(display, pbuffer)
    EGL14.eglDestroyContext(display, eglContext)
    EGL14.eglTerminate(display)
    eglContext = EGL14.EGL_NO_CONTEXT
  }

  private fun program(fragment: String): Int {
    val vertexShader = shader(GLES20.GL_VERTEX_SHADER, GlShaders.VERTEX)
    val fragmentShader = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    check(status[0] == GLES20.GL_TRUE) { "Link failed: ${GLES20.glGetProgramInfoLog(program)}" }
    GLES20.glDeleteShader(vertexShader)
    GLES20.glDeleteShader(fragmentShader)
    return program
  }

  private fun shader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    check(status[0] == GLES20.GL_TRUE) { "Compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
    return shader
  }

  private fun fail(message: String, error: Exception?) {
    Log.e(TAG, message, error)
    main.post { if (!released) onFailure(message) }
  }

  private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

  companion object {
    private const val TAG = "MirrorGl"
    private const val QUIT_DELAY_MS = 2000L

    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /**
     * Frame point (y down) → buffer point, for streams the camera did not
     * already turn upright: undo the mirroring, then the rotation. Column-major
     * like GL expects.
     */
    fun displayToBuffer(rotationDegrees: Int, mirroring: Boolean): FloatArray {
      // Rows of the 3×3 affine matrices, written row by row for readability.
      val mirror = if (mirroring) rowMajor(-1f, 0f, 1f, 0f, 1f, 0f) else rowMajor(1f, 0f, 0f, 0f, 1f, 0f)
      val unrotate = when (rotationDegrees) {
        90 -> rowMajor(0f, 1f, 0f, -1f, 0f, 1f)
        180 -> rowMajor(-1f, 0f, 1f, 0f, -1f, 1f)
        270 -> rowMajor(0f, -1f, 1f, 1f, 0f, 0f)
        else -> rowMajor(1f, 0f, 0f, 0f, 1f, 0f)
      }
      return columnMajor(multiply(unrotate, mirror))
    }

    private fun rowMajor(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) =
      floatArrayOf(a, b, c, d, e, f, 0f, 0f, 1f)

    private fun multiply(left: FloatArray, right: FloatArray): FloatArray =
      FloatArray(9) { index ->
        val row = index / 3
        val column = index % 3
        (0 until 3).sumOf { (left[row * 3 + it] * right[it * 3 + column]).toDouble() }.toFloat()
      }

    private fun columnMajor(m: FloatArray) =
      floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])
  }
}
