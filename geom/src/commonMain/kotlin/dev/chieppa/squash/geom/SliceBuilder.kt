package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val CAP_RING_DIVISOR = 6
private const val MIN_CAP_RINGS = 2

/** How a run of slices ends. */
enum class EndClosure { OPEN, FLAT, ROUND, POINTED }

/**
 * A run of slices along [axis] between [uStart] and [uEnd]. Without [views], [radius], [taper], [widthRatio] and [drop]
 * size them; with [views], the drawing does. [roundRings] sets how far a round end reaches when nothing is drawn.
 * A ring on an open end faces [socketNormal].
 */
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
    val widthRatio: Float = 1f,
    val roundRings: Int? = null,
    val drop: Float = 0f,
    val socketNormal: Vec3? = null,
    val views: ViewSizing? = null,
    val front: FrontSection? = null,
)

/** A run given ring by ring, for a run that does not follow the part's axis: full extents and centre at each u. */
class SweepSpec(
    val name: String,
    val us: FloatArray,
    val centres: List<Vec3>,
    val heights: FloatArray,
    val widths: FloatArray,
    val section: ClosedSpline,
    val up: Vec3,
    val startClosure: EndClosure,
    val endClosure: EndClosure,
)

/** Builds slices and the patches lofted from them. */
object SliceBuilder {
    fun tube(spec: TubeSpec): Patch {
        require(spec.section.controlPoints.size == spec.segments) {
            "${spec.name}: a section has ${spec.section.controlPoints.size} control points for ${spec.segments} segments"
        }
        val us = ringUs(spec)
        val slices = spec.views?.let { drawnSlices(spec, it, us) } ?: restSlices(spec, us)
        return loft(spec.name, slices, spec.segments)
    }

    private fun sectionAt(spec: TubeSpec, u: Float) = spec.front?.at(u, spec.section) ?: spec.section

    /** A drawing closes the ends of the part itself, so only their last ring closes; a run's inner end still rounds off. */
    private fun drawnSlices(spec: TubeSpec, views: ViewSizing, us: List<Float>): List<Slice> {
        val capRings = defaultCapRings(us.size)
        fun scale(fromEnd: Int, reachesPartEnd: Boolean, closure: EndClosure) =
            if (reachesPartEnd) drawnEndScale(fromEnd, closure) else endScale(fromEnd, capRings, closure)
        return us.mapIndexed { ring, u ->
            val closure = scale(ring, spec.uStart <= 0f, spec.startClosure) *
                scale(us.size - 1 - ring, spec.uEnd >= 1f, spec.endClosure)
            views.slice(u, sectionAt(spec, u), closure)
        }
    }

    private fun restSlices(spec: TubeSpec, us: List<Float>): List<Slice> {
        val frames = frames(spec.axis, us, null)
        val capRings = spec.roundRings ?: defaultCapRings(us.size)
        return us.indices.map { ring ->
            val along = (us[ring] - spec.uStart) / (spec.uEnd - spec.uStart)
            val taper = 1f + (spec.taper - 1f) * along
            val closure = endScale(ring, capRings, spec.startClosure) * endScale(us.size - 1 - ring, capRings, spec.endClosure)
            val extent = 2f * spec.radius * taper * closure
            val onSocket = (ring == 0 && spec.startClosure == EndClosure.OPEN) ||
                (ring == us.lastIndex && spec.endClosure == EndClosure.OPEN)
            val frame = placed(frames[ring], spec.drop, spec.socketNormal.takeIf { onSocket })
            Slice(us[ring], frame, extent, extent * spec.widthRatio, sectionAt(spec, us[ring]), frame.rotation)
        }
    }

    private fun placed(frame: Frame, drop: Float, facing: Vec3?): Frame {
        val center = frame.center - frame.up * drop
        if (facing == null) return Frame(center, frame.tangent, frame.up)
        return Frame(center, facing, (frame.up - facing * (frame.up dot facing)).normalized())
    }

    /** A run from its rings' centres, through a spline over them. */
    fun sweep(spec: SweepSpec): Patch {
        val first = spec.us.first()
        val range = spec.us.last() - first
        val along = FloatArray(spec.us.size) { (spec.us[it] - first) / range }
        val frames = frames(AxisSpline(spec.centres, along), along.asList(), spec.up)
        val capRings = defaultCapRings(spec.us.size)
        val slices = spec.us.indices.map { ring ->
            val closure = endScale(ring, capRings, spec.startClosure) * endScale(spec.us.size - 1 - ring, capRings, spec.endClosure)
            val frame = frames[ring]
            Slice(spec.us[ring], frame, spec.heights[ring] * closure, spec.widths[ring] * closure, spec.section, frame.rotation)
        }
        return loft(spec.name, slices, spec.section.controlPoints.size)
    }

    /** The grid sampled from [slices] at fixed v. */
    fun loft(name: String, slices: List<Slice>, segments: Int): Patch {
        val points = FloatArray(slices.size * segments * Patch.COMPONENTS)
        var o = 0
        var sampledSection: ClosedSpline? = null
        var samples = emptyList<Vec2>()
        for (slice in slices) {
            if (slice.section !== sampledSection) {
                sampledSection = slice.section
                samples = slice.section.sample(segments)
            }
            val frame = slice.frame
            val side = frame.side
            for (p in samples) {
                val across = p.x * slice.width / 2f
                val rise = p.y * slice.height / 2f
                points[o++] = frame.center.x + side.x * across + frame.up.x * rise
                points[o++] = frame.center.y + side.y * across + frame.up.y * rise
                points[o++] = frame.center.z + side.z * across + frame.up.z * rise
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

    private fun frames(axis: AxisSpline, us: List<Float>, up: Vec3?): List<Frame> {
        var current = up ?: PartAxes.of(axis.tangentAt(us.first())).up
        return us.map { u ->
            val tangent = axis.tangentAt(u)
            current = (current - tangent * (current dot tangent)).normalized()
            Frame(axis.pointAt(u), tangent, current)
        }
    }

    private fun defaultCapRings(rings: Int): Int = max(MIN_CAP_RINGS, rings / CAP_RING_DIVISOR)

    /** The scale of the ring [fromEnd] rings from an end the drawing shapes: only a closed end's last ring closes. */
    private fun drawnEndScale(fromEnd: Int, closure: EndClosure): Float =
        if (closure != EndClosure.OPEN && fromEnd == 0) 0f else 1f

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
