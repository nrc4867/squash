package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.hair.fit.FlatOutline
import dev.chieppa.squash.character.hair.fit.HeadView
import dev.chieppa.squash.character.hair.fit.drawnOutline
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.OverlayBuilder

/** How squarely a pane must look along an outline's view to show it: the cosine of the angle between them. */
private const val FACING = 0.97f

private const val LINE_WIDTH = 1.5f
private const val TIP_RADIUS = 3.5f
private const val VALLEY_RADIUS = 2.5f

/**
 * Draws [head]'s hair outline through [sight], when the pane looks along one of its views: the outline in [ink], each tip a dot in
 * [tipInk] and each valley a smaller dot in [valleyInk], [density] pixels a unit; laid in front of the head so nothing hides it.
 */
internal fun OverlayBuilder.hairOutlineDrawn(sight: SurfaceView, character: Character, head: PlacedPart, density: Float, ink: Color, tipInk: Color, valleyInk: Color) {
    val id: PartId = head.id ?: return
    val outline = character.hairOutlineOn(id)?.let { drawnOutline(character, head, it) } ?: return
    val box = head.box ?: return
    val size = HeadView.sizeOf(character, head)
    for (drawn in outline.views) {
        val view = HeadView.of(head, size, drawn.view)
        if ((sight.toViewer(box.center) dot view.toViewer) < FACING) continue
        val flat = FlatOutline.of(drawn)
        val front = view.depth(box.center) + view.drawn(box.radius)
        val pixels = flat.points.map { sight.toPixel(view.body(it, front)) }
        if (pixels.any { it == null }) continue
        val path: List<Offset> = pixels.filterNotNull()
        for (i in path.indices) line(path[i], path[(i + 1) % path.size], ink, LINE_WIDTH * density)
        flat.valleys.forEach { dot(path[it], VALLEY_RADIUS * density, valleyInk) }
        flat.tips.forEach { dot(path[it], TIP_RADIUS * density, tipInk) }
    }
}
