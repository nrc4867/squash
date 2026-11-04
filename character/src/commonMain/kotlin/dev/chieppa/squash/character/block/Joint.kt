package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.character.model.ParamRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("joint")
@Doc("A bone boundary; bones are the spans between joints in u order.")
data class Joint(
    override val name: String,
    @SerialName("if") override val condition: ParamRef? = null,
    @Range(min = 0.0, max = 1.0)
    val u: Float,
    @Range(min = 0.0, max = 1.0)
    val v: Float? = null,
    val hinge: Hinge,
    @Doc("Degrees.")
    val limits: Limits? = null,
    val stretch: Boolean = false,
) : Block {
    override fun joints() = listOf(JointSpec(name, u, v, hinge, limits, stretch))

    override fun withJointAt(name: String, u: Float): Block = if (name == this.name) copy(u = u) else this

    override fun lint(ctx: LintContext): List<Finding> {
        val ordered = limits == null || limits.min <= limits.max
        return ctx.unit("u", u) + ctx.unit("v", v) +
            if (ordered) emptyList() else ctx.finding("limits", "minimum is above maximum")
    }
}
