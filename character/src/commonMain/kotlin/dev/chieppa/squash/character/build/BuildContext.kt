package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.model.CapShape
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.AxisSpline
import dev.chieppa.squash.geom.EndClosure
import dev.chieppa.squash.geom.FrontSection
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.geom.ViewSizing
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.arcLengthAxis
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.turningDegrees
import kotlin.math.ceil

/** The most a curved run's axis turns from one ring to the next, in degrees, before it takes more rings than its template's. */
private const val RING_TURN_DEGREES = 6f

/** The rings a curved run adds past what its turn needs, for its ends to close over. */
private const val CURVED_END_RINGS = 8

/** The most rings a curved run takes. */
private const val MAX_CURVED_RINGS = 256

/**
 * What a block builds from: its template, resolved parameters, drawn outlines, the part's axis and the blocks switched
 * on. Blocks build in file order, and [built] holds what the blocks before this one made.
 */
class BuildContext(
    val template: PartTemplate,
    private val params: Map<String, Float>,
    /** The blocks switched on, in file order. */
    val blocksOn: List<Block>,
    outlines: Map<View, OutlineCurve> = emptyMap(),
    /** Throws if this build's run was cancelled. A block building several patches calls it between them. */
    val checkCancelled: () -> Unit = {},
    /** What the part's length along its axis is multiplied by, see [LegLengths]. What lies across the axis keeps its size. */
    lengthFactor: Float = 1f,
) {
    val axes: PartAxes = PartBuilder.axes(template)

    private val size: Float = params[SIZE_PARAM] ?: 1f

    /** Whether the template sweeps along its axis, so a curve gives its path; see [sweeps]. */
    val sweeps: Boolean = template.sweeps

    /** The part's own curve places, its spiral's or its own, which replace its template's curve. */
    private val ownCurve = AxisCurveParams.ownPlaces(params)

    /** Whether the part has a curve of its own that its template ignores, since it does not sweep. */
    val curveIgnored: Boolean = ownCurve.isNotEmpty() && !sweeps

    private val drawn = DrawnViews(template, outlines.shortenedBy(lengthFactor), axes, size, ViewParams.topMovesMiddle(params))

    /** What drawn Side and Top outlines set, if either is drawn. */
    val views: ViewSizing? = drawn.sizing

    /** The section a drawn Front outline sets, if it is drawn. */
    val front: FrontSection? = drawn.front

    /** The axis length before the length factor: lengths in a template are fractions of this. */
    private val fullLength: Float = views?.let { it.length / lengthFactor } ?: (size * template.axis.length)

    /** The template's own axis length at the part's size, drawn or not: what a solid placed in fractions of the axis length is measured in. */
    val solidLength: Float = size * template.axis.length

    /** How far the axis reaches. */
    val axisLength: Float = views?.length ?: (fullLength * lengthFactor)

    /** Whether the part's axis is a curve through places, its own or its template's, on a template that sweeps. */
    val curved: Boolean = sweeps && (ownCurve.isNotEmpty() || template.axis.curve.isNotEmpty())

    /**
     * Whether a drawn part follows its own curve, the drawing giving its width along the length and the curve its path. A template's
     * curve shapes the part undrawn only: drawn, its drawing's midline is its path, so a silhouette traced from the undrawn part keeps it.
     */
    val followsAxis: Boolean = sweeps && ownCurve.isNotEmpty() && views != null

    val axis: AxisSpline = if (followsAxis) curvedAxis() else views?.axis ?: restAxis()

    /** How many rings a run takes: the template's, or more on a curve that turns more than [RING_TURN_DEGREES] a ring would allow. */
    val rings: Int = if (!curved) template.topology.rings else maxOf(template.topology.rings, minOf(MAX_CURVED_RINGS, ceil(turningDegrees(axis) / RING_TURN_DEGREES).toInt() + CURVED_END_RINGS))

    val jointUs: List<Float> = blocksOn.flatMap { it.joints() }.map { it.u }

    private val patches = mutableListOf<Patch>()

    private val sharedBuilds = HashMap<Any, Any>()

    /** What [make] makes for [key], made once for this build however many blocks ask, as a field two blocks cut. */
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> shared(key: Any, make: () -> T): T = sharedBuilds.getOrPut(key, make) as T

    val built: List<Patch> get() = patches

    internal fun add(built: List<Patch>) {
        patches += built
    }

    /** The ring the first run reaching [u] stands there before its ends close, with nothing built. */
    fun ringAt(u: Float): Slice? = blocksOn.firstNotNullOfOrNull { it.ringAt(this, u) }

    /** The first run already built that reaches [u]. */
    fun runAt(u: Float): Patch? = patches.firstOrNull { it.covers(u) }

    fun value(param: Param): Float = param.resolve(params)

    /** The part's parameter values over its template's defaults. */
    val values: Map<String, Float> get() = params

    fun length(param: Param): Float = value(param) * fullLength

    /** Straight along the rest direction, bent toward the underside at the template's bend point, or the part's or the template's curve. */
    private fun restAxis(): AxisSpline {
        if (curved) return curvedAxis()
        val end = axes.point(axisLength, 0f, 0f)
        val bend = template.axis.bend?.let { value(it) } ?: 0f
        if (bend == 0f) return AxisSpline(listOf(Vec3.ZERO, end))
        val at = template.axis.bendAt
        return AxisSpline(
            listOf(Vec3.ZERO, axes.point(axisLength * at, -bend * fullLength, 0f), end),
            floatArrayOf(0f, at, 1f),
        )
    }

    /** The smooth curve from the socket point through the part's own places, or the template's, its u by length along it. */
    private fun curvedAxis(): AxisSpline {
        val own = ownCurve.map { axes.point(it.x, it.y, it.z) }
        val places = listOf(Vec3.ZERO) + own.ifEmpty { template.axis.curve.map { axes.point(value(it.along), value(it.up), value(it.side)) } }
        return arcLengthAxis(places, axisLength)
    }

    /** Whether the template's [block] is switched on for this build. */
    fun isOn(block: Block): Boolean = block in blocksOn

    /** Where the surface passes between [own] and the run covering its end at [u], the middle of their overlap, or null when no other run covers it. */
    fun handover(u: Float, own: Span): Float? =
        blocksOn.firstNotNullOfOrNull { block -> block.run()?.takeIf { it != own && u >= it.start && u <= it.end } }?.let { cover ->
            (maxOf(own.start, cover.start) + minOf(own.end, cover.end)) / 2f
        }

    /** How a run that stops at [u] closes [end]: open on the socket plane, else with that end's cap, else flat. */
    fun closure(end: End, u: Float): EndClosure {
        val reachesEnd = if (end == End.START) u <= 0f else u >= 1f
        if (reachesEnd && template.openEnd == end) return EndClosure.OPEN
        return when (blocksOn.firstNotNullOfOrNull { it.capShape(end) }) {
            null, CapShape.FLAT -> EndClosure.FLAT
            CapShape.ROUND -> EndClosure.ROUND
            CapShape.POINTED -> EndClosure.POINTED
        }
    }
}
