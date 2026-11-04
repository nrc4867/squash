package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.View

/** The state a pane's corner chip shows. */
enum class PaneChip { DRAWING, DRAWN, FROM_TEMPLATE, LIVE, REFIT, THIS_SIDE, HIDES_PAIR }

/**
 * The chip for a pane holding [view], null for Full, on a part with the views in [drawn] drawn and a stroke in [drawingIn],
 * while the graph is [refitting] or not. A view in [overridden] says whose outline it shows, a warning with [mirror] on since
 * an edit then lands on the pair, under the override. A view past the others in [conflicted] leaves its corner to the highlight.
 */
fun paneChip(
    view: View?,
    drawn: Set<View>,
    drawingIn: View?,
    refitting: Boolean = false,
    overridden: Set<View> = emptySet(),
    mirror: Boolean = true,
    conflicted: Set<View> = emptySet(),
): PaneChip? = when (view) {
    null -> if (refitting) PaneChip.REFIT else PaneChip.LIVE
    drawingIn -> PaneChip.DRAWING
    in overridden -> if (mirror) PaneChip.HIDES_PAIR else PaneChip.THIS_SIDE
    in conflicted -> null
    in drawn -> PaneChip.DRAWN
    else -> PaneChip.FROM_TEMPLATE
}
