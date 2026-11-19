package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.Frame
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Slice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TURN = (2.0 * PI).toFloat()

/** The fewest rings round a loop. */
private const val MIN_RINGS = 6

@Serializable
@SerialName("loop")
@Doc(
    "A closed ring: a round wire swept once round an oval about the axis start, its hole along the rest axis and its length along up, " +
        "its last ring on its first, so its rings take one span fewer round it. Its parameters and size set it, and drawn outlines leave it as it is. u runs round the loop from its top, v round the wire from its inside. A separate mesh.",
)
data class Loop(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Doc("The loop's radius across, to the middle of its wire, as a fraction of the template's axis length at the part's size.")
    val radius: Param,
    @Doc("How long the loop is along up for its width, 1 for a round ring: a chain link takes more.")
    val stretch: Param = Param.of(1f),
    @Doc("The wire's radius, as a fraction of the template's axis length at the part's size.")
    val thickness: Param,
) : Block {
    override fun run() = Span(0f, 1f)

    override fun patchNames() = listOf(PatchName(name ?: BODY_PATCH))

    override fun surfaceMethod(): SurfaceMethod = SurfaceMethod.SEPARATE

    override fun build(ctx: BuildContext): List<Patch> {
        val rings = maxOf(ctx.template.topology.rings, MIN_RINGS)
        val segments = ctx.template.topology.segments
        val section = ctx.template.section.spline(segments)
        val across = ctx.value(radius) * ctx.solidLength
        val long = across * ctx.value(stretch)
        val wire = ctx.value(thickness) * ctx.solidLength
        val axes = ctx.axes
        val nu = rings
        val spans = rings - 1
        val points = FloatArray(nu * segments * 3)
        val slices = List(nu) { ring ->
            val angle = TURN * (ring % spans) / spans
            val center = axes.up * (cos(angle) * long) + axes.side * (sin(angle) * across)
            val tangent = (axes.side * (cos(angle) * across) - axes.up * (sin(angle) * long)).normalized()
            val inward = (axes.along cross tangent).let { if ((it dot center) > 0f) it * -1f else it }.normalized()
            val frame = Frame(center, tangent, inward)
            val slice = Slice(ring.toFloat() / spans, frame, 2f * wire, 2f * wire, section, frame.rotation)
            for (column in 0 until segments) {
                val p = slice.pointAt(column.toFloat() / segments)
                val at = (ring * segments + column) * 3
                points[at] = p.x
                points[at + 1] = p.y
                points[at + 2] = p.z
            }
            slice
        }
        return listOf(Patch(name ?: BODY_PATCH, nu, segments, true, slices, points, openEnds = true))
    }

    override fun lint(ctx: LintContext): List<Finding> = ctx.param("radius", radius) + ctx.param("stretch", stretch) + ctx.param("thickness", thickness)
}
