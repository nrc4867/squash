package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.ParamRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("pocket")
@Doc(
    "Makes the part a recess into the part it sits on: its first run is the hollow, from its mouth at the socket into the part along its " +
        "rest, cut into the parent's runs with a wall round it that a lining fills again, a rolled rim round the mouth, and the run's own " +
        "areas painted on the hollow's inside. Rest the part pointing into the part it sits on.",
)
data class Pocket(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Doc("Radius of the rolled rim round the mouth, as a share of the mouth's radius; 0 for a plain edge.")
    @Range(min = 0.0, max = 1.0)
    val rim: Float = DEFAULT_RIM,
    @Doc("How thick the lining round the hollow is, as a share of the mouth's radius, at least the rim's width.")
    @Range(min = 0.05, max = 2.0)
    val wall: Float = DEFAULT_WALL,
) : Block {
    override fun lint(ctx: LintContext): List<Finding> = when {
        rim !in 0f..1f -> ctx.finding("rim", "$rim is outside 0 to 1")
        wall !in 0.05f..2f -> ctx.finding("wall", "$wall is outside 0.05 to 2")
        else -> emptyList()
    }

    companion object {
        const val DEFAULT_RIM = 0.25f
        const val DEFAULT_WALL = 0.6f
    }
}
