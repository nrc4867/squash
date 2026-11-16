package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.collections.IntList

/**
 * The faces joining a surface meshed at one cell to the surface meshed at half that cell across the boundary of the finer region, as
 * dual contouring joins an octree's cells of two sizes (Ju, Losasso, Schaefer and Warren): one face across each finer grid edge of the
 * boundary the surface crosses, joining the vertices of the four cells round it, finer or coarser, those of a coarser cell met twice
 * taken once, so the face is a triangle where the edge lies inside a coarser cell's face and a quad where it lies along a coarser edge.
 * A coarser cell gives the vertex of the sheet the crossing lies on: its edge's where the finer edge is half of it, and on a face crossed
 * four times the sheet of the corner whose quarter round the face's saddle holds the crossing's lone side, as [CellTables] pairs them.
 */
internal class LevelSeams(
    private val coarse: Side,
    private val fine: Side,
    private val fineRegion: MeshRegion,
    private val positions: FloatArray,
) {
    /** One level's blocks as meshed, its mesh's block order and where its vertices start in the joined mesh. */
    class Side(val grid: BlockGrid, val blocks: Map<Long, SurfaceBlock>, val mesh: SurfaceMesh, val base: Int) {
        /** Vertex [sheet] of [cell] of block [key] in the joined mesh, or -1 where the block holds no such vertex. */
        fun vertex(key: Long, cell: Int, sheet: Int): Int {
            val block = blocks[key] ?: return -1
            val first = block.cellFirst[cell]
            if (first < 0 || sheet < 0) return -1
            val at = mesh.blockKeys.binarySearch(key)
            if (at < 0) return -1
            return base + mesh.blockStarts[at] + first + sheet
        }
    }

    /** Crossed boundary edges whose faces could not be made: a cell round them held no vertex for the crossing. */
    var missed = 0
        private set

    /** The faces facing out: the quads as pairs of triangles, corners in order at each pair's indices 0, 1, 4 and 5, three vertices a triangle. */
    val quads = IntList()

    /** The faces facing out that are triangles, three vertices each. */
    val triangles = IntList()

    /** Finds the faces into [quads] and [triangles]. */
    fun join() {
        val out = triangles
        val seen = HashSet<Long>()
        val point = IntArray(3)
        val global = IntArray(3)
        for ((key, block) in fine.blocks) {
            if (block.coarser == 0) continue
            val origin = intArrayOf(block.i0, block.j0, block.k0)
            for (k in 0..BLOCK) for (j in 0..BLOCK) for (i in 0..BLOCK) {
                if (!CoarserFaces.shared(i, j, k, block.coarser)) continue
                point[0] = i
                point[1] = j
                point[2] = k
                for (axis in 0 until 3) {
                    if (point[axis] == BLOCK) continue
                    point[axis]++
                    val endShared = CoarserFaces.shared(point[0], point[1], point[2], block.coarser)
                    val there = block.corners[BlockGrid.corner(point[0], point[1], point[2])]
                    point[axis]--
                    if (!endShared) continue
                    val here = block.corners[BlockGrid.corner(i, j, k)]
                    if ((here < 0f) == (there < 0f)) continue
                    for (c in 0 until 3) global[c] = origin[c] + point[c]
                    if (!seen.add(edgeKey(global, axis))) continue
                    face(global, axis, here, out)
                }
            }
        }
    }

    private fun edgeKey(g: IntArray, axis: Int): Long =
        ((g[0].toLong() and 0xFFFFF) shl 42) or ((g[1].toLong() and 0xFFFFF) shl 22) or ((g[2].toLong() and 0xFFFFF) shl 2) or axis.toLong()

    /** The face across the finer edge from grid point [g] along [axis], whose value at [g] is [here], added to [out]. */
    private fun face(g: IntArray, axis: Int, here: Float, out: IntList) {
        val o1 = (axis + 1) % 3
        val o2 = (axis + 2) % 3
        val ring = IntArray(4)
        var coarser = 0
        val cellAt = IntArray(3)
        for (q in 0 until 4) {
            val b = if (q == 1 || q == 2) 0 else -1
            val c = if (q >= 2) 0 else -1
            g.copyInto(cellAt)
            cellAt[o1] += b
            cellAt[o2] += c
            val fineKey = fine.grid.key(cellAt[0] shr 3, cellAt[1] shr 3, cellAt[2] shr 3)
            ring[q] = when {
                fineRegion.inside?.contains(fineKey) == true -> {
                    val octant = SurfaceBlock.octantOf(cellAt[0] and (BLOCK - 1), cellAt[1] and (BLOCK - 1), cellAt[2] and (BLOCK - 1))
                    if (fineRegion.excluded(fine.grid, fineKey) shr octant and 1 == 1) return
                    fineVertex(fineKey, cellAt, g, axis)
                }
                else -> {
                    coarser++
                    coarseVertex(cellAt, g, axis, here)
                }
            }
        }
        if (coarser == 0) return
        if (ring.any { it < 0 }) {
            missed++
            return
        }
        val outward = here < 0f
        val ordered = if (outward) ring else intArrayOf(ring[0], ring[3], ring[2], ring[1])
        val distinct = IntArray(4)
        var n = 0
        for (q in 0 until 4) {
            val v = ordered[q]
            if (n > 0 && distinct[n - 1] == v) continue
            distinct[n++] = v
        }
        if (n > 1 && distinct[n - 1] == distinct[0]) n--
        when (n) {
            3 -> for (q in 0 until 3) out.add(distinct[q])
            4 -> {
                val (a, b, c, d) = distinct
                val q = quads
                if (apart(a, c) <= apart(b, d)) {
                    q.add(a); q.add(b); q.add(c)
                    q.add(a); q.add(c); q.add(d)
                } else {
                    q.add(a); q.add(b); q.add(d)
                    q.add(b); q.add(c); q.add(d)
                }
            }
        }
    }

    private fun apart(a: Int, b: Int): Float {
        val dx = positions[a * 3] - positions[b * 3]
        val dy = positions[a * 3 + 1] - positions[b * 3 + 1]
        val dz = positions[a * 3 + 2] - positions[b * 3 + 2]
        return dx * dx + dy * dy + dz * dz
    }

    /** The vertex of finer cell [cellAt] on the crossing of the edge from [g] along [axis]. */
    private fun fineVertex(key: Long, cellAt: IntArray, g: IntArray, axis: Int): Int {
        val block = fine.blocks[key] ?: return -1
        val local = BlockGrid.cell(cellAt[0] - block.i0, cellAt[1] - block.j0, cellAt[2] - block.k0)
        val edge = edgeOf(axis, IntArray(3) { g[it] - cellAt[it] })
        return fine.vertex(key, local, block.edgeSheets[local * CellTables.EDGES + edge].toInt())
    }

    /** The vertex of the coarser cell holding finer cell [cellAt], on the sheet the crossing of the finer edge from [g] along [axis] lies on. */
    private fun coarseVertex(cellAt: IntArray, g: IntArray, axis: Int, here: Float): Int {
        val cc = IntArray(3) { cellAt[it] shr 1 }
        val key = coarse.grid.key(cc[0] shr 3, cc[1] shr 3, cc[2] shr 3)
        val block = coarse.blocks[key] ?: return -1
        val ci = cc[0] - block.i0
        val cj = cc[1] - block.j0
        val ck = cc[2] - block.k0
        val cell = BlockGrid.cell(ci, cj, ck)
        val at = IntArray(3) { g[it] - cc[it] * 2 }
        val o1 = (axis + 1) % 3
        val o2 = (axis + 2) % 3
        val sheet = if (at[o1] and 1 == 0 && at[o2] and 1 == 0) {
            val bits = IntArray(3)
            bits[o1] = at[o1] / 2
            bits[o2] = at[o2] / 2
            block.edgeSheets[cell * CellTables.EDGES + edgeOf(axis, bits)].toInt()
        } else {
            val side = if (at[o1] and 1 == 0) o1 else o2
            val across = if (side == o1) o2 else o1
            faceSheet(block, ci, cj, ck, cell, side, at[side] / 2, axis, across, at, here)
        }
        return coarse.vertex(key, cell, sheet)
    }

    /**
     * The sheet of coarser cell [cell] (at [ci], [cj], [ck] in its block) that a finer crossing inside its face across [side] at [sideAt]
     * lies on: the face's one sheet where it is crossed twice; where four times, the sheet of the corner on the lone side whose quarter
     * round the bilinear saddle holds the finer edge's end on that side. [at] is the finer edge's start in half cells from the cell's
     * corner, along [axis], and [across] the face's other axis.
     */
    private fun faceSheet(block: SurfaceBlock, ci: Int, cj: Int, ck: Int, cell: Int, side: Int, sideAt: Int, axis: Int, across: Int, at: IntArray, here: Float): Int {
        val values = FloatArray(4)
        val bits = IntArray(3)
        bits[side] = sideAt
        for (t in 0..1) for (s in 0..1) {
            bits[axis] = s
            bits[across] = t
            values[s + 2 * t] = block.corners[BlockGrid.corner(ci + bits[0], cj + bits[1], ck + bits[2])]
        }
        var crossings = 0
        var any = -1
        for (t in 0..1) {
            if ((values[2 * t] < 0f) != (values[1 + 2 * t] < 0f)) {
                crossings++
                any = sheetAlong(block, cell, axis, side, sideAt, across, t)
            }
        }
        for (s in 0..1) {
            if ((values[s] < 0f) != (values[s + 2] < 0f)) {
                crossings++
                any = sheetAlong(block, cell, across, side, sideAt, axis, s)
            }
        }
        if (crossings != 4) return any
        val joinInside = values.sum() < 0f
        val bS = values[1] - values[0]
        val cT = values[2] - values[0]
        val d = values[0] - values[1] - values[2] + values[3]
        val lone = if (joinInside) here >= 0f else here < 0f
        val endS = (at[axis] + if (lone) 0 else 1) * 0.5f
        val endT = at[across] * 0.5f
        val s0 = if (d != 0f) -cT / d else 0.5f
        val t0 = if (d != 0f) -bS / d else 0.5f
        var cs = if (endS > s0) 1 else 0
        var ct = if (endT > t0) 1 else 0
        if ((values[cs + 2 * ct] < 0f) == joinInside) {
            val ds = endS - cs
            val dt = endT - ct
            if (ds * ds < dt * dt) cs = 1 - cs else ct = 1 - ct
        }
        return sheetAlong(block, cell, axis, side, sideAt, across, ct)
    }

    /** The sheet of [cell]'s edge along [axis] on the face across [side] at [sideAt], at [otherAt] along [other]. */
    private fun sheetAlong(block: SurfaceBlock, cell: Int, axis: Int, side: Int, sideAt: Int, other: Int, otherAt: Int): Int {
        val bits = IntArray(3)
        bits[side] = sideAt
        bits[other] = otherAt
        return block.edgeSheets[cell * CellTables.EDGES + edgeOf(axis, bits)].toInt()
    }

    /** The edge of a cell along [axis] whose lower corner sits at [bits] (each 0 or 1) on the other two axes. */
    private fun edgeOf(axis: Int, bits: IntArray): Int = when (axis) {
        0 -> CellTables.edgeAlong(0, bits[1], bits[2])
        1 -> CellTables.edgeAlong(1, bits[0], bits[2])
        else -> CellTables.edgeAlong(2, bits[0], bits[1])
    }
}
