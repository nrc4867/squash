package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val CAP_RING_DIVISOR = 6
private const val MIN_CAP_RINGS = 2
private const val VERTICAL_AXIS_DOT = 0.99f

/** How a run of slices ends. */
enum class EndClosure { OPEN, FLAT, ROUND, POINTED }

/** A run of slices along [axis] between [uStart] and [uEnd], before any view resizes it. */
data class TubeSpec(
    val name: String,
    val axis: AxisSpline,
    val uStart: Float,
    val uEnd: Float,
    val rings: Int,
    val segments: Int,
    val section: ClosedSpline,
    val radius: Float,
    val taper: Float = 1f,
    val startClosure: EndClosure,
    val endClosure: EndClosure,
    val jointUs: List<Float> = emptyList(),
)

/** Builds slices and the patches lofted from them. */
object SliceBuilder {
    fun tube(spec: TubeSpec): Patch {
        require(spec.section.controlPoints.size == spec.segments) {
            "${spec.name}: a section has ${spec.section.controlPoints.size} control points for ${spec.segments} segments"
        }
        val us = ringUs(spec)
        val frames = frames(spec.axis, us)
        val slices = us.indices.map { ring ->
            val along = (us[ring] - spec.uStart) / (spec.uEnd - spec.uStart)
            val taper = 1f + (spec.taper - 1f) * along
            val closure = closureScale(ring, us.size, spec.startClosure, spec.endClosure)
            val extent = 2f * spec.radius * taper * closure
            Slice(us[ring], frames[ring], extent, extent, spec.section, frames[ring].rotation)
        }
        return loft(spec.name, slices, spec.segments)
    }

    /** The grid sampled from [slices] at fixed v. */
    fun loft(name: String, slices: List<Slice>, segments: Int): Patch {
        val points = FloatArray(slices.size * segments * Patch.COMPONENTS)
        var o = 0
        for (slice in slices) {
            for (p in slice.section.sample(segments)) {
                val world = slice.frame.center +
                    slice.frame.side * (p.x * slice.width / 2f) +
                    slice.frame.up * (p.y * slice.height / 2f)
                points[o++] = world.x
                points[o++] = world.y
                points[o++] = world.z
            }
        }
        return Patch(name, slices.size, segments, closedV = true, slices = slices, points = points)
    }

    /** Evenly spaced u values with the nearest ring moved onto each joint, so a joint always lands on a ring. */
    private fun ringUs(spec: TubeSpec): List<Float> {
        val n = spec.rings
        val span = spec.uEnd - spec.uStart
        val us = FloatArray(n) { spec.uStart + span * it / (n - 1) }
        for (joint in spec.jointUs) {
            if (joint < spec.uStart || joint > spec.uEnd) continue
            val nearest = us.indices.minBy { abs(us[it] - joint) }
            us[nearest] = joint
        }
        if (spec.endClosure == EndClosure.FLAT) us[n - 2] = spec.uEnd
        if (spec.startClosure == EndClosure.FLAT) us[1] = spec.uStart
        return us.toList()
    }

    private fun frames(axis: AxisSpline, us: List<Float>): List<Frame> {
        val first = axis.tangentAt(us.first())
        val reference = if (abs(first dot Vec3.Y) > VERTICAL_AXIS_DOT) Vec3.X else Vec3.Y
        var up = (reference - first * (reference dot first)).normalized()
        return us.map { u ->
            val tangent = axis.tangentAt(u)
            up = (up - tangent * (up dot tangent)).normalized()
            Frame(axis.pointAt(u), tangent, up)
        }
    }

    private fun closureScale(ring: Int, rings: Int, start: EndClosure, end: EndClosure): Float {
        val capRings = max(MIN_CAP_RINGS, rings / CAP_RING_DIVISOR)
        return endScale(ring, capRings, start) * endScale(rings - 1 - ring, capRings, end)
    }

    /** The scale of the ring [fromEnd] rings away from an end closed by [closure]. */
    private fun endScale(fromEnd: Int, capRings: Int, closure: EndClosure): Float {
        if (fromEnd >= capRings) return 1f
        val t = 1f - fromEnd.toFloat() / capRings
        return when (closure) {
            EndClosure.OPEN -> 1f
            EndClosure.FLAT -> if (fromEnd == 0) 0f else 1f
            EndClosure.ROUND -> sqrt(1f - t * t)
            EndClosure.POINTED -> 1f - t
        }
    }
}
