package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera

private const val SEARCH_STEPS = 48
private const val REFINE_STEPS = 8
private const val REFINE_ROUNDS = 3
private const val JOINT_SAMPLES = 128

/** How close to each other, in u, two joints may come, and how far from a part's ends one may go. */
private const val JOINT_GAP = 0.05f

/** Handles this many pixels apart count as one spot on screen, where the one nearer the viewer is on top. */
private const val SAME_SPOT = 3f

/** How a view shows body space: where a point lands in its pixels, or null behind the camera, and which way the viewer lies from it. */
interface HandleView {
    fun toPixel(point: Vec3): Offset?
    fun toViewer(point: Vec3): Vec3
}

/** A locked pane as a [HandleView]. */
internal class LockedHandleView(private val camera: LockedCamera) : HandleView {
    override fun toPixel(point: Vec3): Offset = camera.toPixel(point)
    override fun toViewer(point: Vec3): Vec3 = camera.basis.back
}

/** Full's free camera as a [HandleView]. */
internal class FullHandleView(private val view: FullView) : HandleView {
    override fun toPixel(point: Vec3): Offset? = view.toPixel(point)
    override fun toViewer(point: Vec3): Vec3 = view.toViewer(point)
}

/** Something the Move tool drags. */
sealed interface MoveHandle {
    val position: Vec3
}

/**
 * Socket [socket] of part [owner], lying on [parent], which is the owner or its twin, as the part on [side] sits on it. [child] is
 * that part, or null while the socket is empty.
 */
class SocketHandle(
    val parent: PlacedPart,
    val socket: String,
    val side: Side?,
    val child: PartId?,
    override val position: Vec3,
    val owner: PartId? = parent.id,
) : MoveHandle

/** Joint [name] of [part], at [u], free to move within [range]. */
class JointHandle(
    val part: PlacedPart,
    val name: String,
    val u: Float,
    val range: ClosedFloatingPointRange<Float>,
    override val position: Vec3,
) : MoveHandle

/** Every socket of [character], with a part on it or not, and the joints of [selected] that can move. */
fun moveHandles(character: AssembledCharacter, selected: PartId?): List<MoveHandle> {
    val placed = character.parts.mapNotNull { part ->
        val socket = part.socket ?: return@mapNotNull null
        val parent = character.part(socket.parent) ?: return@mapNotNull null
        val at = part.anchor ?: return@mapNotNull null
        SocketHandle(parent, socket.socket, part.side, part.id, at)
    }
    val open = character.sockets.mapNotNull { socket ->
        val parent = character.part(socket.on) ?: return@mapNotNull null
        SocketHandle(parent, socket.ref.socket, socket.side, null, socket.position, socket.ref.parent)
    }
    return placed + open + selected?.let(character::part)?.let(::jointHandles).orEmpty()
}

/** A part's joints between its ends: one on an end stays on it, since the socket or the next part sets where that is. */
private fun jointHandles(part: PlacedPart): List<JointHandle> {
    val joints = part.template.blocks.flatMap { it.joints() }.sortedBy { it.u }
    return joints.mapIndexedNotNull { i, joint ->
        if (joint.u <= 0f || joint.u >= 1f) return@mapIndexedNotNull null
        val low = (joints.getOrNull(i - 1)?.u ?: 0f) + JOINT_GAP
        val high = (joints.getOrNull(i + 1)?.u ?: 1f) - JOINT_GAP
        val at = axisPoint(part, joint.u) ?: return@mapIndexedNotNull null
        JointHandle(part, joint.name, joint.u, low..high.coerceAtLeast(low), at)
    }
}

/** The middle of [part] at [u], between its surface's top and underside. */
private fun axisPoint(part: PlacedPart, u: Float): Vec3? {
    val top = part.pointOn(u, 0f) ?: return null
    val under = part.pointOn(u, 0.5f) ?: return null
    return (top + under) * 0.5f
}

/** The handle under [pixel], within [reach] pixels: the nearest, and of those on one spot the one nearest the viewer. */
fun handleAt(handles: List<MoveHandle>, pixel: Offset, reach: Float, view: HandleView): MoveHandle? {
    val near = handles.mapNotNull { handle ->
        val distance = (view.toPixel(handle.position) ?: return@mapNotNull null).minus(pixel).getDistance()
        if (distance <= reach) handle to distance else null
    }
    val closest = near.minOfOrNull { it.second } ?: return null
    return near.filter { it.second <= closest + SAME_SPOT }.maxBy { (handle, _) -> handle.position dot view.toViewer(handle.position) }.first
}

/** Where along [handle]'s part its joint comes closest to [pixel]. */
fun jointUnder(handle: JointHandle, pixel: Offset, view: HandleView): Float {
    val range = handle.range
    val samples = (0..JOINT_SAMPLES).map { range.start + (range.endInclusive - range.start) * it / JOINT_SAMPLES }
    return samples.minByOrNull { u ->
        val at = axisPoint(handle.part, u)?.let(view::toPixel)
        if (at == null) Float.POSITIVE_INFINITY else (at - pixel).getDistance()
    } ?: handle.u
}

/** A place on a part's surface. */
data class SurfacePlace(val u: Float, val v: Float)

/** The place on [part]'s surface facing the viewer that shows closest to [pixel], or null when none faces the viewer. */
fun surfaceUnder(part: PlacedPart, pixel: Offset, view: HandleView): SurfacePlace? {
    fun distanceAt(u: Float, v: Float): Float? {
        val point = part.pointOn(u, v) ?: return null
        val normal = part.normalAt(u, v) ?: return null
        if ((normal dot view.toViewer(point)) < 0f) return null
        return (view.toPixel(point) ?: return null).minus(pixel).getDistance()
    }
    var best: SurfacePlace? = null
    var bestDistance = Float.POSITIVE_INFINITY
    fun consider(u: Float, v: Float) {
        val distance = distanceAt(u, v) ?: return
        if (distance < bestDistance) {
            bestDistance = distance
            best = SurfacePlace(u, v)
        }
    }
    for (i in 0..SEARCH_STEPS) for (j in 0 until SEARCH_STEPS) consider(i.toFloat() / SEARCH_STEPS, j.toFloat() / SEARCH_STEPS)
    var step = 1f / SEARCH_STEPS
    repeat(REFINE_ROUNDS) {
        val centre = best ?: return null
        for (i in -REFINE_STEPS..REFINE_STEPS) {
            for (j in -REFINE_STEPS..REFINE_STEPS) {
                val u = (centre.u + step * i / REFINE_STEPS).coerceIn(0f, 1f)
                val v = centre.v + step * j / REFINE_STEPS
                consider(u, v - floor(v))
            }
        }
        step /= REFINE_STEPS / 2f
    }
    return best
}
