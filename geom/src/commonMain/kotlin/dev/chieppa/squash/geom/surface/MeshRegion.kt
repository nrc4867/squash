package dev.chieppa.squash.geom.surface

/**
 * Which cells of a grid one mesher meshes: those of the blocks of [inside], or of every block when it is null, less the octants of a
 * block that a mesher at half the cell meshes instead, each the block of that mesher's grid [finer] names. A block of [inside] beside
 * one outside it borders a coarser mesher's blocks.
 */
class MeshRegion internal constructor(internal val inside: Set<Long>?, internal val finer: Set<Long>) {
    /** Whether the mesher meshes any cell of block [key] of [grid]. */
    internal fun meshes(grid: BlockGrid, key: Long): Boolean = (inside == null || key in inside) && excluded(grid, key) != ALL_OCTANTS

    /** Which octants of block [key] of [grid] the finer mesher meshes instead, as bits of [SurfaceBlock.octantOf]. */
    internal fun excluded(grid: BlockGrid, key: Long): Int {
        if (finer.isEmpty()) return 0
        val bx = grid.bx(key) * 2
        val by = grid.by(key) * 2
        val bz = grid.bz(key) * 2
        var bits = 0
        for (dz in 0..1) for (dy in 0..1) for (dx in 0..1) {
            if (finerKey(bx + dx, by + dy, bz + dz) in finer) bits = bits or (1 shl (dx + 2 * dy + 4 * dz))
        }
        return bits
    }

    private fun finerKey(bx: Int, by: Int, bz: Int): Long = FINER_KEYS.key(bx, by, bz)

    /** Which of the 26 blocks round block ([bx], [by], [bz]) lie outside [inside], as bits of [besideBit]. */
    internal fun coarserBeside(grid: BlockGrid, bx: Int, by: Int, bz: Int): Int {
        val within = inside ?: return 0
        var bits = 0
        for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) {
            if ((dx != 0 || dy != 0 || dz != 0) && grid.key(bx + dx, by + dy, bz + dz) !in within) bits = bits or (1 shl besideBit(dx, dy, dz))
        }
        return bits
    }

    /**
     * The blocks whose meshing differs between [before] and this region: those holding a finer block one has and the other not, and
     * those beside a block that entered or left [inside], whose coarser neighbours differ.
     */
    internal fun changedFrom(before: MeshRegion, grid: BlockGrid): Set<Long> {
        val out = HashSet<Long>()
        val parent = { key: Long -> grid.key(FINER_KEYS.bx(key) shr 1, FINER_KEYS.by(key) shr 1, FINER_KEYS.bz(key) shr 1) }
        for (key in before.finer) if (key !in finer) out += parent(key)
        for (key in finer) if (key !in before.finer) out += parent(key)
        val was = before.inside
        val now = inside
        if (was == now) return out
        val moved = HashSet<Long>()
        if (was != null) for (key in was) if (now == null || key !in now) moved += key
        if (now != null) for (key in now) if (was == null || key !in was) moved += key
        for (key in moved) {
            val bx = grid.bx(key)
            val by = grid.by(key)
            val bz = grid.bz(key)
            for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) out += grid.key(bx + dx, by + dy, bz + dz)
        }
        return out
    }

    override fun equals(other: Any?): Boolean = other is MeshRegion && other.inside == inside && other.finer == finer

    override fun hashCode(): Int = (inside?.hashCode() ?: 0) * 31 + finer.hashCode()

    companion object {
        /** Every block. */
        val ALL: MeshRegion = MeshRegion(null, emptySet())

        /** Every octant of a block. */
        internal const val ALL_OCTANTS = 0xFF

        /** Packs and unpacks block places into keys; the same for every cell, so any grid's does. */
        internal val FINER_KEYS = BlockGrid(1f)

        /** The bit naming the block [dx], [dy], [dz] (each -1, 0 or 1) from another. */
        internal fun besideBit(dx: Int, dy: Int, dz: Int): Int = (dx + 1) + 3 * (dy + 1) + 9 * (dz + 1)
    }
}
