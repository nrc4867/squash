package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.geom.MembraneBuilder
import dev.chieppa.squash.geom.MembraneRail
import dev.chieppa.squash.geom.MembraneShape
import dev.chieppa.squash.geom.Patch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_NAME = "web"
private const val SEPARATOR = ":"

/** Two digits of a row a membrane spans, by their places in the row, with how far up them it reaches when not the membrane's. */
@Serializable
@Doc("Two digits a membrane spans, by their places in the row; reach in place of the membrane's.")
data class MembranePair(
    @Range(min = 0.0)
    val first: Int,
    @Range(min = 0.0)
    val second: Int,
    val reach: Param? = null,
)

@Serializable
@SerialName("membrane")
@Doc("A web spanned between neighbouring digits of a toe row listed before it, one patch a pair, named for the membrane and the pair's first digit.")
data class Membrane(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Doc("The toe row whose digits it spans. The row is listed before the membrane.")
    val on: String,
    @Doc("How far up the digits its free edge meets them, as a share of each digit's span from its root.")
    val reach: Param,
    @Doc("How far its free edge dips toward its middle, as a share of its reach.")
    val sag: Param = Param.of(0f),
    @Doc("How thick it is half way between two digits, as a share of their thickness.")
    val thickness: Param,
    @Doc("Rings across it, from one digit to the other.")
    @Range(min = 4.0)
    val rings: Int,
    @Doc("Points round each ring, an even number.")
    @Range(min = 6.0)
    val segments: Int,
    @Doc("The pairs of digits it spans; each digit with the next when left out, leaving out a digit turned back.")
    val between: List<MembranePair>? = null,
) : Block {
    private val patchName get() = name ?: DEFAULT_NAME

    override fun patchNames() = listOf(PatchName(patchName, repeats = true))

    override fun build(ctx: BuildContext): List<Patch> {
        val row = ctx.blocksOn.filterIsInstance<ToeRow>().firstOrNull { it.name == on } ?: return emptyList()
        val digits = ctx.built.filter { it.name.startsWith(on + SEPARATOR) }.associateBy { it.name.removePrefix(on + SEPARATOR).toIntOrNull() }
        val shape = MembraneShape(ctx.value(thickness), ctx.value(sag))
        return pairs(row, digits.keys.filterNotNull().sorted()).mapNotNull { pair ->
            val first = digits[pair.first] ?: return@mapNotNull null
            val second = digits[pair.second] ?: return@mapNotNull null
            ctx.checkCancelled()
            val share = ctx.value(pair.reach ?: reach)
            MembraneBuilder.membrane(patchName + SEPARATOR + pair.first, rail(first, share), rail(second, share), rings, segments, shape)
        }
    }

    /** The pairs it spans among the [built] digits of [row]: [between], or each digit with the next, leaving out a digit turned back. */
    private fun pairs(row: ToeRow, built: List<Int>): List<MembranePair> =
        between ?: built.filter { !row.pointsBack(it) }.zipWithNext { a, b -> MembranePair(a, b) }

    private fun rail(digit: Patch, share: Float): MembraneRail {
        val slices = requireNotNull(digit.slices) { "Digit ${digit.name} has no slices" }
        val root = slices.first().u
        return MembraneRail(digit, root, root + (slices.last().u - root) * share)
    }

    override fun lint(ctx: LintContext): List<Finding> {
        val placed = when {
            ctx.block(on) !is ToeRow -> ctx.finding("on", "'$on' is not a toe row")
            !ctx.listedBefore(on, this) -> ctx.finding("on", "'$on' comes after this membrane")
            else -> emptyList()
        }
        return placed + ctx.param("reach", reach) + ctx.param("sag", sag) + ctx.param("thickness", thickness) +
            ctx.atLeast("rings", rings, MembraneBuilder.MIN_RINGS) + ctx.atLeast("segments", segments, MembraneBuilder.MIN_SEGMENTS) +
            (if (segments % 2 != 0) ctx.finding("segments", "a membrane's segments are an even number") else emptyList()) +
            between.orEmpty().flatMapIndexed { i, pair ->
                ctx.param("between[$i].reach", pair.reach) +
                    (if (pair.first == pair.second) ctx.finding("between[$i]", "a pair spans two digits") else emptyList()) +
                    (if (pair.first < 0 || pair.second < 0) ctx.finding("between[$i]", "a digit's place is 0 or more") else emptyList())
            } +
            between.orEmpty().groupBy { it.first }.filter { it.value.size > 1 }.keys.flatMap { ctx.finding("between", "two pairs start at digit $it") }
    }
}
