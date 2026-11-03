package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.geom.PadBuilder
import dev.chieppa.squash.geom.Patch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val PATCH_PREFIX = "pad"
private const val PATCH_SEPARATOR = ":"
private const val V_UNDER = 0.5f
private const val ALONG_TOE = 0.62f

@Serializable
@SerialName("pad")
@Doc("A raised area tagged with a palette slot, on the first run listed before it that reaches its u.")
data class Pad(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Range(min = 0.0, max = 1.0)
    val u: Float? = null,
    @Doc("Around the surface; underneath when left out.")
    @Range(min = 0.0, max = 1.0)
    val v: Float? = null,
    @Doc("The toe row this pad repeats along, in place of u. The row is listed before the pad.")
    val on: String? = null,
    val size: Param,
    @Doc("A palette slot.")
    val color: String? = null,
) : Block {
    override fun build(ctx: BuildContext): List<Patch> {
        val own = PATCH_PREFIX + PATCH_SEPARATOR + (name ?: on)
        val radius = ctx.length(size)
        val around = v ?: V_UNDER
        if (on != null) {
            val row = on + PATCH_SEPARATOR
            return ctx.built.filter { it.name.startsWith(row) }.map { toe ->
                val rings = requireNotNull(toe.slices) { "Toe ${toe.name} has no slices" }
                val at = rings.first().u + (rings.last().u - rings.first().u) * ALONG_TOE
                PadBuilder.pad(own + PATCH_SEPARATOR + toe.name.removePrefix(row), toe, at, around, radius)
            }
        }
        val at = requireNotNull(u) { "Pad ${name ?: ""} has neither u nor on" }
        val run = requireNotNull(ctx.runAt(at)) { "Pad ${name ?: ""}: no run reaches u = $at" }
        return listOf(PadBuilder.pad(own, run, at, around, radius))
    }

    override fun lint(ctx: LintContext): List<Finding> {
        val placed = when {
            on != null && ctx.block(on) !is ToeRow -> ctx.finding("on", "'$on' is not a toe row")
            on != null && !ctx.listedBefore(on, this) -> ctx.finding("on", "'$on' comes after this pad")
            on == null && u == null -> ctx.finding("u", "a pad needs u or on")
            else -> emptyList()
        }
        return placed + ctx.unit("u", u) + ctx.unit("v", v) + ctx.param("size", size)
    }
}
