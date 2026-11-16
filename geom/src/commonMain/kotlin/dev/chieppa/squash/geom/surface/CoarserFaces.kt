package dev.chieppa.squash.geom.surface

/**
 * The grid points a block shares with blocks meshed at twice its cell. There the field is taken as the trilinear blend of the coarser
 * grid's points, the block's own even grid points, so the cells either side of the boundary see the same signs along it: a crossing on a
 * finer edge of the boundary lies on a crossed face or edge of the coarser cell beside it, and that cell holds a vertex for it.
 */
internal object CoarserFaces {
    /** Each grid point of a block, as the bits of [MeshRegion.besideBit] of the blocks round it it is shared with. */
    private val sharedWith = IntArray(BLOCK_CORNERS).also { table ->
        for (k in 0..BLOCK) for (j in 0..BLOCK) for (i in 0..BLOCK) {
            var bits = 0
            for (dz in sides(k)) for (dy in sides(j)) for (dx in sides(i)) {
                if (dx != 0 || dy != 0 || dz != 0) bits = bits or (1 shl MeshRegion.besideBit(dx, dy, dz))
            }
            table[BlockGrid.corner(i, j, k)] = bits
        }
    }

    private fun sides(at: Int): IntArray = when (at) {
        0 -> intArrayOf(-1, 0)
        BLOCK -> intArrayOf(0, 1)
        else -> intArrayOf(0)
    }

    /** Whether grid point ([i], [j], [k]) of a block is shared with one of the blocks [coarser] names. */
    fun shared(i: Int, j: Int, k: Int, coarser: Int): Boolean = sharedWith[BlockGrid.corner(i, j, k)] and coarser != 0

    /** Replaces the values in [corners] at every grid point shared with a block [coarser] names by the blend of the even grid points round it. */
    fun blend(corners: FloatArray, coarser: Int) {
        for (k in 0..BLOCK) for (j in 0..BLOCK) for (i in 0..BLOCK) {
            if ((i or j or k) and 1 == 0) continue
            val c = BlockGrid.corner(i, j, k)
            if (sharedWith[c] and coarser == 0) continue
            var sum = 0f
            var weight = 0f
            for (dk in steps(k)) for (dj in steps(j)) for (di in steps(i)) {
                sum += corners[BlockGrid.corner(i + di, j + dj, k + dk)]
                weight += 1f
            }
            corners[c] = sum / weight
        }
    }

    private val ODD = intArrayOf(-1, 1)
    private val EVEN = intArrayOf(0)

    private fun steps(at: Int): IntArray = if (at and 1 == 1) ODD else EVEN
}
