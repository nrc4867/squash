package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("pad")
@Doc("A raised area tagged with a palette slot.")
data class Pad(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Range(min = 0.0, max = 1.0)
    val u: Float? = null,
    @Range(min = 0.0, max = 1.0)
    val v: Float? = null,
    @Doc("The toe row this pad repeats along, in place of u.")
    val on: String? = null,
    val size: Param,
    @Doc("A palette slot.")
    val color: String? = null,
) : Block {
    override fun lint(ctx: LintContext): List<Finding> {
        val placed = when {
            on != null && ctx.block(on) !is ToeRow -> ctx.finding("on", "'$on' is not a toe row")
            on == null && u == null -> ctx.finding("u", "a pad needs u or on")
            else -> emptyList()
        }
        return placed + ctx.unit("u", u) + ctx.unit("v", v) + ctx.param("size", size)
    }
}
