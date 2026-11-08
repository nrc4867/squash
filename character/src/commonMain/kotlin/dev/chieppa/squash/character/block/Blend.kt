package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.build.BlendSpec
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val SEPARATOR = ":"

@Serializable
@SerialName("blend")
@Doc("A fillet where the part's patches cross, grown by moving the vertices near each crossing; every patch keeps its grid.")
data class Blend(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Doc("How wide the fillet reaches, as a fraction of the axis length; the surface grows by at most a quarter of it.")
    val radius: Param,
    @Doc("How much of the fillet two surfaces facing each other across a gap still get, from 0 to 1.")
    @Range(min = 0.0, max = 1.0)
    val apart: Float = 0f,
    @Doc("The pairs of patches it fillets, by name; a repeating block's name covers all its patches. Every pair when left out.")
    val between: List<List<String>>? = null,
    @Doc("The patches that keep their place and lend only their shape, by name; the patches they meet then cover the whole fillet.")
    val still: List<String> = emptyList(),
    @Doc("Whether it also fillets where the part comes out of the part it sits on, moving the part's vertices near that crossing and keeping the other's.")
    val socket: Boolean = false,
) : Block {
    /** The blend this block asks of a part built in [ctx]. */
    fun spec(ctx: BuildContext): BlendSpec = BlendSpec(ctx.length(radius), apart, between, still)

    /** The fillet's radius where the part comes out of its parent, for a part of [axisLength] whose parameters are [values]. */
    fun socketRadius(values: Map<String, Float>, axisLength: Float): Float = radius.resolve(values) * axisLength

    override fun lint(ctx: LintContext): List<Finding> {
        val known = ctx.template.blocks.flatMap { block -> block.patchNames().map { it.name } }.toSet()
        return ctx.param("radius", radius) +
            (if (apart in 0f..1f) emptyList() else ctx.finding("apart", "$apart is outside 0 to 1")) +
            still.filter { it.substringBefore(SEPARATOR) !in known }.flatMap { ctx.finding("still", "no patch '$it'") } +
            between.orEmpty().flatMapIndexed { i, pair ->
                (if (pair.size == 2) emptyList() else ctx.finding("between[$i]", "a pair names two patches")) +
                    pair.filter { it.substringBefore(SEPARATOR) !in known }.flatMap { ctx.finding("between[$i]", "no patch '$it'") }
            }
    }
}
