package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.stretch.frame.ViewportOverlay

/** A tool that shows a test the free pane it was handed, so a test can find what shows where. */
internal class SeenFree(private val tool: PaneTool) : PaneTool by tool {
    var view: FullView? = null

    override fun fullOverlay(view: FullView): ViewportOverlay {
        this.view = view
        return tool.fullOverlay(view)
    }
}
