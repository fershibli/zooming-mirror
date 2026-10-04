package expo.modules.mirrorview

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MirrorViewModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("MirrorView")

    View(MirrorView::class) {
      OnViewDestroys { view ->
        view.release()
      }
    }
  }
}
