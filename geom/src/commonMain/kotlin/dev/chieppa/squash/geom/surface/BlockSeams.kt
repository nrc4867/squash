package dev.chieppa.squash.geom.surface

/**
 * Where neighbouring blocks disagree about the surface. Two blocks hold the grid points of the face, edge or corner they share each in
 * its own channels, read exactly or stood in for by bounds that only hold while a field changes no faster than [LIPSCHITZ] allows; where
 * one side's stand-in has the wrong sign, the cells either side cross different edges and the quads across them cannot be made, so the
 * surface would open there.
 */
internal object BlockSeams {
    /** The blocks of [map] that disagree in sign with a neighbour of theirs in [map] at a grid point they share, among [keys] and their neighbours. */
    fun disagreeing(grid: BlockGrid, map: Map<Long, SurfaceBlock>, keys: Collection<Long>): Set<Long> {
        val out = HashSet<Long>()
        for (key in keys) {
            val block = map[key] ?: continue
            for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0 && dz == 0) continue
                val otherKey = grid.key(block.bx + dx, block.by + dy, block.bz + dz)
                val other = map[otherKey] ?: continue
                if (differs(block, other, dx, dy, dz)) {
                    out += key
                    out += otherKey
                }
            }
        }
        return out
    }

    /** Whether [block] and [other], [dx], [dy], [dz] blocks from it, hold a shared grid point on opposite sides of the surface. */
    private fun differs(block: SurfaceBlock, other: SurfaceBlock, dx: Int, dy: Int, dz: Int): Boolean {
        val xs = shared(dx)
        val ys = shared(dy)
        val zs = shared(dz)
        for (k in zs) for (j in ys) for (i in xs) {
            val here = block.corners[BlockGrid.corner(i, j, k)] < 0f
            val there = other.corners[BlockGrid.corner(i - dx * BLOCK, j - dy * BLOCK, k - dz * BLOCK)] < 0f
            if (here != there) return true
        }
        return false
    }

    /** The places along one axis, in the block's own grid points, it shares with a neighbour [d] blocks along it. */
    private fun shared(d: Int): IntRange = when (d) {
        -1 -> 0..0
        1 -> BLOCK..BLOCK
        else -> 0..BLOCK
    }
}
