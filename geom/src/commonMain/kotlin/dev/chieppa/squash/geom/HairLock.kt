package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()

/** How far along a lock, as a share of its length, its root closes to a point inside what it grows from. */
private const val ROOT_CLOSE = 0.02f

/** The rings round a round tip's cap. */
private const val CAP_RINGS = 5

private const val HALF_PI = (PI / 2.0).toFloat()

/** How far past where it stops lying on the head a lock's rings ease from facing away from it to carrying on, as a share of its length. */
private const val LYING_EASE = 0.1f

/** A lock's cross-section: a flattened ribbon cupped round what it lies on, or a rounded clump. */
enum class LockProfile(internal val section: ClosedSpline) {
    RIBBON(SectionProfile.LENS.spline(12).cupped(-0.25f)),
    CLUMP(SectionProfile.EGG.spline(12)),
}

/**
 * Builds a hair lock as a swept patch of its own: [shape] carried along a guide curve through a few points, its rings' flat side facing
 * [up] at its root and carried along without twisting but by the lock's own turn. Where it lies on a head, up to [lying] of its length,
 * its flat side keeps facing away from the head's [middle], so a wide lock lies flat over the head however it bends round it; past that it
 * carries on without twisting. Its root ring closes to a point, so the root is a closed end sunk into what it grows from, and its tip
 * closes as [shape] says.
 */
object LockBuilder {
    /** The lock's patch, [rings] rings by [segments] points, along [guide] from its root (the first point) to its tip. */
    fun patch(
        name: String, guide: List<Vec3>, up: Vec3, shape: ClumpShape, profile: LockProfile, rings: Int, segments: Int,
        middle: Vec3? = null, lying: Float = 0f,
    ): Patch {
        require(guide.size >= 2) { "A lock's guide needs a root and a tip" }
        val axis = AxisSpline(guide)
        val built = lofted(name, axis, up, shape, profile, rings, segments, middle, lying)
        val points = built.points.copyOf()
        val us = FloatArray(built.nu) { built.ringUs[it] }
        val root = guide.first()
        for (column in 0 until built.nv) {
            val i = column * Patch.COMPONENTS
            points[i] = root.x
            points[i + 1] = root.y
            points[i + 2] = root.z
        }
        us[0] = 0f
        if (built.nu > 1) us[1] = maxOf(us[1], ROOT_CLOSE)
        return Patch(name, built.nu, built.nv, closedV = true, slices = null, points = points, proxyNormals = built.proxyNormals, us = us)
    }

    /**
     * The lock's rings along [axis], each its shape's width and thickness there, turned and swayed as the shape says, and lofted; a forked
     * tip's points cut back between its lobes. Unlike a clump on a part, a lock's rings keep their size through a tight curl, so a curled
     * lock stays a full roll.
     */
    private fun lofted(
        name: String, axis: AxisSpline, up: Vec3, shape: ClumpShape, profile: LockProfile, rings: Int, segments: Int, middle: Vec3?, lying: Float,
    ): Patch {
        val us = ringUs(shape, rings)
        val frames = SliceBuilder.frames(axis, us, up).let { if (middle == null) it else lyingFlat(it, us, middle, lying) }
        val slices = us.mapIndexed { ring, t ->
            val base = frames[ring]
            val turn = shape.turnAt(t)
            val turnedUp = if (turn == 0f) base.up else (base.up * cos(turn) + base.side * sin(turn)).normalized()
            val turned = Frame(base.center, base.tangent, turnedUp)
            val sway = shape.swayAt(t)
            val frame = if (sway == 0f) turned else Frame(base.center + turned.side * sway, base.tangent, turnedUp)
            Slice(t, frame, shape.thicknessAt(t), shape.widthAt(t), profile.section, frame.rotation)
        }
        val patch = SliceBuilder.loft(name, slices, segments)
        if (shape.points < 2 || shape.notch <= 0f) return patch
        val length = slices.zipWithNext { a, b -> (b.frame.center - a.frame.center).length }.sum()
        val across = profile.section.sample(segments).map { it.x }
        val points = patch.points.copyOf()
        for ((ring, slice) in slices.withIndex()) {
            if (shape.intoTip(slice.u) <= 0f) continue
            val tangent = slice.frame.tangent
            for (column in 0 until segments) {
                val back = shape.cutBack(slice.u, across[column]) * length * shape.lobedEnd(slice.u)
                if (back == 0f) continue
                val i = (ring * segments + column) * Patch.COMPONENTS
                points[i] -= tangent.x * back
                points[i + 1] -= tangent.y * back
                points[i + 2] -= tangent.z * back
            }
        }
        return patch.withPoints(points, patch.proxyNormals)
    }

    /**
     * [frames] at [us] turned about their tangents so their up faces away from [middle] up to [lying], easing off over [LYING_EASE] past
     * it, each frame past that keeping the last turn, so the rings never jump.
     */
    private fun lyingFlat(frames: List<Frame>, us: List<Float>, middle: Vec3, lying: Float): List<Frame> {
        var held = 0f
        return frames.mapIndexed { ring, frame ->
            val away = (frame.center - middle).let { it - frame.tangent * (it dot frame.tangent) }
            val weight = (1f - (us[ring] - lying) / LYING_EASE).coerceIn(0f, 1f)
            if (away.length > 1e-6f && weight > 0f) {
                val target = away.normalized()
                val angle = atan2((frame.up cross target) dot frame.tangent, frame.up dot target)
                held = weight * angle + (1f - weight) * held
            }
            if (held == 0f) frame else Frame(frame.center, frame.tangent, (frame.up * cos(held) + (frame.tangent cross frame.up) * sin(held)).normalized())
        }
    }

    /**
     * Where along the lock its [rings] rings lie: evenly, but for a round tip, whose last rings step evenly round its cap by angle, so
     * the cap closes as a smooth dome rather than a flat end.
     */
    private fun ringUs(shape: ClumpShape, rings: Int): List<Float> {
        if (shape.tipRound <= 0f || shape.points > 1 || rings < CAP_RINGS + 3) return List(rings) { it.toFloat() / (rings - 1) }
        val capStart = 1f - shape.tipReach
        val body = rings - CAP_RINGS
        return List(body) { capStart * it / body } + List(CAP_RINGS) { k ->
            capStart + shape.tipReach * sin((k + 1).toFloat() / CAP_RINGS * HALF_PI)
        }
    }

    /** The length of the curve through [guide], read at [steps] places. */
    fun lengthOf(guide: List<Vec3>, steps: Int = 32): Float {
        if (guide.size < 2) return 0f
        val axis = AxisSpline(guide)
        var total = 0f
        var last = axis.pointAt(0f)
        for (i in 1..steps) {
            val here = axis.pointAt(i.toFloat() / steps)
            total += (here - last).length
            last = here
        }
        return total
    }
}

/** Builds a band tied round a gathered lock: a ring of [radius] round [axis] through [center], [thickness] through. */
object BandBuilder {
    fun patch(name: String, center: Vec3, axis: Vec3, radius: Float, thickness: Float, rings: Int = 20, segments: Int = 8): Patch {
        val along = axis.normalized()
        val first = (if (kotlin.math.abs(along.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3.X).let { (it - along * (it dot along)).normalized() }
        val second = along cross first
        val points = FloatArray((rings + 1) * segments * Patch.COMPONENTS)
        var o = 0
        for (ring in 0..rings) {
            val a = TWO_PI * ring / rings
            val out = first * cos(a) + second * sin(a)
            val middle = center + out * radius
            for (s in 0 until segments) {
                val b = TWO_PI * s / segments
                val p = middle + (out * cos(b) + along * sin(b)) * (thickness / 2f)
                points[o++] = p.x
                points[o++] = p.y
                points[o++] = p.z
            }
        }
        return Patch(name, rings + 1, segments, closedV = true, slices = null, points = points, us = FloatArray(rings + 1) { it.toFloat() / rings })
    }
}
