package expo.modules.mirrorview

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MirrorViewModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("MirrorView")

    View(MirrorView::class) {
      Events("onSettingsGesture", "onCameraInfo")

      Prop("autoLight") { view: MirrorView, value: Boolean -> view.autoLight = value }
      Prop("facePriority") { view: MirrorView, value: Boolean -> view.facePriority = value }
      Prop("highResolution") { view: MirrorView, value: Boolean -> view.highResolution = value }
      Prop("gpuSharpening") { view: MirrorView, value: Boolean -> view.gpuSharpening = value }
      Prop("trueView") { view: MirrorView, value: Boolean -> view.trueView = value }

      OnViewDidUpdateProps { view ->
        view.applySettings()
      }

      OnViewDestroys { view ->
        view.release()
      }
    }
  }
}
