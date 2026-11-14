package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor
import kotlin.math.sqrt

/** Cells a block spans along each axis. */
internal const val BLOCK = 8

/** Grid points a block holds along each axis: its cells' corners, its far face shared with the next block. */
internal const val SIDE = BLOCK + 1
internal const val BLOCK_CORNERS = SIDE * SIDE * SIDE
internal const val BLOCK_CELLS = BLOCK * BLOCK * BLOCK

private const val KEY_BITS = 21
private const val KEY_MASK = (1L shl KEY_BITS) - 1
private const val KEY_OFFSET = 1 shl (KEY_BITS - 1)

/**
 * An odd number a block's packed place is multiplied by to make its key, and its inverse modulo 2^64 that undoes it: the product
 * spreads neighbouring blocks' keys over every bit, so tables hashed by key keep them apart.
 */
private const val KEY_SPREAD = -0x61c8864680b583ebL
private const val KEY_UNSPREAD = -0xe217c1e66c88cc3L

/**
 * The body's space cut into cubes [cell] across, grid point (i, j, k) at (i, j, k) times [cell] from the body's origin, so a cell's
 * place never depends on what is meshed, and grouped in blocks of [BLOCK] cells a side.
 */
internal class BlockGrid(val cell: Float) {
    /** Half a block's diagonal: every point of a block lies within it of the block's middle. */
    val halfDiagonal: Float = sqrt(3f) * BLOCK * cell / 2f

    fun key(bx: Int, by: Int, bz: Int): Long =
        (((bx + KEY_OFFSET).toLong() and KEY_MASK shl (2 * KEY_BITS)) or ((by + KEY_OFFSET).toLong() and KEY_MASK shl KEY_BITS) or
            ((bz + KEY_OFFSET).toLong() and KEY_MASK)) * KEY_SPREAD

    fun bx(key: Long): Int = (((key * KEY_UNSPREAD) shr (2 * KEY_BITS)) and KEY_MASK).toInt() - KEY_OFFSET
    fun by(key: Long): Int = (((key * KEY_UNSPREAD) shr KEY_BITS) and KEY_MASK).toInt() - KEY_OFFSET
    fun bz(key: Long): Int = ((key * KEY_UNSPREAD) and KEY_MASK).toInt() - KEY_OFFSET

    /** Grid point (i, j, k) in body space. */
    fun point(i: Int, j: Int, k: Int): Vec3 = Vec3(i * cell, j * cell, k * cell)

    /** The middle of block [key], which is one of its grid points. */
    fun middle(key: Long): Vec3 = point(bx(key) * BLOCK + BLOCK / 2, by(key) * BLOCK + BLOCK / 2, bz(key) * BLOCK + BLOCK / 2)

    /** The block holding [value] along one axis. */
    fun blockOf(value: Float): Int = floor(value / (cell * BLOCK)).toInt()

    /** Every block that meets the box from [low] to [high]. */
    inline fun blocksIn(low: Vec3, high: Vec3, each: (Long) -> Unit) {
        for (x in blockOf(low.x)..blockOf(high.x)) for (y in blockOf(low.y)..blockOf(high.y)) for (z in blockOf(low.z)..blockOf(high.z)) each(key(x, y, z))
    }

    companion object {
        /** A grid point of a block, from its place along each axis, 0 to [BLOCK]. */
        fun corner(i: Int, j: Int, k: Int): Int = i + SIDE * (j + SIDE * k)

        /** A cell of a block, from its place along each axis, 0 to [BLOCK] - 1. */
        fun cell(i: Int, j: Int, k: Int): Int = i + BLOCK * (j + BLOCK * k)
    }
}
