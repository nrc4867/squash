package dev.chieppa.squash.geom.field

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Cells along the longest side of a solid's box. */
private const val CELLS_ALONG = 12

/** Cells the grid reaches past the solid's box on every side. */
private const val PAD_CELLS = 6

/** How much faster than a true distance a pair's field is taken to change across a cell. */
private const val CELL_LIPSCHITZ = 2f

/**
 * The ring pairs of a [SweptSolid] that can give its field anywhere in each cell of a coarse grid over its box, in its own space, so
 * a read tries those first. A cell keeps a pair unless the pair's field is certainly above the cell's [PairCell.bound] there: beyond
 * its planes by more, or farther from its middles' segment by more than its widest ring. The bound is the least pair's field at
 * the cell's middle grown by as far as a field can change across the cell; a read whose least is above it does not trust the cell
 * and reads every pair. Cells are found when first read, by whichever thread reads them first; a [PairCell] holds only fixed values,
 * so one cell found twice is found alike, and handing it from thread to thread needs no lock.
 */
internal class PairGrid(private val solid: SweptSolid) {
    private val size: Float
    private val ox: Float
    private val oy: Float
    private val oz: Float
    private val nx: Int
    private val ny: Int
    private val nz: Int
    private val cells: Array<PairCell?>

    init {
        val extent = solid.high - solid.low
        size = maxOf(extent.x, extent.y, extent.z, TINY) / CELLS_ALONG
        val pad = size * PAD_CELLS
        ox = solid.low.x - pad
        oy = solid.low.y - pad
        oz = solid.low.z - pad
        nx = ceil((extent.x + 2 * pad) / size).toInt().coerceAtLeast(1)
        ny = ceil((extent.y + 2 * pad) / size).toInt().coerceAtLeast(1)
        nz = ceil((extent.z + 2 * pad) / size).toInt().coerceAtLeast(1)
        cells = arrayOfNulls(nx * ny * nz)
    }

    /** The cell holding ([px], [py], [pz]), or null outside the grid. */
    fun cellAt(px: Float, py: Float, pz: Float): PairCell? {
        val fx = floor((px - ox) / size)
        val fy = floor((py - oy) / size)
        val fz = floor((pz - oz) / size)
        if (fx < 0f || fy < 0f || fz < 0f || fx >= nx || fy >= ny || fz >= nz) return null
        val i = fx.toInt()
        val j = fy.toInt()
        val k = fz.toInt()
        val at = i + nx * (j + ny * k)
        return cells[at] ?: found(i, j, k).also { cells[at] = it }
    }

    private fun found(i: Int, j: Int, k: Int): PairCell {
        val half = size * 0.5f
        val cx = ox + i * size + half
        val cy = oy + j * size + half
        val cz = oz + k * size + half
        val reachOut = sqrt(3f) * half
        val pairs = solid.pairCount
        val lows = FloatArray(pairs) { lowest(cx, cy, cz, half, reachOut, it) }
        val byLow = IntArray(pairs) { it }
        for (n in 1 until pairs) {
            val p = byLow[n]
            var m = n
            while (m > 0 && lows[byLow[m - 1]] > lows[p]) {
                byLow[m] = byLow[m - 1]
                m--
            }
            byLow[m] = p
        }
        var least = Float.MAX_VALUE
        var guess = byLow[0]
        for (p in byLow) {
            if (least != Float.MAX_VALUE && lows[p] > maxOf(least + CELL_LIPSCHITZ * reachOut, 0f)) break
            val field = solid.pairAt(cx, cy, cz, p, null)
            if (field < least) {
                least = field
                guess = p
            }
        }
        val bound = least + CELL_LIPSCHITZ * reachOut
        val keep = maxOf(bound, 0f)
        var count = 1
        for (p in byLow) if (p != guess && lows[p] <= keep) count++
        val kept = IntArray(count)
        val keptLows = FloatArray(count)
        kept[0] = guess
        keptLows[0] = lows[guess]
        var at = 1
        for (p in byLow) if (p != guess && lows[p] <= keep) {
            kept[at] = p
            keptLows[at++] = lows[p]
        }
        return PairCell(kept, keptLows, bound)
    }

    /**
     * A lower bound on pair [p]'s field over the cube [half] a side either way of ([cx], [cy], [cz]), [reachOut] its half diagonal,
     * the same bounds a read skips a pair by: how far beyond the pair's planes every point lies, and how far from its middles'
     * segment less its widest ring. Within the pair's planes only the second holds, and it may be below zero.
     */
    private fun lowest(cx: Float, cy: Float, cz: Float, half: Float, reachOut: Float, p: Int): Float {
        val before = solid.plane(cx, cy, cz, p)
        val after = solid.plane(cx, cy, cz, p + 1)
        val beforeSpread = solid.planeSpread(p) * half
        val afterSpread = solid.planeSpread(p + 1) * half
        val beforeHigh = before + beforeSpread
        val beforeLow = before - beforeSpread
        val afterLow = after - afterSpread
        val past = when {
            beforeHigh < 0f -> -beforeHigh
            beforeLow >= 0f && afterLow > 0f -> afterLow
            else -> 0f
        }
        val segment = sqrt(solid.segmentSquared(cx, cy, cz, p)) - reachOut - solid.pairReach(p)
        return maxOf(past, segment)
    }

    private companion object {
        const val TINY = 1e-6f
    }
}

/**
 * The ring [pairs] a [PairGrid] cell keeps: the one least at its middle first, then the rest by the least their field can be in the cell,
 * [lows], and the [bound] a read's least must not pass to trust them.
 */
internal class PairCell(val pairs: IntArray, val lows: FloatArray, val bound: Float)

/** How far a plane square to unit ([x], [y], [z]) can move across a cube of half side one: the sum of its sizes. */
internal fun spreadOf(x: Float, y: Float, z: Float): Float = abs(x) + abs(y) + abs(z)
