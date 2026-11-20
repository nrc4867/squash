package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val CAP_RING_DIVISOR = 6
private const val MIN_CAP_RINGS = 2

/** The share of the axis's bend radius an undrawn ring reaches at most, so the rings of a tight curl never fold into each other. */
private const val BEND_HOLD = 0.85f

/** How many rings either way a ring's hold to the bend is spread over. */
private const val HOLD_SPREAD = 3

/** A turn between neighbouring rings below which the axis counts as straight there, in radians. */
private const val MIN_TURN = 1e-4f

/** How large a ring is, as a share of the drawing, at the deep end of the stretch of a run that lies inside another. */
private const val COVERED_SCALE = 0.8f

/** How a run of slices ends. */
enum class EndClosure { OPEN, FLAT, ROUND, POINTED }

/**
 * A run of slices along [axis] between [uStart] and [uEnd]. Without [views], [radius], [taper], [widthRatio] and [drop]
 * size them; with [views], the drawing does. [roundRings] sets how far a round end reaches when nothing is drawn.
 * A ring on an open end faces [socketNormal]. [startHandover] and [endHandover] say another run of the part covers that end, and
 * at what u the surface passes from one run to the other, which is how a drawing makes the two read as one surface.
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
    val startHandover: Float? = null,
    val endHandover: Float? = null,
    /** Where an undrawn run's rings take their up from, or null for what the axis's own direction gives. */
    val up: Vec3? = null,
    /** A stretch of u holding more than its share of the rings, or null for rings spaced evenly. */
    val focus: RingFocus? = null,
    /** A second section [section] turns into over a stretch of u, or null for one section the whole run. */
    val along: SectionAlong? = null,
    /**
     * Whether a drawn run follows [axis], each ring square to it at its u and sized by [views], so the drawing gives the width along the
     * length and a curved axis the path; otherwise a drawn run's rings stand in the planes along its rest the drawing sets.
     */
    val followsAxis: Boolean = false,
)

/**
 * A section [other] that a run's own section turns into, control point by control point, over the stretch of u from [from] to [to]:
 * eased in over [ease] of the run from [from] and out over the same before [to], [other] whole between.
 */
data class SectionAlong(val other: ClosedSpline, val from: Float, val to: Float, val ease: Float) {
    /** How far toward [other] the section at [u] lies, 0 to 1. */
    fun weight(u: Float): Float {
        if (u <= from || u >= to) return 0f
        val inner = minOf(ease, (to - from) / 2f).coerceAtLeast(1e-4f)
        return smooth(((u - from) / inner).coerceAtMost(1f)) * smooth(((to - u) / inner).coerceAtMost(1f))
    }

    /** The section at [u] over [base]. */
    fun at(u: Float, base: ClosedSpline): ClosedSpline {
        val w = weight(u)
        return if (w <= 0f) base else base.lerp(other, w)
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
}

/** The stretch of u from [from] to [to] holding [share] of a run's ring spacings, the rest spaced evenly outside it. */
data class RingFocus(val from: Float, val to: Float, val share: Float) {
    /** Where the spacing [t] of the way through the rings lies, 0 to 1 in u. */
    fun at(t: Float): Float {
        val inside = (to - from).coerceIn(0f, 1f)
        val outside = 1f - inside
        if (inside <= 0f || outside <= 0f) return t
        val before = from * (1f - share) / outside
        val after = before + share
        return when {
            t <= before -> if (before <= 0f) from else t / before * from
            t <= after -> from + (t - before) / share * inside
            else -> to + (t - after) / (1f - after) * (1f - to)
        }
    }
}

/**
 * A run given ring by ring, for a run that does not follow the part's axis: full extents and centre at each u. Its rings
 * face [facing], or along a spline over their centres when that is null.
 */
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
    val facing: Vec3? = null,
)

/** Builds slices and the patches lofted from them. */
object SliceBuilder {
    fun tube(spec: TubeSpec): Patch {
        require(spec.section.controlPoints.size == spec.segments) {
            "${spec.name}: a section has ${spec.section.controlPoints.size} control points for ${spec.segments} segments"
        }
        val us = ringUs(spec)
        val views = spec.views
        if (views != null && spec.followsAxis) return followingSlices(spec, views, us).let { loft(spec.name, it, spec.segments) }
        val slices = views?.let { drawnSlices(spec, it, us) } ?: restSlices(spec, us)
        return loft(spec.name, slices, spec.segments, views?.let { shadedSlices(spec, it, us, slices) } ?: slices)
    }

    /**
     * The least share of its own size any ring of [spec] keeps where its axis bends tighter than it is wide (1 where none narrows), so a
     * caller can say where a curve is clipped.
     */
    fun bendHold(spec: TubeSpec): Float {
        val us = ringUs(spec)
        val views = spec.views
        val frames = frames(spec.axis, us, spec.up)
        val extents = if (views != null && spec.followsAxis) {
            towardBends(frames, us.indices.map { ring -> views.sliceIn(us[ring], sectionAt(spec, us[ring]), 1f, frames[ring], spec.widthRatio) })
        } else if (views == null) {
            restExtents(spec, us).also { extents -> for (i in extents.indices) extents[i] *= maxOf(1f, spec.widthRatio) }
        } else {
            return 1f
        }
        val held = heldToBends(extents, bendRadii(frames), 1f)
        return extents.indices.filter { extents[it] > 0f }.minOfOrNull { held[it] / extents[it] }?.coerceAtMost(1f) ?: 1f
    }

    /** The ring [spec] stands at [u] before either end closes, with no grid around it. */
    fun ringAt(spec: TubeSpec, u: Float): Slice {
        val section = sectionAt(spec, u)
        spec.views?.let { views ->
            if (!spec.followsAxis) return views.slice(u, section, 1f, spec.widthRatio)
            return views.sliceIn(u, section, 1f, frames(spec.axis, ringUs(spec).filter { it < u } + u, spec.up).last(), spec.widthRatio)
        }
        val along = (u - spec.uStart) / (spec.uEnd - spec.uStart)
        val extent = 2f * spec.radius * (1f + (spec.taper - 1f) * along)
        val tangent = spec.axis.tangentAt(u)
        val start = spec.up ?: PartAxes.of(spec.axis.tangentAt(spec.uStart)).up
        val frame = placed(Frame(spec.axis.pointAt(u), tangent, (start - tangent * (start dot tangent)).normalized()), spec.drop, null)
        return Slice(u, frame, extent, extent * spec.widthRatio, section, frame.rotation)
    }

    private fun sectionAt(spec: TubeSpec, u: Float): ClosedSpline {
        val own = spec.along?.at(u, spec.section) ?: spec.section
        return spec.front?.at(u, own) ?: own
    }

    /**
     * A drawing closes the ends of the part itself, so only their last ring closes. Where one run hands over to another, the
     * later rises from inside to the drawing's size at the handover and the earlier sinks inside right after it, so the two cross
     * once, on one line, and neither fights the other across the overlap. A run's inner end that nothing covers still rounds off.
     */
    private fun drawnSlices(spec: TubeSpec, views: ViewSizing, us: List<Float>): List<Slice> {
        val capRings = defaultCapRings(us.size)
        fun scale(fromEnd: Int, reachesPartEnd: Boolean, closure: EndClosure) =
            if (reachesPartEnd) drawnEndScale(fromEnd, closure) else endScale(fromEnd, capRings, closure)
        val startScale = { ring: Int ->
            val handover = spec.startHandover
            if (handover != null) risingScale(ring, us[ring], spec.uStart, handover) else scale(ring, spec.uStart <= 0f, spec.startClosure)
        }
        val endScale = { ring: Int ->
            val handover = spec.endHandover
            val fromEnd = us.size - 1 - ring
            if (handover != null) sinkingScale(fromEnd, ring > 0 && us[ring - 1] >= handover) else scale(fromEnd, spec.uEnd >= 1f, spec.endClosure)
        }
        return us.mapIndexed { ring, u -> views.slice(u, sectionAt(spec, u), startScale(ring) * endScale(ring), spec.widthRatio) }
    }

    /**
     * A drawn run laid along its axis: each ring square to the axis, carried along it without twisting from its first ring's up, sized
     * by the drawing at its u and closed at its ends as [drawnSlices] closes them, then held within the axis's bend as undrawn rings are.
     */
    private fun followingSlices(spec: TubeSpec, views: ViewSizing, us: List<Float>): List<Slice> {
        val frames = frames(spec.axis, us, spec.up)
        val scales = drawnScales(spec, us)
        val sized = us.mapIndexed { ring, u ->
            val onSocket = (ring == 0 && spec.startClosure == EndClosure.OPEN) || (ring == us.lastIndex && spec.endClosure == EndClosure.OPEN)
            views.sliceIn(u, sectionAt(spec, u), scales[ring], placed(frames[ring], 0f, spec.socketNormal.takeIf { onSocket }), spec.widthRatio)
        }
        val extents = towardBends(frames, sized)
        val held = heldToBends(extents, bendRadii(frames), 1f)
        return sized.mapIndexed { ring, slice ->
            val share = if (extents[ring] > 0f) held[ring] / extents[ring] else 1f
            if (share >= 1f) slice else Slice(slice.u, slice.frame, slice.height * share, slice.width * share, slice.section, slice.frame.rotation)
        }
    }

    /**
     * How far across each of [slices] reaches toward the inside of the axis's bend there, in full: its height and width taken along the
     * way the axis turns, so a flat ribbon bending across its thin side keeps its width. A straight stretch takes the larger.
     */
    private fun towardBends(frames: List<Frame>, slices: List<Slice>): FloatArray = FloatArray(slices.size) { i ->
        val turn = frames[minOf(i + 1, frames.size - 1)].tangent - frames[maxOf(i - 1, 0)].tangent
        val slice = slices[i]
        if (turn.length < MIN_TURN) return@FloatArray maxOf(slice.height, slice.width)
        val toward = turn.normalized()
        val up = (toward dot slice.frame.up) * slice.height
        val side = (toward dot slice.frame.side) * slice.width
        sqrt(up * up + side * side)
    }

    /** How large each ring of a drawn run is at its ends, as [drawnSlices] closes them. */
    private fun drawnScales(spec: TubeSpec, us: List<Float>): FloatArray {
        val capRings = defaultCapRings(us.size)
        fun scale(fromEnd: Int, reachesPartEnd: Boolean, closure: EndClosure) =
            if (reachesPartEnd) drawnEndScale(fromEnd, closure) else endScale(fromEnd, capRings, closure)
        return FloatArray(us.size) { ring ->
            val fromEnd = us.size - 1 - ring
            val start = spec.startHandover?.let { risingScale(ring, us[ring], spec.uStart, it) } ?: scale(ring, spec.uStart <= 0f, spec.startClosure)
            val end = spec.endHandover?.let { sinkingScale(fromEnd, ring > 0 && us[ring - 1] >= it) } ?: scale(fromEnd, spec.uEnd >= 1f, spec.endClosure)
            start * end
        }
    }

    /** The rings a run's shading reads: the drawing's own wherever another run covers the end, so hiding a stretch inside that run does not turn the shading. */
    private fun shadedSlices(spec: TubeSpec, views: ViewSizing, us: List<Float>, slices: List<Slice>): List<Slice> {
        val start = spec.startHandover
        val end = spec.endHandover
        if (start == null && end == null) return slices
        return us.mapIndexed { ring, u ->
            if ((start != null && u < start) || (end != null && u >= end)) views.slice(u, sectionAt(spec, u), 1f, spec.widthRatio) else slices[ring]
        }
    }

    private fun restSlices(spec: TubeSpec, us: List<Float>): List<Slice> {
        val frames = frames(spec.axis, us, spec.up)
        val extents = restExtents(spec, us)
        val held = heldToBends(extents, bendRadii(frames), maxOf(1f, spec.widthRatio))
        return us.indices.map { ring ->
            val extent = held[ring]
            val onSocket = (ring == 0 && spec.startClosure == EndClosure.OPEN) ||
                (ring == us.lastIndex && spec.endClosure == EndClosure.OPEN)
            val frame = placed(frames[ring], spec.drop, spec.socketNormal.takeIf { onSocket })
            Slice(us[ring], frame, extent, extent * spec.widthRatio, sectionAt(spec, us[ring]), frame.rotation)
        }
    }

    /** The full height of each undrawn ring of [spec], tapered and closed at its ends, before the axis's bend holds it. */
    private fun restExtents(spec: TubeSpec, us: List<Float>): FloatArray {
        val capRings = spec.roundRings ?: defaultCapRings(us.size)
        return FloatArray(us.size) { ring ->
            val along = (us[ring] - spec.uStart) / (spec.uEnd - spec.uStart)
            val taper = 1f + (spec.taper - 1f) * along
            val closure = endScale(ring, capRings, spec.startClosure) * endScale(us.size - 1 - ring, capRings, spec.endClosure)
            2f * spec.radius * taper * closure
        }
    }

    /**
     * [extents] held within [BEND_HOLD] of the axis's bend radius at each ring, [reach] the widest of a ring's sides to its height: each
     * ring's share of its own extent the hold leaves is taken as the least over its neighbours [HOLD_SPREAD] rings either way and then
     * averaged over the same, so a tight curl narrows the run smoothly there and nowhere else.
     */
    internal fun heldToBends(extents: FloatArray, bends: FloatArray, reach: Float): FloatArray {
        val n = extents.size
        val shares = FloatArray(n) { i -> if (extents[i] <= 0f) 1f else minOf(1f, 2f * BEND_HOLD * bends[i] / reach / extents[i]) }
        if (shares.all { it >= 1f }) return extents
        val least = FloatArray(n) { i -> (maxOf(0, i - HOLD_SPREAD)..minOf(n - 1, i + HOLD_SPREAD)).minOf { shares[it] } }
        return FloatArray(n) { i ->
            val window = maxOf(0, i - HOLD_SPREAD)..minOf(n - 1, i + HOLD_SPREAD)
            extents[i] * minOf(window.sumOf { least[it].toDouble() }.toFloat() / (window.last - window.first + 1), 1f)
        }
    }

    /**
     * How tightly the axis bends at each ring: the radius of the circle through its turn from the ring before to the ring after. Two
     * neighbouring rings' planes meet that far from the axis on the inside of the bend, so a ring reaching past it folds into its
     * neighbour; a straight stretch is unbounded.
     */
    internal fun bendRadii(frames: List<Frame>): FloatArray = FloatArray(frames.size) { i ->
        val before = frames[maxOf(i - 1, 0)]
        val after = frames[minOf(i + 1, frames.size - 1)]
        val turn = kotlin.math.acos((before.tangent dot after.tangent).coerceIn(-1f, 1f))
        val span = (after.center - before.center).length
        if (turn < MIN_TURN || span <= 0f) Float.MAX_VALUE else span / turn
    }

    internal fun placed(frame: Frame, drop: Float, facing: Vec3?): Frame {
        val center = frame.center - frame.up * drop
        if (facing == null) return Frame(center, frame.tangent, frame.up)
        return Frame(center, facing, (frame.up - facing * (frame.up dot facing)).normalized())
    }

    /** A run from its rings' centres, through a spline over them. */
    fun sweep(spec: SweepSpec): Patch {
        val first = spec.us.first()
        val range = spec.us.last() - first
        val along = FloatArray(spec.us.size) { (spec.us[it] - first) / range }
        val frames = spec.facing?.let { facing -> spec.centres.map { Frame(it, facing, spec.up) } }
            ?: frames(AxisSpline(spec.centres, along), along.asList(), spec.up)
        val capRings = defaultCapRings(spec.us.size)
        val slices = spec.us.indices.map { ring ->
            val closure = endScale(ring, capRings, spec.startClosure) * endScale(spec.us.size - 1 - ring, capRings, spec.endClosure)
            val frame = frames[ring]
            Slice(spec.us[ring], frame, spec.heights[ring] * closure, spec.widths[ring] * closure, spec.section, frame.rotation)
        }
        return loft(spec.name, slices, spec.section.controlPoints.size)
    }

    /** The grid sampled from [slices] at fixed v, with its shading normals from [shaded]. */
    fun loft(name: String, slices: List<Slice>, segments: Int, shaded: List<Slice> = slices): Patch {
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
        return Patch(name, slices.size, segments, closedV = true, slices = slices, points = points, proxyNormals = proxyNormals(shaded, segments))
    }

    /**
     * Evenly spaced u values, or more of them over the spec's focus, with the nearest ring moved onto each joint, so a joint always lands on a ring. A flat end's
     * pole shares its u with the ring it closes, and the other rings space evenly between.
     */
    internal fun ringUs(spec: TubeSpec): List<Float> {
        val n = spec.rings
        val first = if (spec.startClosure == EndClosure.FLAT) 1 else 0
        val last = if (spec.endClosure == EndClosure.FLAT) n - 2 else n - 1
        val steps = (last - first).coerceAtLeast(1)
        val span = spec.uEnd - spec.uStart
        val focus = spec.focus
        val us = FloatArray(n) {
            val step = (it - first).coerceIn(0, steps)
            if (focus == null) spec.uStart + span * step / steps else spec.uStart + span * focus.at(step.toFloat() / steps)
        }
        for (joint in spec.jointUs) {
            if (joint < spec.uStart || joint > spec.uEnd) continue
            val nearest = us.indices.minBy { abs(us[it] - joint) }
            us[nearest] = joint
        }
        return us.toList()
    }

    internal fun frames(axis: AxisSpline, us: List<Float>, up: Vec3?): List<Frame> {
        var current = up ?: PartAxes.of(axis.tangentAt(us.first())).up
        return us.map { u ->
            val tangent = axis.tangentAt(u)
            current = (current - tangent * (current dot tangent)).normalized()
            Frame(axis.pointAt(u), tangent, current)
        }
    }

    internal fun defaultCapRings(rings: Int): Int = max(MIN_CAP_RINGS, rings / CAP_RING_DIVISOR)

    /** The scale of the ring [fromEnd] rings from an end the drawing shapes: only a closed end's last ring closes. */
    private fun drawnEndScale(fromEnd: Int, closure: EndClosure): Float =
        if (closure != EndClosure.OPEN && fromEnd == 0) 0f else 1f

    /** The scale of [ring], at [u], of a run starting at [uStart] inside another: closed at its first ring and rising to full size at [handover]. */
    private fun risingScale(ring: Int, u: Float, uStart: Float, handover: Float): Float {
        if (ring == 0) return 0f
        if (u >= handover || handover <= uStart) return 1f
        return COVERED_SCALE + (1f - COVERED_SCALE) * (u - uStart) / (handover - uStart)
    }

    /** The scale of the ring [fromEnd] rings from an end inside another run: closed at its last ring, and sunk [pastHandover]. */
    private fun sinkingScale(fromEnd: Int, pastHandover: Boolean): Float = when {
        fromEnd == 0 -> 0f
        pastHandover -> COVERED_SCALE
        else -> 1f
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
