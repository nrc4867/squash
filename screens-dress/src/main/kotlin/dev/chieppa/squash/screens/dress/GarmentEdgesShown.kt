package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.dress.PaintedGarment
import dev.chieppa.squash.character.dress.paintedOf
import dev.chieppa.squash.character.dress.placeOnBody
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.shape.cellMiddle
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceCurve
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.bodyThrough
import dev.chieppa.squash.screens.sheet.surfaceThrough
import dev.chieppa.squash.screens.surface.surfaceHit
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.OverlayBuilder

/** How far from a hem's handle, in pixels, a press takes that handle. */
internal const val GRAB_REACH = 10f

/**
 * One way [curve] shows: itself, or on the other side of a pair; [back] takes a place on what shows to the place on the curve as
 * stored.
 */
internal class ShownCurve(val curve: SurfaceCurve, val back: (Anchor) -> Anchor)

/** [curve] as it shows: itself, and on the other side of a pair. */
internal fun shownCurves(character: Character, curve: SurfaceCurve): List<ShownCurve> {
    val own = curve.points.firstOrNull()?.part
    val twin = own?.let(character::part)?.let(character::twinOf)?.id
    val paired = if (own == null || twin == null) null else ShownCurve(curve.copy(points = curve.points.map { if (it.part == own) it.copy(part = twin) else it })) {
        if (it.part == twin) it.copy(part = own) else it
    }
    return listOfNotNull(ShownCurve(curve) { it }, paired)
}

/** Where [anchor] lies on [body] at rest, or null on a part it does not hold. */
internal fun placeOf(body: AssembledCharacter, anchor: Anchor): Vec3? = placeOnBody(body, anchor)

/** The pixels of [curve]'s points through [sight], null where one does not show. */
internal fun pixelsOf(body: AssembledCharacter, curve: SurfaceCurve, sight: SurfaceView): List<Offset?> =
    curve.points.map { placeOf(body, it)?.let(sight::toPixel) }

/** The picked garment worn over the open character, with the character and body, or null. */
internal class PickedGarment(val character: Character, val body: AssembledCharacter, val garment: Garment) {
    val painted: PaintedGarment get() = paintedOf(character, body, garment)
}

internal fun AppState.pickedGarment(): PickedGarment? {
    val session = session.value ?: return null
    val character = session.document.value.character
    val garment = dress.garment.value?.let(character.wardrobe::garment) ?: return null
    val body = session.assembly.output.value ?: return null
    return PickedGarment(character, body, garment)
}

/** How big a dot marks a cell an opening will cut, as a share of a handle's. */
private const val CUT_DOT_SHARE = 0.5f

/**
 * The picked garment's edges on the open character, each as it shows, the curves its cloth is cut on, seams thinner; the handles of the
 * picked one, or of every one with Handles, as dots; [dragged], a hem being dragged, as it will be cut; and each painted cell an opening
 * will cut, a dot in the warn colour where it lies.
 */
internal fun OverlayBuilder.edges(state: AppState, look: SheetLook, sight: SurfaceView, dragged: SurfaceCurve? = null) {
    val picked = state.pickedGarment() ?: return
    val painted = picked.painted
    val chosen = state.dress.edge.value
    painted.edges.forEachIndexed { i, on ->
        val width = if (on.edge.seam) look.guideWidth else look.activeWidth
        for (shown in shownCurves(picked.character, on.edge.curve)) curveLines(pixelsOf(picked.body, shown.curve, sight), look.active, width)
    }
    dragged?.let { curve -> for (shown in shownCurves(picked.character, curve)) curveLines(pixelsOf(picked.body, shown.curve, sight), look.ring, look.activeWidth) }
    painted.hems.forEachIndexed { i, hem ->
        if (hem == null || (i != chosen && !state.dress.handles.value)) return@forEachIndexed
        for (shown in shownCurves(picked.character, SurfaceCurve(hem.handles))) {
            pixelsOf(picked.body, shown.curve, sight).filterNotNull().forEach { dot(it, look.dotRadius + look.dotEdge, look.ring) }
        }
    }
    val cut = cutCellsOf(picked.character, picked.body, picked.garment, painted)
    if (cut.isEmpty) return
    val chart = painted.chart
    for (part in chart.parts) {
        val sides = listOf(part.id) + chart.twins.filterValues { it == part.id }.keys
        for (cell in 0 until CHART_CELLS) {
            if (!cut.held(part.id, cell)) continue
            for (side in sides) placeOf(picked.body, cellMiddle(side, part.patch, cell))?.let(sight::toPixel)?.let { dot(it, look.dotRadius * CUT_DOT_SHARE, look.warn) }
        }
    }
}

/** Lines through [pixels], closed round, skipping any span with an end that does not show. */
private fun OverlayBuilder.curveLines(pixels: List<Offset?>, color: Color, width: Float) {
    val spans = pixels.zipWithNext() + if (pixels.size > 2) listOf(pixels.last() to pixels.first()) else emptyList()
    spans.forEach { (a, b) -> if (a != null && b != null) line(a, b, color, width) }
}

/** A handle of a garment's hem: its edge [edge], the handle [handle] of its hem's handles, and [shown], how that side shows. */
internal class HemHandleAt(val edge: Int, val handle: Int, val shown: ShownCurve)

/** The nearest handle of [painted]'s hems to [pixel], within [reach], on either side of a pair. */
internal fun nearestHandle(character: Character, body: AssembledCharacter, painted: PaintedGarment, pixel: Offset, sight: SurfaceView, reach: Float): HemHandleAt? {
    var best: HemHandleAt? = null
    var nearest = reach
    painted.hems.forEachIndexed { i, hem ->
        if (hem == null) return@forEachIndexed
        for (shown in shownCurves(character, SurfaceCurve(hem.handles))) {
            pixelsOf(body, shown.curve, sight).forEachIndexed { j, at ->
                val distance = at?.let { (it - pixel).getDistance() } ?: return@forEachIndexed
                if (distance <= nearest) {
                    nearest = distance
                    best = HemHandleAt(i, j, shown)
                }
            }
        }
    }
    return best
}

/**
 * Takes a press on a handle of the picked garment's hems, of the picked edge alone with [pickedOnly], and drags it along the surface
 * till it lets go, the hem bending through it and the cells either side of where it moved repainted as it goes (see [redrawnEdge]), one
 * step; [preview] is handed the hem as dragged so far, and null when the drag ends. False when the press is on no handle.
 */
internal suspend fun AwaitPointerEventScope.dragEdgePoint(
    state: AppState,
    down: PointerInputChange,
    sight: SurfaceView,
    pickedOnly: Boolean,
    preview: (SurfaceCurve?) -> Unit = {},
): Boolean {
    val picked = state.pickedGarment() ?: return false
    val session = state.session.value ?: return false
    val held = picked.painted
    val at = nearestHandle(picked.character, picked.body, held, down.position, sight, GRAB_REACH) ?: return false
    if (pickedOnly && state.dress.edge.value != at.edge) return false
    val hem = held.hems[at.edge] ?: return false
    down.consume()
    state.dress.edge.value = at.edge
    val from = hem.curve()
    val gesture: History<SquashDocument>.Transaction = session.history.begin()
    var released = false
    try {
        released = followToRelease(down.id, {}) { pixel ->
            val hit = state.bodyThrough(sight)?.let { body -> state.surfaceThrough(sight)?.let { surfaceHit(body, pixel, sight, it) } } ?: return@followToRelease
            val to = at.shown.back(hit.anchor)
            if (to.part != hem.part || to.patch != hem.patch) return@followToRelease
            val moved = hem.moved(at.handle, to).curve()
            preview(moved)
            gesture.update(picked.character.edgeMovedCommand(picked.body, picked.garment, held, at.edge, from, moved))
        }
    } finally {
        preview(null)
        if (released) gesture.commit() else gesture.cancel()
    }
    return true
}
