package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs

/** How much of the pointer's movement a fine drag follows. */
const val FINE_DRAG_SPEED = 0.2f

/** How far, in pixels, a drag with its axis held to the pointer moves before it chooses the pane's axis it follows. */
const val AXIS_CHOICE_PIXELS = 6f

/** A direction's part along a pane axis smaller than this is no part at all, so a held axis keeps the other one exactly. */
private const val AXIS_NOISE = 1e-5f

/** A body axis shorter than this share of a unit on screen points at the viewer, and a drag cannot follow it. */
private const val FACING_AXIS = 0.05f

/** One of the character's body axes: X forward, Y up and Z across, from left to right. */
enum class BodyAxis(val direction: Vec3) {
    X(Vec3.X),
    Y(Vec3.Y),
    Z(Vec3.Z),
}

/** The keys held during a drag that steer it: [axis] holds it to one of the pane's axes, [fine] slows it, [free] leaves out the snaps. */
data class DragKeys(val axis: Boolean = false, val fine: Boolean = false, val free: Boolean = false)

/**
 * Where a handle dragged from [start] should go as the pointer moves, as a pixel in its pane. A fine drag follows the pointer's
 * movement at [FINE_DRAG_SPEED], so pressing and letting go of the fine key mid-drag never makes it jump. A drag held to an axis
 * moves along a line through [start] only: the axis a key named, or with [DragKeys.axis] held the pane's axis the drag has gone
 * furthest along, chosen once it has gone [AXIS_CHOICE_PIXELS]. Along a pane's axis the other pixel coordinate stays [start]'s
 * exactly.
 */
class DragAim(private val start: Offset, pointer: Offset = start) {
    private var last = pointer
    private var travelled = Offset.Zero

    /** The pane's axis [DragKeys.axis] holds the drag to, once chosen. */
    private var chosen: Offset? = null

    /** The direction on screen the drag is held to, or null while it is free. */
    var direction: Offset? = null
        private set

    /** Where the handle goes for the pointer at [pointer] with [keys] held and [keyed], a named axis's direction on screen, if any. */
    fun follow(pointer: Offset, keys: DragKeys, keyed: Offset? = null): Offset {
        val moved = pointer - last
        last = pointer
        travelled += if (keys.fine) moved * FINE_DRAG_SPEED else moved
        if (!keys.axis) chosen = null
        if (keys.axis && chosen == null && travelled.getDistance() >= AXIS_CHOICE_PIXELS) {
            chosen = if (abs(travelled.x) >= abs(travelled.y)) Offset(1f, 0f) else Offset(0f, 1f)
        }
        val held = keyed ?: chosen
        direction = held
        if (held == null) return if (keys.axis) start else start + travelled
        val along = travelled.x * held.x + travelled.y * held.y
        return Offset(start.x + held.x * along, start.y + held.y * along)
    }

    companion object {
        /**
         * The direction on screen [axis] runs in [view] through [at], made exact along a pane's axis, or null where it points at
         * the viewer.
         */
        fun directionOf(axis: BodyAxis, at: Vec3, view: HandleView): Offset? {
            val from = view.toPixel(at) ?: return null
            val to = view.toPixel(at + axis.direction) ?: return null
            val unit = view.toPixel(at + view.toViewer(at).let { if (it.length > 0f) acrossOf(it) else Vec3.X }) ?: return null
            val run = to - from
            val scale = (unit - from).getDistance()
            if (scale == 0f || run.getDistance() < FACING_AXIS * scale) return null
            val direction = run / run.getDistance()
            return Offset(clean(direction.x), clean(direction.y)).let { it / it.getDistance() }
        }

        /** A unit direction square to [toViewer], which shows at full length on screen. */
        private fun acrossOf(toViewer: Vec3): Vec3 {
            val other = if (abs(toViewer.normalized() dot Vec3.Y) < 0.9f) Vec3.Y else Vec3.X
            return (toViewer cross other).normalized()
        }

        private fun clean(component: Float) = if (abs(component) < AXIS_NOISE) 0f else component
    }
}
