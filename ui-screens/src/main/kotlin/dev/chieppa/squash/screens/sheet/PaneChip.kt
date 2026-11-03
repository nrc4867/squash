package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.View

/** The state a pane's corner chip shows. */
enum class PaneChip { DRAWING, DRAWN, FROM_TEMPLATE, LIVE, REFIT }

/** The chip for [pane] on a part with the views in [drawn] drawn and a stroke in [drawingIn], while the graph is [refitting] or not. */
fun paneChip(pane: SheetPane, drawn: Set<View>, drawingIn: View?, refitting: Boolean = false): PaneChip {
    val view = pane.view ?: return if (refitting) PaneChip.REFIT else PaneChip.LIVE
    return when (view) {
        drawingIn -> PaneChip.DRAWING
        in drawn -> PaneChip.DRAWN
        else -> PaneChip.FROM_TEMPLATE
    }
}
