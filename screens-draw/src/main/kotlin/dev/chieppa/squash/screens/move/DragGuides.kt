package dev.chieppa.squash.screens.move

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.edit.rememberCurveLook
import dev.chieppa.squash.screens.edit.ring
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch

/** How far either way, in pixels, the line a drag is held to runs from where the handle started. */
private const val HELD_REACH = 4000f

/**
 * How a handle drag's guides draw: the line it is held to faint and dotted in [dev.chieppa.squash.screens.edit.CurveLook]'s
 * symmetry look, as Draw outline and Edit curves draw the line of symmetry, and
 * the snap line it lies on in the accent, as a stroke's caught guides draw, with a ring where the handle lands.
 */
data class DragGuideLook(
    val held: Color,
    val heldWidth: Float,
    val heldDash: Float,
    val heldGap: Float,
    val caught: Color,
    val caughtWidth: Float,
    val markRadius: Float,
)

/** The guides' drawing in the current theme and pixel density. */
@Composable
fun rememberDragGuideLook(): DragGuideLook {
    val c = Stretch.colors
    val curves = rememberCurveLook()
    val density = LocalDensity.current
    fun px(dp: Int) = with(density) { dp.dp.toPx() }
    return DragGuideLook(
        held = curves.symmetry,
        heldWidth = curves.symmetryWidth,
        heldDash = curves.symmetryDash,
        heldGap = curves.symmetryGap,
        caught = c.accent,
        caughtWidth = px(2),
        markRadius = px(11),
    )
}

/** [shown]'s guides in [pane], or nothing while the drag is in another pane or none is on. */
fun dragGuides(shown: DragShown?, pane: Any, look: DragGuideLook): ViewportOverlay {
    if (shown == null || shown.pane != pane) return ViewportOverlay.Empty
    return buildOverlay { guides(shown, look) }
}

private fun OverlayBuilder.guides(shown: DragShown, look: DragGuideLook) {
    shown.direction?.let { direction ->
        dashed(listOf(shown.start - direction * HELD_REACH, shown.start + direction * HELD_REACH), look.held, look.heldWidth, look.heldDash, look.heldGap)
    }
    val snap = shown.snap ?: return
    for (line in snap.lines) line.points.zipWithNext().forEach { (from, to) -> line(from, to, look.caught, look.caughtWidth) }
    ring(snap.pixel, look.markRadius, look.caught, look.caughtWidth)
}
