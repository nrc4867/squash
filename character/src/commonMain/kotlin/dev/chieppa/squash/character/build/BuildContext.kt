package dev.chieppa.squash.character.build

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
import dev.chieppa.squash.geom.ViewSizing
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers

/**
 * What a block builds from: its template, resolved parameters, drawn outlines, the part's axis and the blocks switched
 * on. Blocks build in file order, and [built] holds what the blocks before this one made.
 */
class BuildContext(
    val template: PartTemplate,
    private val params: Map<String, Float>,
    private val blocks: List<Block>,
    outlines: Map<View, OutlineCurve> = emptyMap(),
    /** Throws if this build's run was cancelled. A block building several patches calls it between them. */
    val checkCancelled: () -> Unit = {},
) {
    val axes: PartAxes = PartBuilder.axes(template)

    private val size: Float = params[SIZE_PARAM] ?: 1f
    private val drawn = DrawnViews(template, outlines, axes, size)

    /** What drawn Side and Top outlines set, if either is drawn. */
    val views: ViewSizing? = drawn.sizing

    /** The section a drawn Front outline sets, if it is drawn. */
    val front: FrontSection? = drawn.front

    /** The axis length: lengths in a template are fractions of this. */
    val axisLength: Float = views?.length ?: (size * template.axis.length)

    val axis: AxisSpline = views?.axis ?: restAxis()

    val jointUs: List<Float> = blocks.flatMap { it.joints() }.map { it.u }

    private val patches = mutableListOf<Patch>()

    val built: List<Patch> get() = patches

    internal fun add(built: List<Patch>) {
        patches += built
    }

    /** The first run already built that reaches [u]. */
    fun runAt(u: Float): Patch? = patches.firstOrNull { it.covers(u) }

    fun value(param: Param): Float = param.resolve(params)

    fun length(param: Param): Float = value(param) * axisLength

    /** Straight along the rest direction, or bent toward the underside at the template's bend point. */
    private fun restAxis(): AxisSpline {
        val length = size * template.axis.length
        val end = axes.point(length, 0f, 0f)
        val bend = template.axis.bend?.let { value(it) } ?: 0f
        if (bend == 0f) return AxisSpline(listOf(Vec3.ZERO, end))
        val at = template.axis.bendAt
        return AxisSpline(
            listOf(Vec3.ZERO, axes.point(length * at, -bend * length, 0f), end),
            floatArrayOf(0f, at, 1f),
        )
    }

    /** How a run that stops at [u] closes [end]: open on the socket plane, else with that end's cap, else flat. */
    fun closure(end: End, u: Float): EndClosure {
        val reachesEnd = if (end == End.START) u <= 0f else u >= 1f
        if (reachesEnd && template.openEnd == end) return EndClosure.OPEN
        return when (blocks.firstNotNullOfOrNull { it.capShape(end) }) {
            null, CapShape.FLAT -> EndClosure.FLAT
            CapShape.ROUND -> EndClosure.ROUND
            CapShape.POINTED -> EndClosure.POINTED
        }
    }
}
