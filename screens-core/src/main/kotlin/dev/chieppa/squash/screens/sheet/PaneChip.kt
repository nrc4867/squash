package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.View

/** What a pane's corner chip says is happening there. */
enum class PaneChip { DRAWING, FIT_ERROR, REFIT, THIS_SIDE, HIDES_PAIR, SURFACE_REFUSED }

/**
 * The chip for a pane holding [view], null for Full, with a stroke in [drawingIn], while the graph is [refitting] or not, and with a fit's
 * [fitError] if it holds one. Full lights up while a tool that needs a locked pane is [refused] there. A view in [overridden] says whose outline it
 * shows, a warning with [mirror] on since an edit then lands on the pair, under the override. A view past the others in [conflicted] leaves its
 * corner to the highlight. With nothing happening a pane has no chip.
 */
fun paneChip(
    view: View?,
    drawingIn: View? = null,
    refitting: Boolean = false,
    overridden: Set<View> = emptySet(),
    mirror: Boolean = true,
    conflicted: Set<View> = emptySet(),
    fitError: Float? = null,
    refused: Boolean = false,
): PaneChip? = when {
    view == null -> when {
        refused -> PaneChip.SURFACE_REFUSED
        refitting -> PaneChip.REFIT
        else -> null
    }
    view == drawingIn -> PaneChip.DRAWING
    view in overridden -> if (mirror) PaneChip.HIDES_PAIR else PaneChip.THIS_SIDE
    view in conflicted -> null
    fitError != null -> PaneChip.FIT_ERROR
    else -> null
}
