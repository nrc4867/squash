package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.View

/** The state a pane's corner chip shows. */
enum class PaneChip { DRAWING, DRAWN, FROM_TEMPLATE, LIVE }

/** The chip for [pane] on a part with the views in [drawn] drawn and a stroke in [drawingIn]. */
fun paneChip(pane: SheetPane, drawn: Set<View>, drawingIn: View?): PaneChip {
    val view = pane.view ?: return PaneChip.LIVE
    return when (view) {
        drawingIn -> PaneChip.DRAWING
        in drawn -> PaneChip.DRAWN
        else -> PaneChip.FROM_TEMPLATE
    }
}
