package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.BoneAngles
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.angles
import dev.chieppa.squash.rig.axes
import dev.chieppa.squash.rig.boneFrames
import dev.chieppa.squash.rig.held
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.pose.TurnAxis
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.OverlayBuilder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

private const val RING_STEPS = 48
private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()

/** The rings' radius on screen, in pixels at density 1. */
private const val RING_RADIUS = 34f

/** A ring of the picked bone: the [axis] it turns about, its points through the sight, and its middle there. */
private class ShownRing(val axis: TurnAxis, val about: Vec3, val points: List<Offset>, val middle: Offset)

/**
 * Turn: a click on the character picks the bone that moves the skin there, Shift adding it to a chain; the picked bone shows a ring
 * for each of Tip, Turn and Lean, and a drag on a ring turns it within its limits, the chain in equal shares and, with Mirror on,
 * each twin the mirrored way. One undo step a drag.
 */
class TurnTool(state: AppState, look: PoseHandleLook) : PoseTool(state, look) {
    override fun takes(grip: PoseGrip): Boolean = false

    override fun shows(grip: PoseGrip): Boolean = false

    override fun gripAt(pixel: Offset, sight: SurfaceView): PoseGrip? =
        rings(sight).minByOrNull { ring -> ring.points.minOf { (it - pixel).getDistance() } }
            ?.takeIf { ring -> ring.points.minOf { (it - pixel).getDistance() } <= look.reach }
            ?.let { PoseGrip.Ring(it.axis) }

    override fun grab(grip: PoseGrip?, pixel: Offset, sight: SurfaceView, shift: Boolean): PoseDrag? {
        val session = state.session.value ?: return null
        val rig = rig ?: return null
        if (grip is PoseGrip.Ring) {
            val ring = rings(sight).firstOrNull { it.axis == grip.axis } ?: return null
            val bones = state.pose.chain.ifEmpty { listOfNotNull(state.pose.bone) }
            val facing = sign(ring.about dot sight.toViewer(rigPoint(rig, bones.last())))
            return RingDrag(session, rig, bones, grip.axis, ring.middle, pixel, if (facing == 0f) 1f else facing, mirrors())
        }
        val posed = state.posing.value?.posed?.output?.value ?: return null
        val bone = boneUnder(rig, posed, pixel, sight) ?: return null
        state.pose.pick(bone, shift)
        return Pressed
    }

    private fun mirrors(): Boolean = state.mirror.value

    private fun rigPoint(rig: Rig, bone: BoneName): Vec3 {
        val i = rig.indexOf(bone) ?: return Vec3.ZERO
        return boneFrames(rig, shownPose).transforms[i](rig.bones[i].pivot)
    }

    /** The picked bone's three rings through [sight]. */
    private fun rings(sight: SurfaceView): List<ShownRing> {
        val rig = rig ?: return emptyList()
        val picked = state.pose.bone ?: return emptyList()
        val i = rig.indexOf(picked) ?: return emptyList()
        val frames = boneFrames(rig, shownPose)
        val bone = rig.bones[i]
        val pivot = frames.transforms[i](bone.pivot)
        val middle = sight.toPixel(pivot) ?: return emptyList()
        val radius = RING_RADIUS * look.density / sight.pixelsPerUnitAt(pivot).coerceAtLeast(1e-6f)
        val own = bone.axes()
        val turn = frames.turns[i]
        return listOf(TurnAxis.TIP to own.x, TurnAxis.LEAN to own.y, TurnAxis.TURN to own.z).map { (axis, local) ->
            val about = (turn * local).normalized()
            val a = (about cross (if (kotlin.math.abs(about.y) < 0.9f) Vec3.Y else Vec3.X)).normalized()
            val b = about cross a
            val points = List(RING_STEPS + 1) { k ->
                val t = (2.0 * PI * k / RING_STEPS).toFloat()
                sight.toPixel(pivot + a * (radius * cos(t)) + b * (radius * sin(t))) ?: middle
            }
            ShownRing(axis, about, points, middle)
        }
    }

    override fun OverlayBuilder.drawn(sight: SurfaceView) {
        val lit = (state.pose.lit as? PoseGrip.Ring)?.axis
        rings(sight).forEach { ring ->
            val color = if (ring.axis == lit) look.accent else look.ring
            ring.points.zipWithNext().forEach { (a, b) -> line(a, b, color, look.width) }
        }
        val rig = rig ?: return
        val frames = boneFrames(rig, shownPose)
        state.pose.chain.ifEmpty { listOfNotNull(state.pose.bone) }.forEach { name ->
            val i = rig.indexOf(name) ?: return@forEach
            sight.toPixel(frames.transforms[i](rig.bones[i].pivot))?.let { dot(it, look.end, look.accent) }
        }
    }
}

/**
 * A drag on a ring about [axis]: the angle the pointer sweeps round the ring's [middle] since [from], turned the way the ring faces
 * the viewer, [facing], shared equally among [bones], each held to its limits; with [mirror] each twin takes the same angle.
 */
private class RingDrag(
    session: CharacterSession,
    private val rig: Rig,
    private val bones: List<BoneName>,
    private val axis: TurnAxis,
    private val middle: Offset,
    private val from: Offset,
    private val facing: Float,
    private val mirror: Boolean,
) : PoseDrag {
    private val gesture: History<SquashDocument>.Transaction = session.history.begin()
    private val character = session.document.value.character
    private val start: Pose = character.pose
    private var moved = false

    override fun move(pixel: Offset, alt: Boolean, shift: Boolean) {
        val swept = sweep(from - middle, pixel - middle)
        val share = -swept * facing * RADIANS_TO_DEGREES / bones.size
        var pose = start
        for (name in bones) {
            val twin = if (mirror) character.part(name.part)?.let(character::twinOf)?.id else null
            for (part in listOfNotNull(name.part, twin)) pose = turned(pose, BoneName(part, name.joint), share)
        }
        gesture.update(SetPose(pose))
        moved = true
    }

    private fun turned(pose: Pose, name: BoneName, by: Float): Pose {
        val bone = rig.bone(name) ?: return pose
        val set = pose.of(name.part, name.joint) ?: BonePose(name.part, name.joint)
        if (set.mode == dev.chieppa.squash.document.JointMode.LOCKED) return pose
        val was = start.of(name.part, name.joint).angles()
        val next = when (axis) {
            TurnAxis.TIP -> was.copy(tip = was.tip + by)
            TurnAxis.TURN -> was.copy(turn = was.turn + by)
            TurnAxis.LEAN -> was.copy(lean = was.lean + by)
        }
        val kept: BoneAngles = held(next, bone.limits, set.mode)
        return pose.with(set.copy(degrees = kept.tip, turn = kept.turn, lean = kept.lean))
    }

    override fun finish(keep: Boolean) {
        if (keep && moved) gesture.commit() else gesture.cancel()
    }
}

/** The angle from [a] to [b] round the origin, on screen, between -π and π. */
private fun sweep(a: Offset, b: Offset): Float {
    var d = atan2(b.y, b.x) - atan2(a.y, a.x)
    if (d > PI) d -= (2 * PI).toFloat()
    if (d < -PI) d += (2 * PI).toFloat()
    return d
}
