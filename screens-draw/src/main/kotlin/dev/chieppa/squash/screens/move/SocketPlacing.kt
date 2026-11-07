package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.surface.surfaceHit
import dev.chieppa.stretch.frame.OverlayBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How long a socket's outward tick reaches past its ring, in pixels. */
private const val TICK = 15f

/** How much a twin's ring and tick show, as the board draws them faint. */
private const val TWIN_ALPHA = 0.45f

/** The thin ink ring round a socket about to be placed, this far outside its accent ring. */
private const val OUTER_RING = 2f
private const val RING_STROKE = 3f
private const val THIN_STROKE = 1f
private const val TWIN_DASH = 3f
private const val CIRCLE_STEPS = 32

/** Where a press of Add socket would place a socket: on [part] at [u] and [v], the press on the body's right when [onRight]. */
class SocketPlace(val part: PlacedPart, val u: Float, val v: Float, val onRight: Boolean)

/** The place on the surface of a part of [character] showing at [pixel] that a socket would take, or null where none shows. */
fun socketPlaceAt(character: AssembledCharacter, pixel: Offset, sight: SurfaceView, handles: HandleView): SocketPlace? {
    val hit = surfaceHit(character, pixel, sight) ?: return null
    val part = character.part(hit.part) ?: return null
    val place = if (hit.patch == BODY_PATCH) SurfacePlace(hit.u, hit.v) else surfaceUnder(part, pixel, handles) ?: return null
    val at = part.pointOn(place.u, place.v) ?: return null
    return SocketPlace(part, place.u, place.v, at.z > 0f)
}

/** A socket's place and the way it faces, in body space. */
class SocketMark(val position: Vec3, val outward: Vec3)

/**
 * Where a socket placed at [place] shows, and its mirrored twin when [paired]: the twin sits across the body's middle on a part in
 * the middle of it, or on the other side of the pair, [twin], on a paired part.
 */
fun socketMarks(place: SocketPlace, outward: Float, paired: Boolean, twin: PlacedPart?): Pair<SocketMark, SocketMark?>? {
    fun mark(on: PlacedPart, v: Float): SocketMark? {
        val at = on.pointOn(place.u, v) ?: return null
        return SocketMark(at, on.outwardAt(place.u, v, outward) ?: return null)
    }
    val own = mark(place.part, place.v) ?: return null
    if (!paired) return own to null
    val other = if (twin != null) mark(twin, place.v) else if (!place.part.mirrored) mark(place.part, 1f - place.v) else null
    return own to other
}

/** The other side of [part]'s pair as placed in [character], found by [twinOf] in the document. */
fun placedTwin(character: AssembledCharacter, part: PlacedPart, twinOf: (PartId) -> PartId?): PlacedPart? = part.id?.let(twinOf)?.let(character::part)

/** A socket about to be placed: an accent ring in a thin ink one, ticked the way it faces; a [twin] faint and dashed. */
fun OverlayBuilder.socketMark(mark: SocketMark, sight: SurfaceView, look: SheetLook, twin: Boolean) {
    val at = sight.toPixel(mark.position) ?: return
    val ahead = sight.toPixel(mark.position + mark.outward * (1f / sight.pixelsPerUnitAt(mark.position).coerceAtLeast(1e-6f))) ?: return
    val direction = (ahead - at).let { if (it.getDistance() > 0f) it / it.getDistance() else Offset(0f, -1f) }
    val radius = look.pickedDotRadius
    val ink = look.ring
    if (twin) {
        val faint = ink.copy(alpha = ink.alpha * TWIN_ALPHA)
        dashed(circle(at, radius), faint, RING_STROKE - 1f, TWIN_DASH, TWIN_DASH)
        line(at + direction * radius, at + direction * (radius + TICK * 0.75f), faint, RING_STROKE - 1f)
        return
    }
    ring(at, radius + OUTER_RING, ink, THIN_STROKE)
    ring(at, radius, look.active, RING_STROKE)
    line(at + direction * (radius + OUTER_RING), at + direction * (radius + OUTER_RING + TICK), look.active, RING_STROKE)
}

private fun OverlayBuilder.ring(center: Offset, radius: Float, color: Color, width: Float) =
    circle(center, radius).zipWithNext().forEach { (a, b) -> line(a, b, color, width) }

private fun circle(center: Offset, radius: Float): List<Offset> = List(CIRCLE_STEPS + 1) { i ->
    val angle = 2.0 * PI * i / CIRCLE_STEPS
    center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
}
