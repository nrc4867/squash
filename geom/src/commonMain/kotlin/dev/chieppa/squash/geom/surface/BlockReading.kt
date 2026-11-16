package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.collections.IntList
import dev.chieppa.squash.geom.field.FieldAsk
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.MemberKey
import dev.chieppa.squash.geom.field.smoothMin
import kotlin.math.abs

/**
 * How much faster than a true distance a member's field may change, for the tests that read a block by its middle: the fields are only
 * near a distance where a run swells or bends hard.
 */
internal const val LIPSCHITZ = 1.5f

/** Every sub-block of a block, as a mask. */
private const val EVERY_SUB = -1L

/** Reading a block's members and the joined field at its grid points. */
internal object BlockReading {
    /**
     * Block [key] read against [field], whose members are found by key in [index]: the members within reach of mattering anywhere in it,
     * judged from its middle, each one's field at its grid points, taken from [kept] for a member it already held, and its cells' sheets.
     * Null where nothing lies near, or, unless [forced], where its middle lies too far from the surface for the surface to cross it; [kept]
     * itself, or a copy of it bound to [field], where nothing it holds has changed. A grown member [kept] does not hold takes what [lent]
     * holds of its solid in place of reading it there. An [exact] block reads every member whose box lies within reach of it at every grid
     * point, standing in for nothing, so its values hold however fast a field changes. The grid points it shares with the blocks
     * [coarser] names are blended from the coarser grid's (see [CoarserFaces]), and the cells of its [excluded] octants hold no surface.
     */
    suspend fun read(
        ask: FieldAsk,
        grid: BlockGrid,
        field: JoinedField,
        index: Map<MemberKey, Int>,
        key: Long,
        keptBlock: SurfaceBlock?,
        forced: Boolean,
        lentChannels: ChannelSource? = null,
        exact: Boolean = false,
        coarser: Int = 0,
        excluded: Int = 0,
    ): SurfaceBlock? {
        val kept = keptBlock.takeUnless { exact }
        val lent = lentChannels.takeUnless { exact }
        val middle = grid.middle(key)
        val margin = grid.halfDiagonal * LIPSCHITZ
        val count = field.members.size
        val boxes = FloatArray(count) { field.members[it].boxDistance(middle) }
        val keptSlots = kept?.keys?.withIndex()?.associate { (s, k) -> k to s }.orEmpty()
        val centre = BlockGrid.corner(BLOCK / 2, BLOCK / 2, BLOCK / 2)
        val owns = FloatArray(count) { Float.NaN }
        val centreSlots = kept?.centreKeys?.withIndex()?.associate { (s, k) -> k to s }.orEmpty()

        /**
         * Each of [ms]' own field at the middle not yet known: kept where the kept block holds it there, read where asked otherwise. Where
         * reads are gathered, a kept channel's value at the middle is not taken, so every middle is read alike.
         */
        suspend fun ownAt(ms: List<Int>) {
            val asked = IntArray(ms.size) { -1 }
            ms.forEachIndexed { n, m ->
                if (!owns[m].isNaN()) return@forEachIndexed
                val memberKey = field.members[m].key
                val s = keptSlots[memberKey]
                val seen = centreSlots[memberKey]
                when {
                    seen != null -> owns[m] = kept!!.centreOwns[seen]
                    s != null && !ask.gathers && SubBlocks.subsOf(centre) and kept!!.exact[s] != 0L -> owns[m] = kept.channels[s][centre]
                    else -> asked[n] = ask.own(m, middle)
                }
            }
            ask.answeredHere()
            ms.forEachIndexed { n, m -> if (asked[n] >= 0) owns[m] = ask.field(asked[n]) }
        }
        val inside = (0 until count).filter { boxes[it] == 0f }
        ownAt(inside)
        var least = Float.MAX_VALUE
        for (m in inside) least = minOf(least, owns[m])
        if (least == Float.MAX_VALUE) {
            val closest = (0 until count).minByOrNull { boxes[it] } ?: return null
            ownAt(listOf(closest))
            least = owns[closest]
        }
        val relevant = ArrayList<Int>()
        if (exact) {
            val near = (0 until count).filter { boxes[it] <= grid.halfDiagonal + margin + field.reaches[it] }
            ownAt(near)
            relevant += near
        } else {
            val near = (0 until count).filter { boxes[it] - margin <= least + margin + field.reaches[it] }
            ownAt(near)
            for (m in near) if (owns[m] - margin <= least + margin + field.reaches[m]) relevant += m
        }
        if (relevant.isEmpty()) return null
        if (!forced && abs(joinedAtMiddle(field, relevant, owns)) > margin + grid.cell) return null
        val block = SurfaceBlock(key, grid.bx(key), grid.by(key), grid.bz(key))
        val centred = (0 until count).filter { !owns[it].isNaN() }
        block.centreKeys = Array(centred.size) { field.members[centred[it]].key }
        block.centreOwns = FloatArray(centred.size) { owns[centred[it]] }
        block.keys = Array(relevant.size) { field.members[relevant[it]].key }
        block.bind(field, index)
        val borrowed = Array(relevant.size) { s ->
            val member = field.members[relevant[s]]
            if (lent == null || member.ease == 0f || keptSlots[member.key] != null) null else lent.lend(key, member)
        }
        val (middles, leastAt) = SubBlocks.middles(ask, relevant, grid, block, relevant.size, FloatArray(relevant.size) { owns[relevant[it]] }) { s ->
            keptSlots[field.members[relevant[s]].key]?.let { k ->
                val octants = kept!!.octants.takeIf { it.isNotEmpty() }?.copyOfRange(k * SurfaceBlock.OCTANT_COUNT, (k + 1) * SurfaceBlock.OCTANT_COUNT)
                SubBlocks.KeptMiddles(kept.middles.copyOfRange(k * SUBS, k * SUBS + SUBS), kept.middlesRead[k], octants)
            } ?: borrowed[s]?.let { SubBlocks.KeptMiddles(it.middles, it.middlesRead, null) }
        }
        block.middles = middles
        block.relevance = if (exact) LongArray(relevant.size) { EVERY_SUB } else SubBlocks.relevance(grid, middles, leastAt, block.parentSlots, block.blends)
        block.shareRelevance = SubBlocks.shareRelevance(grid, middles, leastAt, block.parentSlots, block.reaches)
        val far = if (exact) 0L else SubBlocks.far(grid, middles, block.parentSlots, block.blends)
        block.exact = LongArray(relevant.size)
        val reading = arrayOfNulls<IntArray>(relevant.size)
        val froms = arrayOfNulls<FloatArray>(relevant.size)
        val helds = LongArray(relevant.size)
        val kepts = arrayOfNulls<FloatArray>(relevant.size)
        for (s in relevant.indices) {
            val member = field.members[relevant[s]]
            val k = keptSlots[member.key]
            val need = block.relevance[s] and far.inv()
            val other = borrowed[s]
            val held = if (k != null) kept!!.exact[k] else other?.exact ?: 0L
            block.exact[s] = need or held
            if (k != null && need and held.inv() == 0L && kept!!.far == far) {
                kepts[s] = kept.channels[k]
            } else {
                val from = if (k != null) kept!!.channels[k] else other?.channel
                froms[s] = from
                helds[s] = held
                reading[s] = SubBlocks.askChannel(ask, relevant[s], grid, block.i0, block.j0, block.k0, need, from, held)
            }
        }
        ask.answered()
        block.channels = Array(relevant.size) { s ->
            kepts[s] ?: SubBlocks.channel(ask, checkNotNull(reading[s]), far, middles.copyOfRange(s * SUBS, s * SUBS + SUBS), froms[s], helds[s], grid)
        }
        block.far = far
        if (kept != null && kept.coarser == coarser && kept.excluded == excluded && kept.far == far && kept.keys.contentEquals(block.keys) && kept.relevance.contentEquals(block.relevance) &&
            kept.shareRelevance.contentEquals(block.shareRelevance) && block.channels.indices.all { block.channels[it] === kept.channels[it] }
        ) {
            return if (kept.members.contentEquals(block.members)) kept else kept.copy(placing = false).also { it.bind(field, index) }
        }
        block.coarser = coarser
        block.excluded = excluded
        block.findCells(grid)
        return block
    }

    private fun joinedAtMiddle(field: JoinedField, relevant: List<Int>, owns: FloatArray): Float {
        var joined = Float.MAX_VALUE
        for (m in relevant) {
            val member = field.members[m]
            val parent = member.parent
            val term = if (parent >= 0 && member.blend > 0f && parent in relevant) smoothMin(owns[parent], owns[m], member.blend) else owns[m]
            if (term < joined) joined = term
        }
        return joined
    }

    /** The blocks beside [block] across each face the surface crosses, which must be read too so no sheet stops at the block's edge. */
    fun neighboursNeeded(grid: BlockGrid, block: SurfaceBlock): List<Long> {
        val needed = ArrayList<Long>(6)
        for (axis in 0 until 3) for (side in 0..1) {
            val at = side * BLOCK
            if (faceCrossed(block, axis, at)) {
                val dx = if (axis == 0) side * 2 - 1 else 0
                val dy = if (axis == 1) side * 2 - 1 else 0
                val dz = if (axis == 2) side * 2 - 1 else 0
                needed += grid.key(block.bx + dx, block.by + dy, block.bz + dz)
            }
        }
        return needed
    }

    /** Whether the joined field changes sign between two neighbouring grid points of [block]'s face at [at] across [axis]. */
    private fun faceCrossed(block: SurfaceBlock, axis: Int, at: Int): Boolean {
        fun corner(a: Int, b: Int) = when (axis) {
            0 -> BlockGrid.corner(at, a, b)
            1 -> BlockGrid.corner(a, at, b)
            else -> BlockGrid.corner(a, b, at)
        }
        for (b in 0..BLOCK) for (a in 0..BLOCK) {
            val inside = block.corners[corner(a, b)] < 0f
            if (a < BLOCK && (block.corners[corner(a + 1, b)] < 0f) != inside) return true
            if (b < BLOCK && (block.corners[corner(a, b + 1)] < 0f) != inside) return true
        }
        return false
    }

    /**
     * Fills [block]'s quads: one across each grid edge the surface crosses whose lower end is one of the block's own grid points, joining
     * the vertices of the crossing's sheet in the four cells round it, read from the blocks [lookup] gives by key.
     */
    fun buildQuads(grid: BlockGrid, block: SurfaceBlock, lookup: (Long) -> SurfaceBlock?) {
        val quads = IntList()
        val near = arrayOfNulls<SurfaceBlock>(8)
        for (code in 0 until 8) {
            val dx = -(code and 1)
            val dy = -((code shr 1) and 1)
            val dz = -((code shr 2) and 1)
            near[code] = if (code == 0) block else lookup(grid.key(block.bx + dx, block.by + dy, block.bz + dz))
        }
        val cellB = IntArray(4)
        val refs = IntArray(4)
        for (k in 0 until BLOCK) for (j in 0 until BLOCK) for (i in 0 until BLOCK) {
            val here = block.corners[BlockGrid.corner(i, j, k)]
            for (axis in 0 until 3) {
                val there = when (axis) {
                    0 -> block.corners[BlockGrid.corner(i + 1, j, k)]
                    1 -> block.corners[BlockGrid.corner(i, j + 1, k)]
                    else -> block.corners[BlockGrid.corner(i, j, k + 1)]
                }
                if ((here < 0f) == (there < 0f)) continue
                var whole = true
                for (q in 0 until 4) {
                    val b = if (q == 1 || q == 2) 0 else -1
                    val c = if (q >= 2) 0 else -1
                    val point = intArrayOf(i, j, k)
                    val cellAt = intArrayOf(i, j, k)
                    val other1 = (axis + 1) % 3
                    val other2 = (axis + 2) % 3
                    cellAt[other1] += b
                    cellAt[other2] += c
                    val dx = if (cellAt[0] < 0) -1 else 0
                    val dy = if (cellAt[1] < 0) -1 else 0
                    val dz = if (cellAt[2] < 0) -1 else 0
                    val owner = near[(-dx) or ((-dy) shl 1) or ((-dz) shl 2)]
                    if (owner == null) {
                        whole = false
                        break
                    }
                    val cx = cellAt[0] - dx * BLOCK
                    val cy = cellAt[1] - dy * BLOCK
                    val cz = cellAt[2] - dz * BLOCK
                    val cell = BlockGrid.cell(cx, cy, cz)
                    val bits = IntArray(3) { point[it] - cellAt[it] }
                    val edge = when (axis) {
                        0 -> CellTables.edgeAlong(0, bits[1], bits[2])
                        1 -> CellTables.edgeAlong(1, bits[0], bits[2])
                        else -> CellTables.edgeAlong(2, bits[0], bits[1])
                    }
                    val sheet = owner.edgeSheets[cell * CellTables.EDGES + edge].toInt()
                    if (sheet < 0) {
                        whole = false
                        break
                    }
                    cellB[q] = cell
                    refs[q] = SurfaceBlock.ref(dx, dy, dz, cell, sheet)
                }
                if (!whole) continue
                for (q in 0 until 4) quads.add(refs[q])
                quads.add(axis * 2 + if (here < 0f) 1 else 0)
            }
        }
        block.quads = quads.toArray()
        block.quadCount = block.quads.size / SurfaceBlock.QUAD_INTS
    }
}
