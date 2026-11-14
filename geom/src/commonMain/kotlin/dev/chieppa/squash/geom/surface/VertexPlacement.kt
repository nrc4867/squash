package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.PLACE_ARRAY
import dev.chieppa.squash.geom.field.PLACE_FIELD
import dev.chieppa.squash.geom.field.PLACE_U
import dev.chieppa.squash.geom.field.PLACE_V
import dev.chieppa.squash.geom.field.smoothMin
import kotlin.math.abs

/** How far one step of projection may move a vertex, in cells. */
private const val MOST_STEP_CELLS = 1.5f

private const val TINY = 1e-12f

/** How near the surface, as a share of a cell, a vertex is placed; and how far off it may lie before the field's own gradient is read. */
private const val DONE_SHARE = 1e-3f
private const val CLOSE_SHARE = 0.02f

/** The step the field's own gradient is read across, as a share of a cell. */
private const val GRADIENT_STEP_SHARE = 0.05f

/** How far past its widest blend a member's term of the union may lie from the union and still hold a share, in cells. */
private const val SHARE_BAND_CELLS = 2f

/** Steps along a line after the first two reads. */
private const val LINE_STEPS = 4

/**
 * Places vertices on the surface: from where relaxation leaves a vertex, a step along the trilinear gradient of its cell by the joined
 * field read there, a false-position step between the two reads when they bracket the surface, and the members' own fields and places on
 * their runs read where it lands. Only the members that can change the union near the vertex's sub-block are read for the field, and those
 * that can hold a share there for its coordinates.
 */
internal class VertexPlacement(private val grid: BlockGrid, private val field: JoinedField) {
    /** Places vertex [v] of [block] from [start] and writes its position, normal and coordinates into the block. */
    fun place(block: SurfaceBlock, v: Int, start: Vec3) {
        val slots = block.keys.size
        val owns = Probe(slots)
        val gradient = FloatArray(4)
        val cell = block.vertexCell[v]
        val read = readFor(block, cell)
        val held = heldFor(block, cell, read)
        block.starts[v * 3] = start.x
        block.starts[v * 3 + 1] = start.y
        block.starts[v * 3 + 2] = start.z
        block.trilinear(grid, cell, start, gradient)
        val g = Vec3(gradient[0], gradient[1], gradient[2])
        val length = g.length
        var at = start
        if (length > TINY && slots > 0) {
            val off = FloatArray(1)
            at = alongLine(block, read, start, g * (1f / length), owns, off)
            if (off[0] > grid.cell * CLOSE_SHARE) {
                val step = grid.cell * GRADIENT_STEP_SHARE
                val sampled = Vec3(
                    joined(block, read, at + Vec3(step, 0f, 0f), owns) - joined(block, read, at - Vec3(step, 0f, 0f), owns),
                    joined(block, read, at + Vec3(0f, step, 0f), owns) - joined(block, read, at - Vec3(0f, step, 0f), owns),
                    joined(block, read, at + Vec3(0f, 0f, step), owns) - joined(block, read, at - Vec3(0f, 0f, step), owns),
                )
                if (sampled.length > TINY) at = alongLine(block, read, at, sampled.normalized(), owns, off)
            }
        }
        block.positions[v * 3] = at.x
        block.positions[v * 3 + 1] = at.y
        block.positions[v * 3 + 2] = at.z
        block.trilinear(grid, cell, at, gradient)
        val normal = Vec3(gradient[0], gradient[1], gradient[2]).let { if (it.length > TINY) it.normalized() else n0(start, at) }
        block.normals[v * 3] = normal.x
        block.normals[v * 3 + 1] = normal.y
        block.normals[v * 3 + 2] = normal.z
        coordinate(block, read, held, v, at, owns)

    }

    /**
     * The point on the line from [from] along [n] where the joined field is zero, by secant steps until two reads bracket it and false
     * position between them after, no step longer than [MOST_STEP_CELLS] cells; the nearest to zero it read when it finds none. How far
     * from zero the field lies there goes into [off].
     */
    private fun alongLine(block: SurfaceBlock, read: BooleanArray, from: Vec3, n: Vec3, owns: Probe, off: FloatArray): Vec3 {
        val most = grid.cell * MOST_STEP_CELLS
        val done = grid.cell * DONE_SHARE
        var t0 = 0f
        var f0 = joined(block, read, from, owns)
        owns.keep(from)
        off[0] = abs(f0)
        if (abs(f0) <= done) return from
        var t1 = (-f0).coerceIn(-most, most)
        val p1 = from + n * t1
        var f1 = joined(block, read, p1, owns)
        if (abs(f1) < abs(f0)) owns.keep(p1)
        var bestT = if (abs(f1) < abs(f0)) t1 else t0
        var bestF = minOf(abs(f0), abs(f1))
        for (step in 0 until LINE_STEPS) {
            if (bestF <= done || f1 == f0) break
            val bracketed = (f0 < 0f) != (f1 < 0f)
            var t2 = t1 - f1 * (t1 - t0) / (f1 - f0)
            if (!bracketed) t2 = t2.coerceIn(t1 - most, t1 + most)
            val p2 = from + n * t2
            val f2 = joined(block, read, p2, owns)
            if (abs(f2) < bestF) {
                bestF = abs(f2)
                bestT = t2
                owns.keep(p2)
            }
            if (bracketed && (f2 < 0f) == (f0 < 0f)) {
                t0 = t2
                f0 = f2
            } else {
                t0 = t1
                f0 = f1
                t1 = t2
                f1 = f2
            }
        }
        off[0] = bestF
        return from + n * bestT
    }

    private fun n0(start: Vec3, at: Vec3): Vec3 = (at - start).let { if (it.length > TINY) it.normalized() else Vec3.Y }

    /** The joined field at [p] from the members [block] holds, their own fields and places left in [owns]. */
    private fun joined(block: SurfaceBlock, read: BooleanArray, p: Vec3, owns: Probe): Float {
        for (s in read.indices) {
            val m = block.members[s]
            if (m >= 0 && read[s]) {
                field.members[m].place(p, owns.out)
                owns.owns[s] = owns.out[PLACE_FIELD]
                owns.us[s] = owns.out[PLACE_U]
                owns.vs[s] = owns.out[PLACE_V]
            } else {
                owns.owns[s] = Float.MAX_VALUE
            }
        }
        var joined = Float.MAX_VALUE
        for (s in read.indices) {
            val parent = block.parentSlots[s]
            val term = if (parent >= 0 && block.blends[s] > 0f) smoothMin(owns.owns[parent], owns.owns[s], block.blends[s]) else owns.owns[s]
            if (term < joined) joined = term
        }
        return joined
    }

    /**
     * Reads every member [block] holds at [p] with its place, taking those [placed] read for the field from [probe] where it last kept
     * them at [p], and writes vertex [v]'s owner, its shares and places.
     */
    private fun coordinate(block: SurfaceBlock, placed: BooleanArray, read: BooleanArray, v: Int, p: Vec3, probe: Probe) {
        val slots = block.keys.size
        val owns = FloatArray(slots)
        val us = FloatArray(slots)
        val vs = FloatArray(slots)
        val out = FloatArray(PLACE_ARRAY)
        val fromProbe = probe.keptAt == p
        for (s in 0 until slots) {
            val m = block.members[s]
            if (m < 0 || !read[s]) {
                owns[s] = Float.MAX_VALUE
                continue
            }
            if (fromProbe && placed[s]) {
                owns[s] = probe.bestOwns[s]
                us[s] = probe.bestUs[s]
                vs[s] = probe.bestVs[s]
                continue
            }
            field.members[m].place(p, out)
            owns[s] = out[PLACE_FIELD]
            us[s] = out[PLACE_U]
            vs[s] = out[PLACE_V]
        }
        var winner = -1
        var least = Float.MAX_VALUE
        for (s in 0 until slots) {
            val parent = block.parentSlots[s]
            val term = if (parent >= 0 && block.blends[s] > 0f) smoothMin(owns[parent], owns[s], block.blends[s]) else owns[s]
            if (term < least) {
                least = term
                winner = s
            }
        }
        val c = block.coordinates
        c.winners[v] = winner
        if (winner < 0) return
        c.winnerOwn[v] = owns[winner]
        val parent = block.parentSlots[winner]
        if (parent >= 0 && block.blends[winner] > 0f) {
            c.parents[v] = parent
            c.shares[v] = (0.5f + 0.5f * (owns[parent] - owns[winner]) / block.blends[winner]).coerceIn(0f, 1f)
            c.parentOwn[v] = owns[parent]
        } else {
            c.parents[v] = -1
            c.shares[v] = 1f
        }
        val shares = FloatArray(slots)
        surfaceShares(owns, block.parentSlots, block.blends, block.reaches, grid.cell * SHARE_BAND_CELLS, shares)
        val order = heaviest(shares)
        var kept = 0f
        for (k in 0 until minOf(HELD, slots)) kept += shares[order[k]]
        for (k in 0 until HELD) {
            val at = v * HELD + k
            val s = order[k]
            if (s < 0 || shares[s] <= 0f) {
                c.held[at] = -1
                c.heldShares[at] = 0f
                continue
            }
            c.held[at] = s
            c.heldShares[at] = shares[s] / kept
            c.heldU[at] = us[s]
            c.heldV[at] = vs[s]
            c.heldOwn[at] = owns[s]
        }
    }

    /**
     * The [HELD] slots of [shares] with the largest shares, largest first, the lower slot first between equal shares, as a stable sort
     * by descending share orders them; -1 past the last slot.
     */
    private fun heaviest(shares: FloatArray): IntArray {
        val order = IntArray(HELD) { -1 }
        for (k in 0 until minOf(HELD, shares.size)) {
            var best = -1
            for (s in shares.indices) {
                var taken = false
                for (j in 0 until k) if (order[j] == s) taken = true
                if (!taken && (best < 0 || shares[s].compareTo(shares[best]) > 0)) best = s
            }
            order[k] = best
        }
        return order
    }

    companion object {
        /** Which of [block]'s members are read for the field placing a vertex of [cell]. */
        fun readFor(block: SurfaceBlock, cell: Int): BooleanArray {
            val near = SubBlocks.around(SubBlocks.ofCell(cell))
            return BooleanArray(block.keys.size) { block.relevance[it] and near != 0L }
        }

        /** Which of [block]'s members are read for the coordinates of a vertex of [cell], those [read] for its field among them. */
        fun heldFor(block: SurfaceBlock, cell: Int, read: BooleanArray): BooleanArray {
            val sub = SubBlocks.ofCell(cell)
            return BooleanArray(block.keys.size) { read[it] || (block.shareRelevance[it] shr sub) and 1L == 1L }
        }

        /** Where a vertex lies after relaxing: half way to the mean of [neighbours] from [own], kept inside its cell from [low]. */
        fun relaxed(own: Vec3, neighbours: Vec3?, low: Vec3, cell: Float): Vec3 {
            val moved = if (neighbours == null) own else (own + neighbours) * 0.5f
            return Vec3(
                moved.x.coerceIn(low.x, low.x + cell),
                moved.y.coerceIn(low.y, low.y + cell),
                moved.z.coerceIn(low.z, low.z + cell),
            )
        }
    }
}

/** The members' own fields and places at the point last read, and at the point last kept, with [keep]. */
private class Probe(slots: Int) {
    val out = FloatArray(PLACE_ARRAY)
    val owns = FloatArray(slots)
    val us = FloatArray(slots)
    val vs = FloatArray(slots)
    val bestOwns = FloatArray(slots)
    val bestUs = FloatArray(slots)
    val bestVs = FloatArray(slots)
    var keptAt: Vec3? = null

    /** Keeps the values last read, which were read at [p]. */
    fun keep(p: Vec3) {
        owns.copyInto(bestOwns)
        us.copyInto(bestUs)
        vs.copyInto(bestVs)
        keptAt = p
    }
}
