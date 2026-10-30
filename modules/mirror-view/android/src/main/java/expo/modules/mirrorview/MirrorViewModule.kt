package expo.modules.mirrorview

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MirrorViewModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("MirrorView")

    View(MirrorView::class) {
      Events("onShortcutsGesture", "onShortcutsDrag", "onFreezeChange", "onCameraInfo")

      Prop("autoLight") { view: MirrorView, value: Boolean -> view.autoLight = value }
      Prop("lightSensitivity") { view: MirrorView, value: Int -> view.lightSensitivity = value }
      Prop("facePriority") { view: MirrorView, value: Boolean -> view.facePriority = value }
      Prop("highResolution") { view: MirrorView, value: Boolean -> view.highResolution = value }
      Prop("gpuSharpening") { view: MirrorView, value: Boolean -> view.gpuSharpening = value }
      Prop("sharpening") { view: MirrorView, value: String -> view.sharpening = Sharpening.fromProp(value) }
      Prop("trueView") { view: MirrorView, value: Boolean -> view.trueView = value }
      Prop("zoomHaptics") { view: MirrorView, value: Boolean -> view.zoomHaptics = value }
      Prop("dragInertia") { view: MirrorView, value: Boolean -> view.dragInertia = value }
      Prop("rememberZoom") { view: MirrorView, value: Boolean -> view.restoresZoom = value }
      Prop("volumeZoom") { view: MirrorView, value: Boolean -> view.volumeZoom = value }
      Prop("fillLight") { view: MirrorView, value: Boolean -> view.fillLight = value }

      AsyncFunction("toggleFreeze") { view: MirrorView ->
        view.toggleFreeze()
      }

      OnViewDidUpdateProps { view ->
        view.applySettings()
      }

      OnViewDestroys { view ->
        view.release()
      }
    }
  }
}
