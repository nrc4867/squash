package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.MemberKey
import dev.chieppa.squash.geom.field.smoothMin

/**
 * One block of the grid as last meshed: the members whose fields can matter in it ([keys], each one's own field at every grid point
 * of the block in [channels]), the joined field there in [corners], which sheet each crossed edge of each cell belongs to, and one
 * vertex per sheet per cell, from where it starts to where it was placed on the surface with its coordinates.
 */
internal class SurfaceBlock(val key: Long, val bx: Int, val by: Int, val bz: Int) {
    var keys: Array<MemberKey> = emptyArray()
    var channels: Array<FloatArray> = emptyArray()

    /**
     * For each of [keys], as masks of sub-blocks (see [SubBlocks]): where its channel holds its read field, a bound elsewhere; where it
     * can change the union; and where it can hold some of the surface's shares.
     */
    var exact: LongArray = LongArray(0)

    /** Each of [keys]' own field at the middle of each sub-block, [SUBS] values a member. */
    var middles: FloatArray = FloatArray(0)

    /** Each of [keys]' own field at the middle of each of the block's octants, [OCTANT_COUNT] values a member, NaN where not read. */
    var octants: FloatArray = FloatArray(0)

    /** Members' own fields at the block's middle, by key, as last read there. */
    var centreKeys: Array<MemberKey> = emptyArray()
    var centreOwns: FloatArray = FloatArray(0)

    /** Where each of [keys]' [middles] were read, a bound from the block's middle elsewhere. */
    var middlesRead: LongArray = LongArray(0)
    var relevance: LongArray = LongArray(0)
    var shareRelevance: LongArray = LongArray(0)

    /** The sub-blocks where the union cannot change sign, whose channels hold only stand-ins wherever they are not exact. */
    var far: Long = 0L

    /** Each of [keys] as an index into the field the block was last read against, -1 for one it no longer holds. */
    var members: IntArray = IntArray(0)

    /** Each of [keys]' parent among them, or -1, and the blend it joins its parent by. */
    var parentSlots: IntArray = IntArray(0)
    var blends: FloatArray = FloatArray(0)

    /** Each of [keys]' widest blend, its own or a child's. */
    var reaches: FloatArray = FloatArray(0)

    var corners = FloatArray(BLOCK_CORNERS)

    /** The blocks beside this one across a face the surface crosses (see [BlockReading.neighboursNeeded]). */
    var needed: List<Long> = emptyList()

    /** For each cell's twelve edges, the sheet its crossing belongs to, -1 for none. */
    var edgeSheets = ByteArray(BLOCK_CELLS * CellTables.EDGES)

    /** Each cell's first vertex, its sheets' vertices following in order; -1 for a cell the surface does not cross. */
    var cellFirst = IntArray(BLOCK_CELLS) { -1 }

    var vertexCount = 0
    var vertexCell = IntArray(0)

    /** Where each vertex starts, three values each: the mean of its sheet's edge crossings. */
    var initial = FloatArray(0)

    /** Where each vertex was placed on the surface, three values each, its normal, and what it holds of the members there. */
    var positions = FloatArray(0)
    var normals = FloatArray(0)

    /** Where each vertex was relaxed to before it was placed, three values each. */
    var starts = FloatArray(0)
    var coordinates = VertexCoordinates(0)

    /** Each quad as four vertex references (see [ref]) and the direction it faces, [QUAD_INTS] values each. */
    var quads = IntArray(0)
    var quadCount = 0

    /** The lowest grid point of the block. */
    val i0 get() = bx * BLOCK
    val j0 get() = by * BLOCK
    val k0 get() = bz * BLOCK

    /**
     * This block sharing everything read and found in it, with its own copies of what placing and quads write when [placing], so a run
     * that stops part way leaves the block it copied as it was.
     */
    fun copy(placing: Boolean): SurfaceBlock = SurfaceBlock(key, bx, by, bz).also { b ->
        b.keys = keys
        b.channels = channels
        b.exact = exact
        b.relevance = relevance
        b.shareRelevance = shareRelevance
        b.far = far
        b.middles = middles
        b.middlesRead = middlesRead
        b.octants = octants
        b.centreKeys = centreKeys
        b.centreOwns = centreOwns
        b.members = members
        b.parentSlots = parentSlots
        b.blends = blends
        b.reaches = reaches
        b.corners = corners
        b.needed = needed
        b.edgeSheets = edgeSheets
        b.cellFirst = cellFirst
        b.vertexCount = vertexCount
        b.vertexCell = vertexCell
        b.initial = initial
        b.positions = if (placing) positions.copyOf() else positions
        b.normals = if (placing) normals.copyOf() else normals
        b.starts = if (placing) starts.copyOf() else starts
        b.coordinates = if (placing) coordinates.copy() else coordinates
        b.quads = quads
        b.quadCount = quadCount
    }

    /** Gives the block its own copies of what placing writes, as [copy] with placing does. */
    fun ownPlacing() {
        positions = positions.copyOf()
        normals = normals.copyOf()
        starts = starts.copyOf()
        coordinates = coordinates.copy()
    }

    /** The joined field at grid point [corner] of the block from its kept channels. */
    fun joinedAt(corner: Int): Float {
        var joined = Float.MAX_VALUE
        for (s in keys.indices) {
            val d = channels[s][corner]
            val parent = parentSlots[s]
            val term = if (parent >= 0 && blends[s] > 0f) smoothMin(channels[parent][corner], d, blends[s]) else d
            if (term < joined) joined = term
        }
        return joined
    }

    /** Sets [members], [parentSlots] and [blends] for [field], whose members are found by key in [index]. */
    fun bind(field: JoinedField, index: Map<MemberKey, Int>) {
        members = IntArray(keys.size) { index[keys[it]] ?: -1 }
        parentSlots = IntArray(keys.size) { s ->
            val m = members[s]
            val parent = if (m >= 0) field.members[m].parent else -1
            if (parent < 0) -1 else members.indexOf(parent)
        }
        blends = FloatArray(keys.size) { s -> members[s].let { if (it >= 0) field.members[it].blend else 0f } }
        reaches = FloatArray(keys.size) { s -> members[s].let { if (it >= 0) field.reaches[it] else 0f } }
    }

    /** Fills [corners] from the channels, finds each cell's sheets and starts a vertex for each at the mean of its crossings. */
    fun findCells(grid: BlockGrid) {
        for (c in 0 until BLOCK_CORNERS) corners[c] = joinedAt(c)
        val values = FloatArray(CellTables.CORNERS)
        val sheetsOf = IntArray(BLOCK_CELLS)
        var total = 0
        for (ck in 0 until BLOCK) for (cj in 0 until BLOCK) for (ci in 0 until BLOCK) {
            val cell = BlockGrid.cell(ci, cj, ck)
            for (c in 0 until CellTables.CORNERS) {
                values[c] = corners[BlockGrid.corner(ci + CellTables.cornerX(c), cj + CellTables.cornerY(c), ck + CellTables.cornerZ(c))]
            }
            val sheets = CellTables.components(values, edgeSheets, cell * CellTables.EDGES)
            sheetsOf[cell] = sheets
            if (sheets > 0) {
                cellFirst[cell] = total
                total += sheets
            } else {
                cellFirst[cell] = -1
            }
        }
        vertexCount = total
        vertexCell = IntArray(total)
        initial = FloatArray(total * 3)
        positions = FloatArray(total * 3)
        normals = FloatArray(total * 3)
        starts = FloatArray(total * 3)
        coordinates = VertexCoordinates(total)
        val sums = FloatArray(4 * 4)
        for (cell in 0 until BLOCK_CELLS) {
            val first = cellFirst[cell]
            if (first < 0) continue
            val ci = cell % BLOCK
            val cj = (cell / BLOCK) % BLOCK
            val ck = cell / (BLOCK * BLOCK)
            for (c in 0 until CellTables.CORNERS) {
                values[c] = corners[BlockGrid.corner(ci + CellTables.cornerX(c), cj + CellTables.cornerY(c), ck + CellTables.cornerZ(c))]
            }
            sums.fill(0f)
            for (e in 0 until CellTables.EDGES) {
                val sheet = edgeSheets[cell * CellTables.EDGES + e].toInt()
                if (sheet < 0) continue
                val (a, b) = CellTables.EDGE_CORNERS[e]
                val t = values[a] / (values[a] - values[b])
                sums[sheet * 4] += CellTables.cornerX(a) + t * (CellTables.cornerX(b) - CellTables.cornerX(a))
                sums[sheet * 4 + 1] += CellTables.cornerY(a) + t * (CellTables.cornerY(b) - CellTables.cornerY(a))
                sums[sheet * 4 + 2] += CellTables.cornerZ(a) + t * (CellTables.cornerZ(b) - CellTables.cornerZ(a))
                sums[sheet * 4 + 3] += 1f
            }
            for (sheet in 0 until sheetsOf[cell]) {
                val v = first + sheet
                val n = sums[sheet * 4 + 3]
                vertexCell[v] = cell
                initial[v * 3] = (i0 + ci + sums[sheet * 4] / n) * grid.cell
                initial[v * 3 + 1] = (j0 + cj + sums[sheet * 4 + 1] / n) * grid.cell
                initial[v * 3 + 2] = (k0 + ck + sums[sheet * 4 + 2] / n) * grid.cell
            }
        }
    }

    /** Where cell [cell] of the block starts, in body space. */
    fun cellLow(grid: BlockGrid, cell: Int): Vec3 =
        grid.point(i0 + cell % BLOCK, j0 + (cell / BLOCK) % BLOCK, k0 + cell / (BLOCK * BLOCK))

    /**
     * The joined field's gradient at [p] in cell [cell], from the trilinear blend of its corners' values, and that value, as (gx, gy, gz, f)
     * written into [out].
     */
    fun trilinear(grid: BlockGrid, cell: Int, p: Vec3, out: FloatArray) {
        val ci = cell % BLOCK
        val cj = (cell / BLOCK) % BLOCK
        val ck = cell / (BLOCK * BLOCK)
        val x = (p.x / grid.cell - (i0 + ci)).coerceIn(0f, 1f)
        val y = (p.y / grid.cell - (j0 + cj)).coerceIn(0f, 1f)
        val z = (p.z / grid.cell - (k0 + ck)).coerceIn(0f, 1f)
        val c000 = corners[BlockGrid.corner(ci, cj, ck)]
        val c100 = corners[BlockGrid.corner(ci + 1, cj, ck)]
        val c010 = corners[BlockGrid.corner(ci, cj + 1, ck)]
        val c110 = corners[BlockGrid.corner(ci + 1, cj + 1, ck)]
        val c001 = corners[BlockGrid.corner(ci, cj, ck + 1)]
        val c101 = corners[BlockGrid.corner(ci + 1, cj, ck + 1)]
        val c011 = corners[BlockGrid.corner(ci, cj + 1, ck + 1)]
        val c111 = corners[BlockGrid.corner(ci + 1, cj + 1, ck + 1)]
        val y0z0 = c000 + (c100 - c000) * x
        val y1z0 = c010 + (c110 - c010) * x
        val y0z1 = c001 + (c101 - c001) * x
        val y1z1 = c011 + (c111 - c011) * x
        val z0 = y0z0 + (y1z0 - y0z0) * y
        val z1 = y0z1 + (y1z1 - y0z1) * y
        out[3] = z0 + (z1 - z0) * z
        val dx0 = (c100 - c000) * (1f - y) + (c110 - c010) * y
        val dx1 = (c101 - c001) * (1f - y) + (c111 - c011) * y
        out[0] = (dx0 * (1f - z) + dx1 * z) / grid.cell
        out[1] = ((y1z0 - y0z0) * (1f - z) + (y1z1 - y0z1) * z) / grid.cell
        out[2] = (z1 - z0) / grid.cell
    }

    companion object {
        /** The octants a block is cut into, each four cells a side. */
        const val OCTANT_COUNT = 8

        /** Values a quad takes in [quads]: four vertex references and its facing. */
        const val QUAD_INTS = 5

        /**
         * A reference to sheet [sheet] of cell [cell] in the block [dx], [dy], [dz] (each 0 or -1) from the one holding the reference.
         */
        fun ref(dx: Int, dy: Int, dz: Int, cell: Int, sheet: Int): Int =
            (((-dx) or ((-dy) shl 1) or ((-dz) shl 2)) shl 11) or (cell shl 2) or sheet

        fun refDx(ref: Int) = -((ref shr 11) and 1)
        fun refDy(ref: Int) = -((ref shr 12) and 1)
        fun refDz(ref: Int) = -((ref shr 13) and 1)
        fun refCell(ref: Int) = (ref shr 2) and (BLOCK_CELLS - 1)
        fun refSheet(ref: Int) = ref and 3
    }
}
