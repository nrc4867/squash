package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.geom.LineRail
import dev.chieppa.squash.geom.MembraneBuilder
import dev.chieppa.squash.geom.MembraneRail
import dev.chieppa.squash.geom.MembraneShape
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_NAME = "web"
private const val SEPARATOR = ":"

/**
 * Two rails a membrane spans: digit [first] of the row and digit [second], or in place of a second digit the part's own run
 * [along] a span of u read from the rail's root to its edge, with how far up them it reaches when not the membrane's.
 */
@Serializable
@Doc("Two rails a membrane spans: a digit of the row by its place, and a second digit or, with along, the part's own run; reach in place of the membrane's.")
data class MembranePair(
    @Range(min = 0.0)
    val first: Int,
    @Range(min = 0.0)
    val second: Int? = null,
    @Doc("The part's own run as the second rail, from u at the rail's root to u at its edge, in place of a second digit.")
    val along: Span? = null,
    val reach: Param? = null,
)

@Serializable
@SerialName("membrane")
@Doc("A web spanned between neighbouring digits of a toe row or a digits row listed before it, or a digit and the part's own run, one patch a pair, named for the membrane and the pair's first digit; thin, its own mesh, riding the bones of the digits either side.")
data class Membrane(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Doc("The toe row or digits row whose digits it spans. The row is listed before the membrane.")
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
    @Doc("The pairs of digits it spans; each digit with the next across the row when left out, leaving out a digit turned back and, on a digits row, a named digit.")
    val between: List<MembranePair>? = null,
) : Block {
    private val patchName get() = name ?: DEFAULT_NAME

    override fun patchNames() = listOf(PatchName(patchName, repeats = true))

    override fun build(ctx: BuildContext): List<Patch> {
        ctx.blocksOn.filterIsInstance<Digits>().firstOrNull { it.rowName == on }?.let { return digitWebs(ctx, it) }
        val row = ctx.blocksOn.filterIsInstance<ToeRow>().firstOrNull { it.name == on } ?: return emptyList()
        val digits = ctx.built.filter { it.name.startsWith(on + SEPARATOR) }.associateBy { it.name.removePrefix(on + SEPARATOR).toIntOrNull() }
        val shape = MembraneShape(ctx.value(thickness), ctx.value(sag))
        return pairs(row, digits.keys.filterNotNull().sorted()).mapNotNull { pair ->
            val first = digits[pair.first] ?: return@mapNotNull null
            ctx.checkCancelled()
            val share = ctx.value(pair.reach ?: reach)
            val second = pair.along?.let { runRail(ctx, it, share) } ?: pair.second?.let(digits::get)?.let { rail(it, share) } ?: return@mapNotNull null
            MembraneBuilder.membrane(patchName + SEPARATOR + pair.first, rail(first, share), second, rings, segments, shape)
        }
    }

    /**
     * The webs between the digits [row] builds, each digit's rail its axis from inside the run it comes out of to [reach] of the way on
     * from there to its tip, as thick there as the digit is, so the web rides that digit's bones.
     */
    private fun digitWebs(ctx: BuildContext, row: Digits): List<Patch> {
        val rails = DigitRails.of(ctx, row)
        val shape = MembraneShape(ctx.value(thickness), ctx.value(sag))
        val pairs = between ?: rails.counted.zipWithNext { a, b -> MembranePair(a, b) }
        return pairs.mapNotNull { pair ->
            val second = pair.second ?: return@mapNotNull null
            ctx.checkCancelled()
            val share = ctx.value(pair.reach ?: reach)
            val first = rails.rail(pair.first, share) ?: return@mapNotNull null
            MembraneBuilder.membrane(patchName + SEPARATOR + pair.first, first, rails.rail(second, share) ?: return@mapNotNull null, rings, segments, shape)
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

    /** The part's own run covering [along], as a rail from its root at [along]'s start toward its edge, [share] of the way to its end. */
    private fun runRail(ctx: BuildContext, along: Span, share: Float): MembraneRail? {
        val low = minOf(along.start, along.end)
        val high = maxOf(along.start, along.end)
        val run = ctx.built.firstOrNull { patch ->
            patch.spanned == null && !patch.name.startsWith(on + SEPARATOR) && patch.slices?.let { it.first().u <= low && it.last().u >= high } == true
        } ?: return null
        return MembraneRail(run, along.start, along.start + (along.end - along.start) * share)
    }

    override fun lint(ctx: LintContext): List<Finding> {
        val row = ctx.template.blocks.firstOrNull { it is Digits && it.rowName == on } ?: ctx.block(on)
        val placed = when {
            row !is ToeRow && row !is Digits -> ctx.finding("on", "'$on' is not a toe row or a digits row")
            row is Digits && ctx.template.blocks.indexOf(row) > ctx.template.blocks.indexOf(this) -> ctx.finding("on", "'$on' comes after this membrane")
            row is Digits -> emptyList()
            !ctx.listedBefore(on, this) -> ctx.finding("on", "'$on' comes after this membrane")
            else -> emptyList()
        }
        return placed + ctx.param("reach", reach) + ctx.param("sag", sag) + ctx.param("thickness", thickness) +
            ctx.atLeast("rings", rings, MembraneBuilder.MIN_RINGS) + ctx.atLeast("segments", segments, MembraneBuilder.MIN_SEGMENTS) +
            (if (segments % 2 != 0) ctx.finding("segments", "a membrane's segments are an even number") else emptyList()) +
            between.orEmpty().flatMapIndexed { i, pair ->
                ctx.param("between[$i].reach", pair.reach) +
                    (if (pair.first == pair.second) ctx.finding("between[$i]", "a pair spans two digits") else emptyList()) +
                    (if ((pair.second == null) == (pair.along == null)) ctx.finding("between[$i]", "a pair names a second digit or a run along the part, not both") else emptyList()) +
                    (pair.along?.let { ctx.unit("between[$i].along.start", it.start) + ctx.unit("between[$i].along.end", it.end) } ?: emptyList()) +
                    (if (pair.first < 0 || (pair.second ?: 0) < 0) ctx.finding("between[$i]", "a digit's place is 0 or more") else emptyList())
            } +
            between.orEmpty().groupBy { it.first }.filter { it.value.size > 1 }.keys.flatMap { ctx.finding("between", "two pairs start at digit $it") }
    }
}

/**
 * The rails of a [Digits] row's built digits for a web, by their place among the counted digits, ordered across the row: each digit's axis
 * from a little inside where it comes out of the part's run to its tip, its half height its radius, tapering, its up square to the row.
 */
internal class DigitRails private constructor(
    private val ctx: BuildContext,
    private val row: Digits,
    private val axes: List<Rail>,
) {
    /** One digit's axis in the part's own axes: its patch, its root and direction, length and radius, where it comes out and its u span. */
    class Rail(val patch: String, val root: Vec3, val direction: Vec3, val length: Float, val radius: Float, val emerges: Float, val span: Span)

    /** The places of the counted digits that point forward, in order across the row. */
    val counted: List<Int> get() = axes.indices.filter { axes[it].patch.substringAfter(SEPARATOR).toIntOrNull() != null && axes[it].direction.x > 0f }

    /** The rail of the digit at [k] from inside its root to [share] of the way from where it comes out to its tip. */
    fun rail(k: Int, share: Float): LineRail? {
        val rail = axes.getOrNull(k) ?: return null
        val across = if (row.across == UP_COORDINATE) Vec3(0f, 1f, 0f) else Vec3(0f, 0f, 1f)
        val up = (rail.direction cross across).let { if (it.length > 0f) it.normalized() else across }
        val start = rail.emerges - rail.radius / rail.length
        val end = rail.emerges + (1f - rail.emerges) * share
        val taper = ctx.value(row.taper)
        fun at(t: Float) = rail.root + rail.direction * (rail.length * t)
        fun half(t: Float) = rail.radius * (1f + (taper - 1f) * t.coerceIn(0f, 1f))
        fun u(t: Float) = rail.span.start + (rail.span.end - rail.span.start) * t
        val world = { p: Vec3 -> ctx.axes.point(p.x, p.y, p.z) }
        return LineRail(rail.patch, world(at(start)), world(at(end)), world(up), half(start), half(end), u(start), u(end))
    }

    companion object {
        fun of(ctx: BuildContext, row: Digits): DigitRails {
            val joined = JoinedDigits.of(ctx)?.takeIf { it.row === row }
            val built = row.builtDigits(ctx)
            val rails = if (joined != null) {
                joined.axes.mapIndexed { k, axis -> Rail(joined.patchName(k), axis.root, axis.direction, axis.length, axis.radius, joined.claims.seam[k].coerceIn(0f, 1f), joined.spans[k]) }
            } else {
                val length = ctx.solidLength * ctx.value(row.reach)
                built.mapIndexed { k, i ->
                    val digit = row.digits[i]
                    Rail(
                        row.patchOf(k, i), point(ctx, digit.root.map { Param.of(it) }), digit.direction(ctx.value(row.spread), row.across),
                        digit.length * length, digit.radius * ctx.solidLength * ctx.value(row.thickness), 0f, row.span(i),
                    )
                }
            }
            val order = rails.indices.sortedBy { k -> rails[k].root.let { if (row.across == UP_COORDINATE) it.y else it.z } }
            return DigitRails(ctx, row, order.map(rails::get))
        }
    }
}
