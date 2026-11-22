package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.SlotSide
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.ToothRow
import dev.chieppa.squash.document.ToothTip
import dev.chieppa.squash.document.ToothType
import dev.chieppa.squash.geom.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** How far past its jaw's line a tooth's root reaches, hidden under the lip until the lip lifts off it, a share of the mouth's width. */
private const val ROOT = 0.06f

/** How much narrower and shorter each row behind the first sits. */
private const val ROW_NARROW = 0.12f
private const val ROW_SHORTEN = 0.15f

/** How round a flat tooth's corners are, a share of its width, and how far a chisel's edge slants back, a share of its width. */
private const val FLAT_CORNER = 0.25f
private const val CHISEL_SLANT = 0.2f

/** Places along each side of a tooth, round a rounded end, and along a bar's edge. */
private const val SIDE_STEPS = 6
private const val END_STEPS = 6
private const val BAR_STEPS = 24

/** How far a pointed tooth's sides bow out, a share of its width. */
private const val POINT_BOW = 0.12f

private const val TINY = 1e-6f

/** One tooth or bar, its [loop] in the flat frame, the [jaw] that carries it, and whether it shows [over] the lips. */
class FlatTooth(val id: MarkId, val loop: List<Vec2>, val jaw: JawName, val over: Boolean)

/** One slot on one side, laid along its row: its middle's distance from the mouth's middle, the side's sign, its tooth and whether it shows over. */
private class Laid(val id: String, val offset: Float, val sign: Int, val tooth: ToothType, val over: Boolean)

/**
 * [dentition]'s teeth on [seat]: for each jaw at a level above none, its rows, back rows first and drawn narrower and shorter, laid from
 * the middle outward slot by slot, each side mirrored unless a slot names one. At blocks each slot is a tooth; at bar a row is one band
 * from its first slot to its last, as long at each slot as its tooth, and slots flagged over stay teeth of their own on it. Sizes are
 * shares of the seat's width; each tooth reaches [ROOT] past its jaw's line, so a lifting lip uncovers tooth rather than a gap. See HLD:
 * look, Teeth.
 */
fun layTeeth(dentition: MouthDentition, seat: TeethSeat): List<FlatTooth> = JawName.entries.flatMap { name ->
    val jaw = dentition.jaw(name)
    if (jaw.level == TeethLevel.NONE) return@flatMap emptyList()
    jaw.rows.withIndex().reversed().flatMap { (r, row) -> RowLayout(name, r, row, dentition, seat).teeth(jaw.level) }
}

private class RowLayout(private val jaw: JawName, private val r: Int, row: ToothRow, dentition: MouthDentition, private val seat: TeethSeat) {
    private val w = seat.width
    private val narrow = 1f - ROW_NARROW * r
    private val shorten = 1f - ROW_SHORTEN * r
    private val down = if (jaw == JawName.UPPER) 1f else -1f
    private val prefix = "teeth-${jaw.name.lowercase()}-$r"

    private val laid: List<Laid> = run {
        val middles = dentition.middles(row.slots)
        listOf(1, -1).flatMap { sign ->
            row.slots.withIndex().mapNotNull { (s, slot) ->
                val middle = middles[s] * w * narrow
                val onSide = slot.side == SlotSide.BOTH || (slot.side == SlotSide.LEFT) == (sign > 0)
                if (!onSide || middle > seat.reach(sign)) null else Laid("$prefix-$s-${if (sign > 0) "l" else "r"}", middle, sign, dentition.toothOf(slot), slot.over)
            }
        }
    }

    fun teeth(level: TeethLevel): List<FlatTooth> = when (level) {
        TeethLevel.NONE -> emptyList()
        TeethLevel.BLOCKS -> laid.map(::block)
        TeethLevel.BAR -> listOfNotNull(bar()) + laid.filter { it.over }.map(::block)
    }

    private fun line(x: Float) = if (jaw == JawName.UPPER) seat.upperLine(x) else seat.lowerLine(x)

    private fun place(x: Float, t: Float) = Vec2(x, line(x) + down * t)

    /** [slot] as its own tooth: its outline from root to tip, its tip hooked toward the middle by its curve. */
    private fun block(slot: Laid): FlatTooth {
        val tooth = slot.tooth
        val half = tooth.width * w * narrow / 2f
        val length = tooth.length * w * shorten
        val root = -ROOT * w
        val centre = seat.middle + slot.sign * slot.offset
        val hook = -slot.sign * tooth.curve * length
        val inner = -slot.sign.toFloat()
        val outline = outline(tooth.tip, half, length, root, inner)
        val loop = outline.map { (u, t) ->
            val along = (t / length).coerceIn(0f, 1f)
            place(centre + u + hook * along * along, t)
        }
        return FlatTooth(MarkId(slot.id), loop, jaw, slot.over)
    }

    /** A tooth's outline in its own terms, across ([first]) and along from its jaw's line ([second]), round from the root's one side to its other. */
    private fun outline(tip: ToothTip, half: Float, length: Float, root: Float, inner: Float): List<Pair<Float, Float>> {
        val sides = { from: Float, to: Float -> List(SIDE_STEPS) { i -> from + (to - from) * i / SIDE_STEPS } }
        return when (tip) {
            ToothTip.FLAT -> {
                val corner = FLAT_CORNER * 2f * half
                val upTo = length - corner
                sides(root, upTo).map { -half to it } + arc(-half + corner, upTo, corner, PI.toFloat(), PI.toFloat() / 2f) +
                    arc(half - corner, upTo, corner, PI.toFloat() / 2f, 0f) + sides(upTo, root).map { half to it }
            }
            ToothTip.ROUND -> {
                val upTo = max(root, length - half)
                sides(root, upTo).map { -half to it } + arc(0f, upTo, half, PI.toFloat(), 0f) + sides(upTo, root).map { half to it }
            }
            ToothTip.POINT -> {
                val steps = SIDE_STEPS * 2
                val left = List(steps) { i ->
                    val t = root + (length - root) * i / steps
                    val share = (length - t) / (length - root)
                    -(half * share + POINT_BOW * 2f * half * sin(PI.toFloat() * share)) to t
                }
                left + (0f to length) + left.reversed().map { (u, t) -> -u to t }
            }
            ToothTip.CHISEL -> {
                val outerEnd = length - CHISEL_SLANT * 2f * half
                val a = inner * half
                sides(root, length).map { a to it } + (a to length) + (-a to outerEnd) + sides(outerEnd, root).drop(1).map { -a to it }
            }
        }
    }

    private fun arc(cu: Float, ct: Float, radius: Float, from: Float, to: Float): List<Pair<Float, Float>> = List(END_STEPS + 1) { i ->
        val angle = from + (to - from) * i / END_STEPS
        cu + radius * cos(angle) to ct + radius * sin(angle)
    }

    /**
     * The row's slots not flagged over as one band from the outermost on one side to the outermost on the other, its length at each place
     * running between its slots' teeth's lengths at their middles.
     */
    private fun bar(): FlatTooth? {
        val held = laid.filterNot { it.over }
        if (held.isEmpty()) return null
        fun edge(sign: Int) = held.filter { it.sign == sign }.maxOfOrNull { it.offset + it.tooth.width * w * narrow / 2f } ?: 0f
        val right = edge(1)
        val left = edge(-1)
        if (left + right <= 0f) return null
        val root = -ROOT * w
        val xs = List(BAR_STEPS + 1) { i -> seat.middle - left + (left + right) * i / BAR_STEPS }
        val stops = held.map { Vec2(it.sign * it.offset, it.tooth.length * w * shorten) }.sortedBy { it.x }
        fun lengthAt(x: Float): Float {
            val at = x - seat.middle
            val after = stops.indexOfFirst { it.x >= at }
            return when {
                after < 0 -> stops.last().y
                after == 0 -> stops.first().y
                else -> {
                    val a = stops[after - 1]
                    val b = stops[after]
                    if (b.x - a.x < TINY) b.y else a.y + (b.y - a.y) * (at - a.x) / (b.x - a.x)
                }
            }
        }
        val tips = xs.map { x -> place(x, lengthAt(x)) }
        val roots = xs.reversed().map { x -> place(x, root) }
        return FlatTooth(MarkId("$prefix-bar"), tips + roots, jaw, over = false)
    }
}
