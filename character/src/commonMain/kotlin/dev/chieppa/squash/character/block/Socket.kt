package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Mirror
import dev.chieppa.squash.character.model.Outward
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.TemplateId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("socket")
@Doc("A child attachment point; pair makes a mirrored twin.")
data class Socket(
    override val name: String,
    @SerialName("if") override val condition: ParamRef? = null,
    @Range(min = 0.0, max = 1.0)
    val u: Float,
    @Range(min = 0.0, max = 1.0)
    val v: Float,
    val outward: Outward = Outward.NORMAL,
    val pair: Mirror? = null,
    val takes: List<TemplateId> = emptyList(),
    @Doc("Degrees the outward normal turns toward the part's end.") @Range(min = -90.0, max = 90.0)
    val tilt: Float = 0f,
    @Doc("Multiplies how deep a child sits inside this part here.") @Range(min = 0.0, max = 4.0)
    val sink: Float = 1f,
) : Block {
    override fun sockets() = listOf(SocketSpec(name, u, v, outward, pair, takes, tilt, sink))

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.unit("v", v) + ctx.paired("v", v, pair) + ctx.onTheSurface("u", u, condition) + ctx.sink("sink", sink)
}
