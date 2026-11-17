package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.block.DigitTips
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.moveSocket
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SheetPart
import dev.chieppa.squash.screens.sheet.ViewBasis

/** How far a known place on a socket's parent may show from its dot, in pixels, and still be where the dot is. */
private const val SAME_PLACE_PIXELS = 0.5f

/** Pixels per body unit of the view a nudge is worked out in, fine enough that its searches land well inside a step. */
private const val NUDGE_PIXELS_PER_UNIT = 4000f

/** How far an arrow key nudges the selected part, in body units, and with Shift held. */
const val NUDGE_STEP = 0.005f
const val NUDGE_STEP_LARGE = 0.05f

/** A view looking along [basis], [scale] pixels to a body unit, with the body's origin at its corner. */
class BasisHandleView(private val basis: ViewBasis, private val scale: Float) : HandleView {
    override fun toPixel(point: Vec3): Offset = Offset((point dot basis.screenRight) * scale, -(point dot basis.screenUp) * scale)
    override fun toViewer(point: Vec3): Vec3 = basis.back
}

/** Where on its parent this socket lies as [view] shows it: its known place where that shows at its dot, else the place found under the dot. */
fun SocketHandle.startPlace(view: HandleView): SurfacePlace? {
    val dot = view.toPixel(position) ?: return null
    val known = place?.takeIf { p -> parent.pointOn(p.u, p.v)?.let(view::toPixel)?.let { (it - dot).getDistance() <= SAME_PLACE_PIXELS } == true }
    return known ?: surfaceUnder(parent, dot, view)
}

/**
 * Where this socket goes on its parent for [target] in [view]: on the ring of [snap] at its u exactly, where it snapped to one; held
 * to a line from [start], where the drag is held to an axis, keeping a coordinate of [start] exactly wherever that line follows one;
 * else the place under [target], or under the snap.
 */
fun SocketHandle.placeFor(target: Offset, view: HandleView, snap: Snap?, held: Boolean, start: SurfacePlace?): SurfacePlace? {
    snap?.u?.let { return surfaceAlong(parent, SurfaceCoordinate.U, it, snap.pixel, view) }
    val pixel = snap?.pixel ?: target
    return if (held) surfaceAt(parent, pixel, view, start) else surfaceUnder(parent, pixel, view)
}

/** The command that puts this socket at [place] on its parent, on that side alone with [thisSide], or null for a digit's tip, which the digit's end sets. */
fun SocketHandle.moveTo(character: Character, place: SurfacePlace, thisSide: Boolean): DocumentCommand? {
    if (DigitTips.isTip(socket)) return null
    val parentId = parent.id ?: return null
    val number = HandSockets.idOf(socket) ?: return moveSocket(character, parentId, socket, side, place.u, place.v, parent.mirrored, thisSide)
    return HandSockets.move(owner ?: parentId, number, place.u, if (side == Side.RIGHT && !parent.mirrored) 1f - place.v else place.v)
}

/**
 * Nudges the selected part's socket [right] and [up] body units across [view] as its pane shows it, as one undo step, holding the
 * socket to that line the way an axis-held drag does. False where nothing moves: no selected part on a socket, or no room that way.
 */
fun AppState.nudgeSelected(view: View, right: Float, up: Float): Boolean {
    val session = session.value ?: return false
    val character = session.assembly.output.value ?: return false
    val selected = selectedPart.value ?: return false
    val part = character.part(selected) ?: return false
    val handle = moveHandles(character, null).filterIsInstance<SocketHandle>().firstOrNull { it.child == selected } ?: return false
    val shown = BasisHandleView(SheetPart(part).axes.basis(view), NUDGE_PIXELS_PER_UNIT)
    val start = handle.startPlace(shown) ?: return false
    val from = shown.toPixel(handle.position)
    val target = Offset(from.x + right * NUDGE_PIXELS_PER_UNIT, from.y - up * NUDGE_PIXELS_PER_UNIT)
    val place = handle.placeFor(target, shown, null, held = true, start = start) ?: return false
    if (place == start) return false
    val command = handle.moveTo(session.document.value.character, place, thisSide) ?: return false
    session.send(command)
    return true
}
