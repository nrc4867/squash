package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.clipClosed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Places along each lip of a shaped mouth. */
private const val SAMPLES = 25

/** How far `wide` 1 draws the corners out, a share of the mouth's half width. */
private const val WIDE_REACH = 0.25f

/** How far `round` 1 draws the corners in, a share of the mouth's half width. */
private const val ROUND_NARROW = 0.5f

/** How far `smile` 1 lifts the corners, a share of the mouth's width. */
private const val SMILE_LIFT = 0.16f

/** How far `upperLip` 1 raises the upper lip's middle, a share of the mouth's width. */
private const val UPPER_RAISE = 0.1f

/** How far `round` 1 arches the upper lip up, a share of the lower lip's drop under it. */
private const val ROUND_ARCH = 0.35f

/** How much of the drop `lowerLip` 1 takes back as the lower lip tucks up under the teeth. */
private const val TUCK = 0.7f

/** Past how hard a press the lips show a crease under them. */
private const val CREASE_FROM = 0.3f

/** How far under the lip line a full press's crease sits at its middle, a share of the mouth's width. */
private const val CREASE_GAP = 0.035f

/** How far the crease reaches either side of the middle, a share of the mouth's half width. */
private const val CREASE_REACH = 0.5f

/** Below this a jaw has not opened. */
private const val LEAST_OPEN = 0.01f

/** How far below the lip line a tooth's top may sit and still be an upper tooth, a share of the mouth's width. */
private const val UPPER_TOOTH_REACH = 0.03f

/** The tongue control at which the tongue's tip is up at the upper teeth; past it the tongue comes out. */
private const val TONGUE_UP = 0.5f

/** How far under the upper lip the lifted tongue's top stays, a share of its height. */
private const val TONGUE_TIP_ROOM = 0.1f

/** How far up behind the upper teeth the lifted tongue's tip tucks, a share of its height. */
private const val TONGUE_BEHIND_TEETH = 0.15f

/** How near the middle the upper teeth's lower edge is read, a share of the mouth's half width. */
private const val TEETH_MIDDLE = 0.3f

/** How much of the tongue is still above the lower lip when it is fully out. */
private const val TONGUE_OUT_ABOVE = 0.3f

/** A tongue no mark draws: its width and height, shares of the mouth's width, how much of it sits below the lower lip at rest, and its places. */
private const val PLAIN_TONGUE_WIDTH = 0.35f
private const val PLAIN_TONGUE_HEIGHT = 0.2f
private const val PLAIN_TONGUE_SUNK = 0.4f
private const val PLAIN_TONGUE_STEPS = 16

/** Past how far a tuck the upper teeth rest over the lower lip, and how far down its band they reach, a share of it. */
private const val TUCK_UNDER = 0.3f
private const val TUCK_REST = 0.4f

/** How much a full press and a full tuck thin a lip's band, and a full pucker thickens it. */
private const val PRESS_THIN = 0.3f
private const val TUCK_THIN = 0.6f
private const val ROUND_FULL = 0.4f

/** How far down the room below the upper lip reaches, in mouth widths: past anything a tongue reaches. */
private const val BELOW = 10f

private const val TINY = 1e-6f

/** The id a tongue no mark draws takes. */
val PLAIN_TONGUE = MarkId("tongue")

/**
 * The one mouth shaper: a mouth as drawn ([MouthDrawing]) shaped by its [MarkControls], as a style scales them, in its flat frame. The
 * built surface and the Face stage both draw what it gives, so they agree. Each control acts across the lip line by how far a place is
 * from the middle toward a corner:
 * - `open` drops a lower lip below the upper by [depth] of the mouth's width at full open, from nothing at the corners;
 * - `wide` draws the corners out or in, `round` draws them in and rounds the opening, arching the upper lip;
 * - `smile` lifts or drops both lips toward the corners; `upperLip` raises the upper lip's middle;
 * - `lowerLip` tucks the lower lip up (taking back the drop) or presses the lips shut, with a crease under them;
 * - `tongue` lifts the tongue to the upper teeth, then out past the lower lip.
 *
 * Teeth that reach the lip line ride the upper lip; teeth below it ride the jaw, as the tongue does. Pieces flagged over, and a tongue out
 * past the lip, draw over the lips and break their ink; everything else shows only inside the opening. See HLD: look, Mouth.
 */
object MouthShaper {
    /** [drawing] shaped by [controls], opening [depth] of its parting width deep, [span] of its line either side of the middle parting. */
    fun shape(drawing: MouthDrawing, controls: MarkControls, depth: Float, span: Float = 1f): FlatMouth = Shaping(drawing, controls, depth, span).shaped()
}

/**
 * One shaping. [width] and [half] are the parting stretch's, [span] of the lip line about its middle: past it, out to the line's own
 * ends, the lips hold shut and only move as the corners do.
 */
private class Shaping(private val drawing: MouthDrawing, private val c: MarkControls, private val depth: Float, span: Float) {
    private val lip = drawing.lip
    private val left = lip.first().x
    private val right = lip.last().x
    private val middle = (left + right) / 2f
    private val half = max((right - left) / 2f * span, TINY)
    private val width = 2f * half
    private val across = (1f + WIDE_REACH * c.wide) * (1f - ROUND_NARROW * c.round)
    private val jaw = c.open * if (c.lowerLip >= 0f) 1f - TUCK * c.lowerLip else 1f + c.lowerLip
    private val reach = depth * width * jaw

    fun lipAt(x: Float): Float {
        if (x <= left) return lip.first().y
        if (x >= right) return lip.last().y
        val i = lip.indexOfLast { it.x <= x }.coerceAtMost(lip.size - 2)
        val a = lip[i]
        val b = lip[i + 1]
        return if (b.x - a.x < TINY) a.y else a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
    }

    private fun toCorner(x: Float) = (abs(x - middle) / half).coerceIn(0f, 1f)

    private fun xOf(x: Float) = middle + (x - middle) * across

    private fun profile(a: Float): Float {
        val drawn = cos(PI.toFloat() / 2f * a)
        val round = sqrt(max(0f, 1f - a * a))
        return drawn + (round - drawn) * c.round
    }

    private fun drop(x: Float) = reach * profile(toCorner(x))

    private fun lift(x: Float) = toCorner(x).let { SMILE_LIFT * width * c.smile * it * it }

    private fun raise(x: Float) = toCorner(x).let { UPPER_RAISE * width * c.upperLip * (1f - it * it) }

    private fun upperY(x: Float) = lipAt(x) - lift(x) - raise(x) - ROUND_ARCH * c.round * drop(x)

    private fun lowerY(x: Float) = lipAt(x) - lift(x) + drop(x)

    /** [p], drawn on a lower tooth as the fully open jaw holds it, where the jaw holds it now. */
    private fun carried(p: Vec2) = Vec2(xOf(p.x), lipAt(p.x) - lift(p.x) + max(0f, p.y - lipAt(p.x)) * jaw)

    /** [p], drawn on an upper tooth at rest, where the upper lip holds it now. */
    private fun ridden(p: Vec2) = Vec2(xOf(p.x), p.y + upperY(p.x) - lipAt(p.x))

    private val xs = List(SAMPLES) { left + (right - left) * it / (SAMPLES - 1) }
    private val upper = xs.map { Vec2(xOf(it), upperY(it)) }
    private val lower = xs.map { Vec2(xOf(it), lowerY(it)) }

    private val drawnInner = drawing.inner?.takeIf { it.size >= MIN_LOOP && jaw >= LEAST_OPEN }

    private val opening: List<Vec2>? = run {
        when {
            drawnInner != null -> drawnInner.map(::carried)
            xs.any { lowerY(it) - upperY(it) > width * LEAST_OPEN } -> upper + lower.reversed().drop(1).dropLast(1)
            else -> null
        }
    }

    fun shaped(): FlatMouth {
        val teeth = drawing.teeth.flatMap(::tooth)
        val front = teeth.filterNot { it.over }.flatMap { it.loop }.filter { abs(it.x - middle) <= TEETH_MIDDLE * half }
        val teethEdge = front.maxOfOrNull { it.y }
        val tongue = tongueMarks().flatMap { tongue(it, teethEdge) }
        val covers = (teeth + tongue).filter { it.over }.map { it.loop }
        val upperCovers = (teeth + tongue).filter { it.over && it.breaksUpper }.map { it.loop }
        val lowerLine = opening?.let { loop -> if (drawnInner != null) bottomOf(loop) else lower }
        return FlatMouth(
            upper = brokenUnder(upper, upperCovers),
            lower = lowerLine?.let { brokenUnder(it, covers) }.orEmpty(),
            crease = crease(),
            opening = opening,
            teeth = teeth,
            tongue = tongue,
            lips = lipBands(),
            lipMoved = !c.drawnLine || covers.isNotEmpty(),
            openingCorners = if (opening == null || drawnInner != null) emptySet() else setOf(0, SAMPLES - 1),
        )
    }

    /** The room below the upper lip, which teeth over a tucked lower lip and a tongue out past it are kept to. */
    private val belowUpper by lazy { upper + Vec2(upper.last().x, upper.last().y + BELOW * width) + Vec2(upper.first().x, upper.first().y + BELOW * width) }

    private fun tooth(mark: FlatMark): List<FlatPiece> {
        val extent = Extent(mark.places)
        val upperTooth = extent.top <= lipAt(extent.middle) + UPPER_TOOTH_REACH * width
        val moved = mark.places.map { if (upperTooth) ridden(it) else carried(it) }
        if (upperTooth && !mark.over && c.lowerLip > TUCK_UNDER) {
            val resting = upper + xs.reversed().map { x -> Vec2(xOf(x), max(lowerY(x), upperY(x)) + TUCK_REST * band(x, drawing.lipLower, tucked = true)) }
            return clipClosed(moved, resting).filter { it.size >= MIN_LOOP }.map { FlatPiece(mark.id, it, over = true, breaksUpper = false) }
        }
        return pieces(mark.id, moved, mark.over, opening)
    }

    private fun tongueMarks(): List<FlatMark> {
        if (drawing.tongue.isNotEmpty()) return drawing.tongue
        if (!drawing.tongueFilled || c.tongue <= 0f) return emptyList()
        val height = PLAIN_TONGUE_HEIGHT * width
        val at = Vec2(middle, lowerY(middle) + PLAIN_TONGUE_SUNK * height - height / 2f)
        return listOf(FlatMark(PLAIN_TONGUE, oval(at, PLAIN_TONGUE_WIDTH * width, height, PLAIN_TONGUE_STEPS)))
    }

    /** [mark] placed as the tongue control lifts it: its tip up to [teethEdge], the lower edge of the upper teeth at the middle, or the upper lip without teeth. */
    private fun tongue(mark: FlatMark, teethEdge: Float?): List<FlatPiece> {
        val drawn = Extent(mark.places)
        val riding = if (mark.id == PLAIN_TONGUE) 0f else drop(drawn.middle) - depth * width * profile(toCorner(drawn.middle)) - lift(drawn.middle)
        val base = mark.places.map { Vec2(if (mark.id == PLAIN_TONGUE) it.x else xOf(it.x), it.y + riding) }
        val extent = Extent(base)
        val height = extent.height
        val tipTo = teethEdge?.let { it - TONGUE_BEHIND_TEETH * height } ?: (upperY(extent.middle) + TONGUE_TIP_ROOM * height)
        val upTop = min(extent.top, tipTo)
        val top = if (c.tongue <= TONGUE_UP) {
            extent.top + (upTop - extent.top) * (c.tongue / TONGUE_UP)
        } else {
            val outTop = lowerY(extent.middle) - TONGUE_OUT_ABOVE * height
            upTop + (outTop - upTop) * ((c.tongue - TONGUE_UP) / (1f - TONGUE_UP))
        }
        val placed = base.map { Vec2(it.x, it.y + top - extent.top) }
        if (c.tongue <= TONGUE_UP || mark.over) return pieces(mark.id, placed, mark.over, opening)
        return clipClosed(placed, belowUpper).filter { it.size >= MIN_LOOP }.map { FlatPiece(mark.id, it, over = true) }
    }

    /** How thick each lip's band is at [x], a share of the mouth's width at its middle thinning to nothing at the corners, as the controls press, pucker or tuck it. */
    private fun band(x: Float, share: Float, tucked: Boolean): Float {
        val a = toCorner(x)
        val press = max(0f, -c.lowerLip)
        val tuck = if (tucked) max(0f, c.lowerLip) else 0f
        return share * width * sqrt(max(0f, 1f - a * a)) * (1f - PRESS_THIN * press - TUCK_THIN * tuck + ROUND_FULL * c.round)
    }

    /** The two lips' filled bands, above the upper lip and below the lower, when the lip line's role is filled. */
    private fun lipBands(): List<List<Vec2>> {
        if (!drawing.lipsFilled) return emptyList()
        val lowerLine = xs.map { Vec2(xOf(it), max(lowerY(it), upperY(it))) }
        val upperBand = upper + xs.reversed().map { Vec2(xOf(it), upperY(it) - band(it, drawing.lipUpper, tucked = false)) }
        val lowerBand = lowerLine + xs.reversed().map { x -> Vec2(xOf(x), max(lowerY(x), upperY(x)) + band(x, drawing.lipLower, tucked = true)) }
        return listOf(upperBand, lowerBand)
    }

    private fun pieces(id: MarkId, loop: List<Vec2>, over: Boolean, within: List<Vec2>?): List<FlatPiece> = when {
        over -> listOf(FlatPiece(id, loop, over = true))
        within == null -> emptyList()
        else -> clipClosed(loop, within).filter { it.size >= MIN_LOOP }.map { FlatPiece(id, it, over = false) }
    }

    private fun crease(): List<Vec2>? {
        val press = -c.lowerLip
        if (press <= CREASE_FROM) return null
        return xs.filter { toCorner(it) <= CREASE_REACH }.map { x ->
            val t = toCorner(x) / CREASE_REACH
            Vec2(xOf(x), lipAt(x) - lift(x) + CREASE_GAP * width * press * (1f - t * t))
        }.takeIf { it.size >= 2 }
    }

    /** The bottom of [loop] across its reach, where a line down the face there last crosses it. */
    private fun bottomOf(loop: List<Vec2>): List<Vec2> {
        val low = loop.minOf { it.x }
        val high = loop.maxOf { it.x }
        return List(SAMPLES) { low + (high - low) * it / (SAMPLES - 1) }.mapNotNull { x ->
            loop.indices.mapNotNull { i ->
                val a = loop[i]
                val b = loop[(i + 1) % loop.size]
                if (x < min(a.x, b.x) || x > max(a.x, b.x) || abs(b.x - a.x) < TINY) null else a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
            }.maxOrNull()?.let { Vec2(x, it) }
        }
    }

    companion object {
        private const val MIN_LOOP = 3
    }
}
