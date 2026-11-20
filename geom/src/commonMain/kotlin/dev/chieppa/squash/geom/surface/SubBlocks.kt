package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.FieldAsk
import dev.chieppa.squash.geom.field.smoothMin
import kotlin.math.sqrt


/**
 * Whether a union lying between [least] and [most] at a point stays more than [margin] off zero: outside wherever [least] is past it,
 * inside wherever [most] is short of minus it.
 */
internal fun clear(least: Float, most: Float, margin: Float): Boolean = least > margin || most < -margin

/** Cells a side of a sub-block. */
internal const val SUB = 2

/** Sub-blocks a side of a block, and in all. */
internal const val SUBS_SIDE = BLOCK / SUB
internal const val SUBS = SUBS_SIDE * SUBS_SIDE * SUBS_SIDE

/** Every sub-block of a block, as a mask, and the octants a block holds. */
private const val ALL = -1L
private const val OCTANTS = SurfaceBlock.OCTANT_COUNT

/** How many of its own blends past the least a member's field may lie and still change the union. */
private const val BLEND_TIMES = 1.25f

/** How far outside its widest blend a member still counts toward the surface's shares, in cells, as [surfaceShares] counts it. */
private const val BAND_CELLS = 2f

/** How far a vertex may be moved out of its cell while it is placed, in cells. */
private const val DRIFT_CELLS = 2f

/**
 * A block's sub-blocks of [SUB] cells a side, and which of a block's members can change the union in each: a member can unless its field at
 * the sub-block's middle, less as far as it can change across the sub-block, lies more than one and a quarter of its own blends past the
 * least of all members' fields there grown by the same, and it is no parent of one that can. Past that its term of the union never reaches
 * the union, so its field is read only where it can, and stands in by the bound its middle gives elsewhere.
 */
internal object SubBlocks {
    /** The sub-blocks each grid point of a block lies in, as a mask. */
    private val cornerSubs = LongArray(BLOCK_CORNERS).also { table ->
        for (k in 0..BLOCK) for (j in 0..BLOCK) for (i in 0..BLOCK) {
            var mask = 0L
            for (c in 0 until SUBS_SIDE) for (b in 0 until SUBS_SIDE) for (a in 0 until SUBS_SIDE) {
                if (i in a * SUB..(a + 1) * SUB && j in b * SUB..(b + 1) * SUB && k in c * SUB..(c + 1) * SUB) mask = mask or (1L shl sub(a, b, c))
            }
            table[BlockGrid.corner(i, j, k)] = mask
        }
    }

    /** The sub-blocks grid point [corner] of a block lies in, as a mask. */
    fun subsOf(corner: Int): Long = cornerSubs[corner]

    private fun sub(a: Int, b: Int, c: Int): Int = a + SUBS_SIDE * (b + SUBS_SIDE * c)

    /** The sub-block cell [cell] of a block lies in. */
    fun ofCell(cell: Int): Int = sub((cell % BLOCK) / SUB, ((cell / BLOCK) % BLOCK) / SUB, (cell / (BLOCK * BLOCK)) / SUB)

    /** The sub-blocks round sub-block [sub], itself among them, as far as a vertex placed in it may drift. */
    fun around(sub: Int): Long {
        val a = sub % SUBS_SIDE
        val b = (sub / SUBS_SIDE) % SUBS_SIDE
        val c = sub / (SUBS_SIDE * SUBS_SIDE)
        val r = (DRIFT_CELLS.toInt() + SUB - 1) / SUB
        var mask = 0L
        for (z in maxOf(0, c - r)..minOf(SUBS_SIDE - 1, c + r)) for (y in maxOf(0, b - r)..minOf(SUBS_SIDE - 1, b + r)) for (x in maxOf(0, a - r)..minOf(SUBS_SIDE - 1, a + r)) {
            mask = mask or (1L shl sub(x, y, z))
        }
        return mask
    }

    /** The grid point at the middle of sub-block [sub], which lies in that sub-block alone. */
    fun middleCorner(sub: Int): Int =
        BlockGrid.corner(sub % SUBS_SIDE * SUB + SUB / 2, (sub / SUBS_SIDE) % SUBS_SIDE * SUB + SUB / 2, sub / (SUBS_SIDE * SUBS_SIDE) * SUB + SUB / 2)

    /** The point at the middle of sub-block [sub] of the block whose lowest grid point is ([i0], [j0], [k0]). */
    fun middle(grid: BlockGrid, i0: Int, j0: Int, k0: Int, sub: Int): Vec3 {
        val half = SUB * 0.5f
        val a = sub % SUBS_SIDE
        val b = (sub / SUBS_SIDE) % SUBS_SIDE
        val c = sub / (SUBS_SIDE * SUBS_SIDE)
        return Vec3((i0 + a * SUB + half) * grid.cell, (j0 + b * SUB + half) * grid.cell, (k0 + c * SUB + half) * grid.cell)
    }

    /** How far a member's field may change between a sub-block's middle and any point of it. */
    fun swing(grid: BlockGrid): Float = LIPSCHITZ * sqrt(3f) * SUB * 0.5f * grid.cell

    /**
     * Which sub-blocks each member of a block can change the union in, from [middles], each member's field at each sub-block's middle,
     * [SUBS] values a member, the members' [parentSlots] among them and own [blends]; a parent can wherever a child of it can.
     */
    fun relevance(grid: BlockGrid, middles: FloatArray, least: FloatArray, parentSlots: IntArray, blends: FloatArray): LongArray {
        val slots = parentSlots.size
        val swing = swing(grid)
        val masks = LongArray(slots)
        for (sub in 0 until SUBS) {
            for (s in 0 until slots) {
                if (middles[s * SUBS + sub] - swing <= least[sub] + swing + BLEND_TIMES * blends[s]) masks[s] = masks[s] or (1L shl sub)
            }
        }
        closeOverParents(masks, parentSlots)
        return masks
    }

    /**
     * Which sub-blocks each member can hold some of the surface's shares in, as [surfaceShares] reckons them: its field at the middle
     * within its widest blend, [reaches], and the shares' band of the least there, both grown by as far as they can change.
     */
    fun shareRelevance(grid: BlockGrid, middles: FloatArray, least: FloatArray, parentSlots: IntArray, reaches: FloatArray): LongArray {
        val slots = parentSlots.size
        val swing = swing(grid) + LIPSCHITZ * DRIFT_CELLS * grid.cell
        val band = BAND_CELLS * grid.cell
        val masks = LongArray(slots)
        for (sub in 0 until SUBS) {
            for (s in 0 until slots) {
                if (middles[s * SUBS + sub] - swing <= least[sub] + swing + reaches[s] + band) masks[s] = masks[s] or (1L shl sub)
            }
        }
        closeOverParents(masks, parentSlots)
        return masks
    }

    /**
     * Each of [slots] members' field at each sub-block's middle of [block], [SUBS] values a member, read only where neither the bounds
     * its field at the block's middle, [centres], gives nor those its field at the middle of the sub-block's octant gives can show it
     * leaves the union unchanged there, and the bound below elsewhere, which
     * can only widen where it is held to share the surface; [kept] gives values already read and where, and the octants' middles where
     * they were read. Which values were read goes into the block's [SurfaceBlock.middlesRead] and the octants' into
     * [SurfaceBlock.octants]. Beside them, the most the least of the members' fields can be at each sub-block's middle.
     */
    suspend fun middles(
        ask: FieldAsk,
        relevant: List<Int>,
        grid: BlockGrid,
        block: SurfaceBlock,
        slots: Int,
        centres: FloatArray,
        kept: (Int) -> KeptMiddles?,
    ): Pair<FloatArray, FloatArray> {
        val out = FloatArray(slots * SUBS)
        val octantsRead = FloatArray(slots * OCTANTS) { Float.NaN }
        val read = LongArray(slots)
        val upper = FloatArray(SUBS) { Float.MAX_VALUE }
        val held = Array(slots) { kept(it) }
        val near = LIPSCHITZ * sqrt(3f) * grid.cell
        val octants = FloatArray(OCTANTS)
        val margin = LIPSCHITZ * grid.halfDiagonal
        val leastCentre = centres.minOrNull() ?: 0f
        val octantsWanted = BooleanArray(slots)
        for (s in 0 until slots) {
            val k = held[s]
            if (k != null) read[s] = k.read
            if (read[s] == 0L && centres[s] - margin > leastCentre + margin + BLEND_TIMES * block.blends[s]) {
                for (sub in 0 until SUBS) {
                    val d = LIPSCHITZ * fromMiddle(grid, sub)
                    out[s * SUBS + sub] = centres[s] - d
                    upper[sub] = minOf(upper[sub], centres[s] + d)
                }
                continue
            }
            if (read[s] == ALL) {
                k!!.values.copyInto(out, s * SUBS)
                for (sub in 0 until SUBS) upper[sub] = minOf(upper[sub], out[s * SUBS + sub])
                continue
            }
            octantsWanted[s] = true
        }
        val octantAsked = IntArray(slots * OCTANTS) { -1 }
        for (s in 0 until slots) if (octantsWanted[s]) {
            val known = held[s]?.octants
            for (o in 0 until OCTANTS) {
                if (known == null || known[o].isNaN()) octantAsked[s * OCTANTS + o] = ask.own(relevant[s], octantMiddle(grid, block, o))
            }
        }
        ask.answered()
        for (s in 0 until slots) if (octantsWanted[s]) {
            val k = held[s]
            val known = k?.octants
            for (o in 0 until OCTANTS) {
                octants[o] = if (known != null && !known[o].isNaN()) known[o] else ask.field(octantAsked[s * OCTANTS + o])
                octantsRead[s * OCTANTS + o] = octants[o]
            }
            for (sub in 0 until SUBS) {
                val exact = (read[s] shr sub) and 1L == 1L
                val octant = octants[octantOf(sub)]
                out[s * SUBS + sub] = if (exact) k!!.values[sub] else octant - near
                upper[sub] = minOf(upper[sub], if (exact) out[s * SUBS + sub] else octant + near)
            }
        }
        val swing = swing(grid)
        val ahead = IntArray(slots * SUBS) { -1 }
        if (ask.gathers) {
            for (s in 0 until slots) {
                val reach = BLEND_TIMES * block.blends[s]
                for (sub in 0 until SUBS) {
                    if ((read[s] shr sub) and 1L == 1L) continue
                    if (out[s * SUBS + sub] - swing > upper[sub] + swing + reach) continue
                    ahead[s * SUBS + sub] = ask.own(relevant[s], middle(grid, block.i0, block.j0, block.k0, sub))
                }
            }
            ask.answered()
        }
        for (s in 0 until slots) {
            val reach = BLEND_TIMES * block.blends[s]
            for (sub in 0 until SUBS) {
                if ((read[s] shr sub) and 1L == 1L) continue
                if (out[s * SUBS + sub] - swing > upper[sub] + swing + reach) continue
                out[s * SUBS + sub] = if (ahead[s * SUBS + sub] >= 0) {
                    ask.field(ahead[s * SUBS + sub])
                } else {
                    val i = ask.own(relevant[s], middle(grid, block.i0, block.j0, block.k0, sub))
                    ask.answered()
                    ask.field(i)
                }
                read[s] = read[s] or (1L shl sub)
                upper[sub] = minOf(upper[sub], out[s * SUBS + sub])
            }
        }
        block.middlesRead = read
        block.octants = octantsRead
        return out to upper
    }

    /** A member's sub-block middles already read, [values] where [read] says, and its octants' middles, NaN where not read. */
    class KeptMiddles(val values: FloatArray, val read: Long, val octants: FloatArray?)

    /** How far sub-block [sub]'s middle lies from its block's middle. */
    private fun fromMiddle(grid: BlockGrid, sub: Int): Float {
        val half = SUB * 0.5f
        val dx = sub % SUBS_SIDE * SUB + half - BLOCK / 2
        val dy = (sub / SUBS_SIDE) % SUBS_SIDE * SUB + half - BLOCK / 2
        val dz = sub / (SUBS_SIDE * SUBS_SIDE) * SUB + half - BLOCK / 2
        return sqrt(dx * dx + dy * dy + dz * dz) * grid.cell
    }

    /** The octant of a block, of four cells a side, that sub-block [sub] lies in. */
    private fun octantOf(sub: Int): Int {
        val a = sub % SUBS_SIDE
        val b = (sub / SUBS_SIDE) % SUBS_SIDE
        val c = sub / (SUBS_SIDE * SUBS_SIDE)
        return a / 2 + 2 * (b / 2) + 4 * (c / 2)
    }

    /** The middle of [block]'s octant [o], a grid point a cell's diagonal from each of its sub-blocks' middles. */
    private fun octantMiddle(grid: BlockGrid, block: SurfaceBlock, o: Int): Vec3 =
        grid.point(block.i0 + BLOCK / 4 + (o and 1) * BLOCK / 2, block.j0 + BLOCK / 4 + ((o shr 1) and 1) * BLOCK / 2, block.k0 + BLOCK / 4 + ((o shr 2) and 1) * BLOCK / 2)

    private fun closeOverParents(masks: LongArray, parentSlots: IntArray) {
        var changed = true
        while (changed) {
            changed = false
            for (s in masks.indices) {
                val parent = parentSlots[s]
                if (parent < 0) continue
                val joined = masks[parent] or masks[s]
                if (joined != masks[parent]) {
                    masks[parent] = joined
                    changed = true
                }
            }
        }
    }

    /**
     * The sub-blocks whose middle lies so far from the surface, by the union of [middles] over the members [parentSlots] and [blends]
     * describe, that the union cannot change sign anywhere in them, whatever share of its blend each member [angled] takes there.
     */
    fun far(grid: BlockGrid, middles: FloatArray, parentSlots: IntArray, blends: FloatArray, angled: BooleanArray): Long {
        val slots = parentSlots.size
        val margin = swing(grid)
        var mask = 0L
        for (sub in 0 until SUBS) {
            var least = Float.MAX_VALUE
            var most = Float.MAX_VALUE
            for (s in 0 until slots) {
                val parent = parentSlots[s]
                val own = middles[s * SUBS + sub]
                val term = if (parent >= 0 && blends[s] > 0f) smoothMin(middles[parent * SUBS + sub], own, blends[s]) else own
                if (term < least) least = term
                val upper = if (angled[s]) own else term
                if (upper < most) most = upper
            }
            if (clear(least, most, margin)) mask = mask or (1L shl sub)
        }
        return mask
    }

    /**
     * Asks for member [m]'s field at the grid points of block ([i0], [j0], [k0]) that a sub-block in [exact] holds, unless [from] is given
     * and a sub-block in [kept] holds the point; the index of each point's answer, -1 where none is asked.
     */
    fun askChannel(ask: FieldAsk, m: Int, grid: BlockGrid, i0: Int, j0: Int, k0: Int, exact: Long, from: FloatArray?, kept: Long): IntArray {
        val asked = IntArray(BLOCK_CORNERS) { -1 }
        val held = if (from != null) kept else 0L
        for (k in 0..BLOCK) for (j in 0..BLOCK) for (i in 0..BLOCK) {
            val corner = BlockGrid.corner(i, j, k)
            val subs = cornerSubs[corner]
            if (subs and held == 0L && subs and exact != 0L) asked[corner] = ask.own(m, grid.point(i0 + i, j0 + j, k0 + k))
        }
        return asked
    }

    /**
     * A member's field at a block's grid points: [from]'s where a sub-block in [kept] holds the point; the answer [ask] holds where
     * [asked] (see [askChannel]) gives one; where a sub-block not in [far] holds it, the bound its field at that sub-block's middle gives,
     * from [middles]; and elsewhere, where only the union's sign matters, its field at the middle of a sub-block holding it.
     */
    fun channel(ask: FieldAsk, asked: IntArray, far: Long, middles: FloatArray, from: FloatArray?, kept: Long, grid: BlockGrid): FloatArray {
        val out = FloatArray(BLOCK_CORNERS)
        val held = if (from != null) kept else 0L
        val half = SUB * 0.5f
        for (k in 0..BLOCK) for (j in 0..BLOCK) for (i in 0..BLOCK) {
            val corner = BlockGrid.corner(i, j, k)
            val subs = cornerSubs[corner]
            out[corner] = when {
                subs and held != 0L -> from!![corner]
                asked[corner] >= 0 -> ask.field(asked[corner])
                subs and far.inv() != 0L -> {
                    val sub = (subs and far.inv()).countTrailingZeroBits()
                    val di = (i - (sub % SUBS_SIDE * SUB + half)) * grid.cell
                    val dj = (j - ((sub / SUBS_SIDE) % SUBS_SIDE * SUB + half)) * grid.cell
                    val dk = (k - (sub / (SUBS_SIDE * SUBS_SIDE) * SUB + half)) * grid.cell
                    middles[sub] - LIPSCHITZ * sqrt(di * di + dj * dj + dk * dk)
                }
                else -> middles[subs.countTrailingZeroBits()]
            }
        }
        return out
    }
}
