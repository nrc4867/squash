package dev.chieppa.squash.character.mouth.shaped

import dev.chieppa.squash.document.TongueTip
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Rings along a tongue, and round each ring. */
private const val TONGUE_RINGS = 14
private const val TONGUE_SEGMENTS = 16

/** Where a tongue's root lies, a share of the way from the hinge to the mouth's corner, back in the lining. */
private const val ROOT_AT_CORNER = 0.85f

/** How far from its root toward the lip line's middle in front a tongue at rest reaches, a share of the way. */
private const val TIP_REACH = 0.82f

/** How wide a tongue is, a share of the mouth's width across at each place. */
private const val WIDTH_SHARE = 0.5f

/** How thick a tongue is, a share of its widest half-width. */
private const val THICK_SHARE = 0.55f

/** How much of a tongue's thickness lies above the floor it rests on; the rest sinks into it. */
private const val ABOVE_FLOOR = 0.7f

/** How flat a tongue's underside is against its top, a share. */
private const val UNDER_FLAT = 0.6f

/** How deep a groove runs down a tongue's top, a share of its thickness, and how wide, a share of its width. */
private const val GROOVE_DEPTH = 0.45f
private const val GROOVE_WIDTH = 0.28f

/** How much of a tongue's length a fork splits, from its tip, and how far back its notch reaches, a share of the tongue's width. */
private const val FORK_SHARE = 0.3f
private const val FORK_NOTCH = 0.9f

/** Where a beak's tongue runs along its lower half, root to tip, in u, and how far up its top it rides, a share of the way up. */
private const val BEAK_ROOT_U = 0.06f
private const val BEAK_TIP_U = 0.6f
private const val BEAK_RIDE = 0.7f

/** How many places round a ring a beak's top is read at. */
private const val BEAK_SAMPLES = 16

/** Where a tongue lies at rest: its floor's middle line from root to tip, the way up off it at each place, and its half-width there. */
internal class TongueLine(val floor: List<Vec3>, val up: List<Vec3>, val halfWidths: List<Float>)

/**
 * A shaped tongue on the floor of the jaw [path] reads, built open with the jaw: from its root back in the lining behind the mouth's
 * corners forward along the middle of the floor, nearly to the lower lip's middle, as wide as half the mouth across, its tip cut as
 * [tip] says. Its joints ([mouthPartChains]) bend it as the tongue control asks. See HLD: look, Tongue.
 */
fun shapedTongue(path: LipPath, tip: TongueTip): Patch {
    val cut = path.cut
    val rootR = cut.cornerRadius * ROOT_AT_CORNER
    val tipR = rootR + (path.frontRadius - rootR) * TIP_REACH
    val places = List(TONGUE_RINGS) { i -> rootR + (tipR - rootR) * i / (TONGUE_RINGS - 1) }
    val floor = places.map { r ->
        val phi = cut.cutPhi(r)
        cut.hinge + (cut.forward * cos(phi) + cut.up * sin(phi)) * r
    }
    val up = places.map { r ->
        val phi = cut.cutPhi(r)
        cut.forward * -sin(phi) + cut.up * cos(phi)
    }
    val widths = places.map { path.halfWidthAt(it) * WIDTH_SHARE }
    return tonguePatch(TongueLine(floor, up, widths), tip, cut::open)
}

/**
 * A shaped tongue along the top of a beak's lower half, [run] its run in the part's own space, [up] and [side] the part's own ways: from near its root
 * along its middle line, riding up into its top, half as wide as it. See HLD: look, Tongue.
 */
fun beakTongue(run: Patch, up: Vec3, side: Vec3, tip: TongueTip): Patch {
    val places = List(TONGUE_RINGS) { i -> BEAK_ROOT_U + (BEAK_TIP_U - BEAK_ROOT_U) * i / (TONGUE_RINGS - 1) }
    val floor = ArrayList<Vec3>()
    val widths = ArrayList<Float>()
    for (u in places) {
        val around = List(BEAK_SAMPLES) { run.positionAt(u, it.toFloat() / BEAK_SAMPLES) }
        val middle = around.reduce { a, b -> a + b } * (1f / around.size)
        val top = around.maxOf { (it - middle) dot up }
        val half = around.maxOf { abs((it - middle) dot side) }
        floor += middle + up * (top * BEAK_RIDE)
        widths += half * WIDTH_SHARE
    }
    return tonguePatch(TongueLine(floor, floor.map { up }, widths), tip) { it }
}

/** The tongue along [line], its tip cut as [tip], each point carried by [map]. */
private fun tonguePatch(line: TongueLine, tip: TongueTip, map: (Vec3) -> Vec3): Patch {
    val n = line.floor.size
    val lengths = FloatArray(n)
    for (i in 1 until n) lengths[i] = lengths[i - 1] + (line.floor[i] - line.floor[i - 1]).length
    val length = lengths.last()
    val widest = line.halfWidths.max()
    val thick = widest * THICK_SHARE
    val tipHalf = line.halfWidths.last()
    val rings = ArrayList<List<Vec3>>()
    for (i in 0 until n) {
        val along = unitOr(line.floor[minOf(i + 1, n - 1)] - line.floor[maxOf(i - 1, 0)], line.up[i])
        val up = unitOr(line.up[i].without(along), line.up[i])
        val half = line.halfWidths[i]
        val left = length - lengths[i]
        val round = if (tip == TongueTip.FORK || left >= tipHalf) 1f else sqrt((1f - ((tipHalf - left) / tipHalf).let { it * it }).coerceAtLeast(0f))
        val rootClose = if (i == 0) 0f else 1f
        val forkShare = if (tip == TongueTip.FORK) ((lengths[i] / length - (1f - FORK_SHARE)) / FORK_SHARE).coerceIn(0f, 1f) else 0f
        val centre = line.floor[i] + up * (thick * (ABOVE_FLOOR - 0.5f))
        val across = acrossOf(up, along)
        val shaped = ring(TONGUE_SEGMENTS, centre, across * half, up * (thick / 2f), along) { a ->
            val x = sin(a)
            val y = cos(a)
            var depth = if (y < 0f) UNDER_FLAT else 1f
            if (tip == TongueTip.GROOVE && y > 0f) depth *= 1f - GROOVE_DEPTH * exp(-(x / GROOVE_WIDTH).let { it * it })
            var shift = 0f
            if (forkShare > 0f) {
                depth *= 1f - forkShare
                shift = -FORK_NOTCH * half * forkShare * exp(-(x / GROOVE_WIDTH).let { it * it })
            }
            Triple(round * rootClose, depth * round * rootClose, shift)
        }
        rings += shaped.map(map)
    }
    val us = FloatArray(n) { i -> TONGUE_ROOT_U + (TONGUE_TIP_U - TONGUE_ROOT_U) * lengths[i] / length }
    return ringPatch(TONGUE_PATCH, rings, us)
}
