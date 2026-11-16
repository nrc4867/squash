package dev.chieppa.squash.screens.dress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentEdge
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceCurve
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.surface.surfaceHit
import dev.chieppa.squash.screens.sheet.bodyThrough
import dev.chieppa.squash.screens.sheet.surfaceThrough
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.OverlayBuilder

/** How far from an edge's point, in pixels, a press takes that point. */
internal const val GRAB_REACH = 10f

/**
 * One way [curve] shows: itself, on the other side of a pair, or mirrored across the middle; [back] takes a place on what shows to the
 * place on the curve as stored.
 */
internal class ShownCurve(val curve: SurfaceCurve, val back: (Anchor) -> Anchor)

/** [curve] as it shows: itself, on the other side of a pair, and mirrored across the middle when it is. */
internal fun shownCurves(character: Character, curve: SurfaceCurve): List<ShownCurve> {
    val own = curve.points.firstOrNull()?.part
    val twin = own?.let(character::part)?.let(character::twinOf)?.id
    val paired = if (own == null || twin == null) null else ShownCurve(curve.copy(points = curve.points.map { if (it.part == own) it.copy(part = twin) else it })) {
        if (it.part == twin) it.copy(part = own) else it
    }
    val mirrored = if (curve.mirrored) ShownCurve(curve.copy(points = curve.points.map { it.copy(v = -it.v) })) { it.copy(v = -it.v) } else null
    return listOfNotNull(ShownCurve(curve) { it }, paired, mirrored)
}

/** Where [anchor] lies on [body] at rest, or null on a part it does not hold. */
internal fun placeOf(body: AssembledCharacter, anchor: Anchor): Vec3? {
    val placed = body.part(anchor.part) ?: return null
    return placed.surface.pointAt(anchor)?.position?.plus(placed.origin)
}

/** The pixels of [curve]'s points through [sight], null where one does not show. */
internal fun pixelsOf(body: AssembledCharacter, curve: SurfaceCurve, sight: SurfaceView): List<Offset?> =
    curve.points.map { placeOf(body, it)?.let(sight::toPixel) }

/** The picked garment's edges on the open character, each as it shows, with the picked one's points as dots. */
internal fun OverlayBuilder.edges(state: AppState, look: SheetLook, sight: SurfaceView) {
    val session = state.session.value ?: return
    val character = session.document.value.character
    val garment = state.dress.garment.value?.let(character.wardrobe::garment) ?: return
    val body = session.assembly.output.value ?: return
    val picked = state.dress.edge.value
    garment.edges.forEachIndexed { i, edge ->
        for (shown in shownCurves(character, edge.curve)) {
            val pixels = pixelsOf(body, shown.curve, sight)
            val spans = pixels.zipWithNext() + if (shown.curve.closed && pixels.size > 2) listOf(pixels.last() to pixels.first()) else emptyList()
            spans.forEach { (a, b) -> if (a != null && b != null) line(a, b, look.active, look.activeWidth) }
            if (i == picked) pixels.filterNotNull().forEach { dot(it, look.dotRadius + look.dotEdge, look.ring) }
        }
    }
}

/** The nearest point of the picked garment's edges to [pixel], within [reach], as the edge's index and the point's, with how it shows. */
internal fun nearestPoint(character: Character, body: AssembledCharacter, garment: Garment, pixel: Offset, sight: SurfaceView, reach: Float, only: Int? = null): Triple<Int, Int, ShownCurve>? {
    var best: Triple<Int, Int, ShownCurve>? = null
    var nearest = reach
    garment.edges.forEachIndexed { i, edge ->
        if (only != null && i != only) return@forEachIndexed
        for (shown in shownCurves(character, edge.curve)) {
            pixelsOf(body, shown.curve, sight).forEachIndexed { j, at ->
                val distance = at?.let { (it - pixel).getDistance() } ?: return@forEachIndexed
                if (distance <= nearest) {
                    nearest = distance
                    best = Triple(i, j, shown)
                }
            }
        }
    }
    return best
}

/** [garment]'s edges with edge [index]'s curve given [points]. */
internal fun Garment.withPoints(index: Int, points: List<Anchor>): List<GarmentEdge> =
    edges.toMutableList().also { it[index] = it[index].copy(curve = it[index].curve.copy(points = points)) }

/**
 * Takes a press on a point of the picked garment's edges, of the picked edge alone with [pickedOnly], and drags it along the surface
 * till it lets go, the garment's shape written again as it goes, one step. False when the press is on no point.
 */
internal suspend fun AwaitPointerEventScope.dragEdgePoint(state: AppState, down: PointerInputChange, sight: SurfaceView, pickedOnly: Boolean): Boolean {
    val session = state.session.value ?: return false
    val character = session.document.value.character
    val garment = state.dress.garment.value?.let(character.wardrobe::garment) ?: return false
    val body = session.assembly.output.value ?: return false
    val only = if (pickedOnly) state.dress.edge.value?.takeIf { it in garment.edges.indices } ?: return false else null
    val (index, point, shown) = nearestPoint(character, body, garment, down.position, sight, GRAB_REACH, only) ?: return false
    down.consume()
    state.dress.edge.value = index
    val curve = garment.edges[index].curve
    val gesture: History<SquashDocument>.Transaction = session.history.begin()
    var released = false
    try {
        released = followToRelease(down.id, {}) { pixel ->
            val hit = state.bodyThrough(sight)?.let { body -> state.surfaceThrough(sight)?.let { surfaceHit(body, pixel, sight, it) } } ?: return@followToRelease
            val points = curve.points.toMutableList().also { it[point] = shown.back(hit.anchor) }
            gesture.update(shapeCommands(character, body, garment, garment.withPoints(index, points), garment.fill))
        }
    } finally {
        if (released) gesture.commit() else gesture.cancel()
    }
    return true
}
