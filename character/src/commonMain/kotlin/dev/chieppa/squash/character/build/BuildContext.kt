package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.model.CapShape
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.geom.AxisSpline
import dev.chieppa.squash.geom.EndClosure
import dev.chieppa.squash.geom.Vec3

/** What a block builds from: its template, resolved parameters, the part's axis and the blocks switched on. */
class BuildContext(
    val template: PartTemplate,
    private val params: Map<String, Float>,
    private val blocks: List<Block>,
) {
    /** The axis length: lengths in a template are fractions of this. */
    val axisLength: Float = params[SIZE_PARAM] ?: 1f

    val axis: AxisSpline = AxisSpline(listOf(Vec3.ZERO, template.axis.rest.toVec3().normalized() * axisLength))

    val jointUs: List<Float> = blocks.flatMap { it.joints() }.map { it.u }

    fun value(param: Param): Float = param.resolve(params)

    fun length(param: Param): Float = value(param) * axisLength

    /** How a run that stops at [u] closes [end]: open on the socket plane, else capped, else flat. */
    fun closure(end: End, u: Float): EndClosure {
        val reachesEnd = if (end == End.START) u <= 0f else u >= 1f
        if (!reachesEnd) return EndClosure.FLAT
        if (template.openEnd == end) return EndClosure.OPEN
        return when (blocks.firstNotNullOfOrNull { it.capShape(end) }) {
            null, CapShape.FLAT -> EndClosure.FLAT
            CapShape.ROUND -> EndClosure.ROUND
            CapShape.POINTED -> EndClosure.POINTED
        }
    }
}
