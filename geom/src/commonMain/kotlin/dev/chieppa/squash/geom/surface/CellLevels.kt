package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField

/** How far past a finer member's box, beyond its widest blend, its finer cells reach, in those cells. */
private const val MARGIN_CELLS = 2f

/**
 * How finely each part of the body's space is meshed: level 0 at the body's [cell], each level after it at half the cell of the one
 * before. [deeper] holds, for each level from 1 on, the blocks of that level's grid meshed at that level or finer; each lies in a block
 * of the level before's, of which it is an octant. Every block round a block of a level is meshed at the level before or finer, so cells
 * two levels apart never meet.
 */
class CellLevels internal constructor(val cell: Float, internal val deeper: List<Set<Long>>) {
    /** How many levels the space is meshed at: 1 where every block is at the body's cell. */
    val count: Int get() = deeper.size + 1

    /** Level [level]'s cell. */
    fun cellOf(level: Int): Float = cell / (1 shl level)

    /** The cells level [level]'s mesher meshes. */
    fun region(level: Int): MeshRegion = MeshRegion(if (level == 0) null else deeper[level - 1], deeper.getOrNull(level).orEmpty())

    override fun equals(other: Any?): Boolean = other is CellLevels && other.cell == cell && other.deeper == deeper

    override fun hashCode(): Int = cell.hashCode() * 31 + deeper.hashCode()

    companion object {
        /**
         * The levels [field] meshes at with its members at [levels] and level 0 at [cell]: the blocks of each member's level meeting its
         * box, grown by its widest blend and [MARGIN_CELLS] of its level's cells, meshed at its level or finer, and every block round a
         * block of a level meshed at the level before or finer.
         */
        fun of(field: JoinedField, cell: Float, levels: IntArray): CellLevels {
            val deepest = levels.maxOrNull() ?: 0
            if (deepest == 0) return CellLevels(cell, emptyList())
            val deeper = List(deepest) { HashSet<Long>() }
            for (level in deepest downTo 1) {
                val grid = BlockGrid(cell / (1 shl level))
                val margin = MARGIN_CELLS * grid.cell
                val into = deeper[level - 1]
                field.members.forEachIndexed { i, member ->
                    if (levels[i] < level) return@forEachIndexed
                    val grow = field.reaches[i] + margin
                    val g = Vec3(grow, grow, grow)
                    grid.blocksIn(member.low - g, member.high + g) { into += it }
                }
                if (level < deepest) {
                    for (key in deeper[level]) {
                        val bx = grid.bx(key)
                        val by = grid.by(key)
                        val bz = grid.bz(key)
                        for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) into += grid.key((bx + dx) shr 1, (by + dy) shr 1, (bz + dz) shr 1)
                    }
                }
            }
            return CellLevels(cell, deeper)
        }
    }
}
