package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.MemberKey
import dev.chieppa.squash.geom.field.ReadRounds

/** Blocks one piece of work reads where the reads are gathered into batches. */
private const val GATHERED_CHUNK = 8

/** Blocks one piece of the seams' check takes. */
private const val SEAM_CHUNK = 256

/** How far past a changed member's reach its blocks are read again, in the grid's block half-diagonals. */
private const val DIRTY_MARGINS = 4f

/**
 * Meshes a joined field into one surface on a sparse grid of blocks, cells [cell] across, and keeps the blocks so that the next field
 * meshes again only where it differs: each block holds its members' own fields at its grid points, so a block a changed member does not
 * reach keeps every vertex and quad, and one it reaches reads only that member again. The mesh is dual: one vertex per sheet of surface in
 * each cell it crosses (Schaefer, Ju and Warren's manifold split of Ju, Losasso, Schaefer and Warren's dual contouring), started at the
 * mean of its sheet's edge crossings, relaxed toward its neighbours inside its cell as Gibson's surface nets do, and projected onto the
 * field; one quad across each grid edge the surface crosses. Not safe for two runs at once.
 */
class SurfaceMesher(val cell: Float, private val reads: FieldReads = CpuFieldReads) {
    private val grid = BlockGrid(cell)
    private var blocks: Map<Long, SurfaceBlock> = emptyMap()
    private var field: JoinedField? = null
    private var kept: SurfaceMesh? = null
    private var generation = reads.generation

    /**
     * A run meshing [next]: hand out its batches of work with [MeshRun.next], each batch's pieces independent of one another, until it
     * has none, then read [MeshRun.mesh]. The mesher keeps what the run made only once the run is done. A grown member a block has not
     * kept reads its channel from [lent], when given on this grid, where that holds its solid's.
     */
    fun begin(next: JoinedField, lent: ChannelSource? = null): MeshRun {
        if (reads.generation != generation) {
            generation = reads.generation
            blocks = emptyMap()
            field = null
            kept = null
        }
        return MeshRun(next, lent?.takeIf { it.cell == cell && it.generation == generation })
    }

    /** One run of the mesher over a field. */
    inner class MeshRun internal constructor(private val next: JoinedField, private val lent: ChannelSource?) {
        private val index: Map<MemberKey, Int> = next.members.withIndex().associate { (i, m) -> m.key to i }
        private val map = HashMap(blocks)
        private val fresh = HashSet<Long>()
        private var stage = Stage.READ
        private var pending: List<Long> = emptyList()
        private var pendingSet: Set<Long> = emptySet()
        private var results: Array<SurfaceBlock?> = emptyArray()
        private var needs: Array<List<Long>> = emptyArray()
        private var alike: BooleanArray = BooleanArray(0)
        private val forced = HashSet<Long>()

        /** Blocks read this run whose seams with their neighbours are not yet checked, and those read again exactly to close a seam. */
        private val unchecked = HashSet<Long>()
        private val exact = HashSet<Long>()
        private var seams: Array<Set<Long>?> = emptyArray()
        private var assembly: MeshAssembly? = null
        private var done: SurfaceMesh? = null
        private var reading: ReadRounds? = null

        /**
         * The round of reads the run waits on, where its reads are gathered, for the caller to answer beside other runs' with
         * [ReadRounds.answerTogether] before it calls [next] again; [next] gives a batch answering it alone otherwise.
         */
        val asking: ReadRounds? get() = reading?.takeIf { !it.answered && it.waiting() }

        /** The mesh the run made, once [next] has given null. */
        val mesh: SurfaceMesh get() = checkNotNull(done) { "The run has work left" }

        /** The next batch of work, or null when the run is done. */
        fun next(): List<() -> Unit>? {
            reading?.let { rounds ->
                if (rounds.answered) return rounds.tasks()
                if (rounds.waiting()) return listOf({ rounds.answer() })
                reading = null
            }
            while (true) {
                when (stage) {
                    Stage.READ -> {
                        val dirty = dirtyKeys()
                        pending = dirty.sorted()
                        pendingSet = dirty
                        val last = kept
                        if (pending.isEmpty() && last != null) {
                            field = next
                            done = last
                            stage = Stage.DONE
                            continue
                        }
                        rebindClean()
                        stage = Stage.READ_DONE
                        return readTasks(pending, forcedAll = false)
                    }
                    Stage.READ_DONE -> {
                        takeResults()
                        unchecked += pending
                        val missing = missingNeighbours()
                        if (missing.isEmpty()) {
                            stage = Stage.SEAMS
                            continue
                        }
                        pending = missing
                        pendingSet = missing.toHashSet()
                        forced += missing
                        return readTasks(missing, forcedAll = true)
                    }
                    Stage.SEAMS -> {
                        val checking = unchecked.toList()
                        unchecked.clear()
                        val found = arrayOfNulls<Set<Long>>((checking.size + SEAM_CHUNK - 1) / SEAM_CHUNK)
                        seams = found
                        stage = Stage.SEAMS_DONE
                        return checking.chunked(SEAM_CHUNK).mapIndexed { i, keys -> { found[i] = BlockSeams.disagreeing(grid, map, keys) } }.ifEmpty { listOf({}) }
                    }
                    Stage.SEAMS_DONE -> {
                        val all = HashSet<Long>()
                        for (part in seams) if (part != null) all += part
                        val torn = all.filter { it !in exact }.sorted()
                        if (torn.isEmpty()) {
                            stage = Stage.QUADS
                            continue
                        }
                        pending = torn
                        pendingSet = torn.toHashSet()
                        exact += torn
                        stage = Stage.READ_DONE
                        return readTasks(torn, forcedAll = true)
                    }
                    Stage.QUADS -> {
                        val requad = HashSet<Long>()
                        for (key in fresh) around(key) { if (it in map) requad += it }
                        val owned = requad.map { key -> map.getValue(key).let { if (key in fresh) it else it.copy(placing = false) }.also { map[key] = it } }
                        stage = Stage.RESOLVE
                        return owned.map { block ->
                            {
                                if (block.key !in fresh) block.ownPlacing()
                                BlockReading.buildQuads(grid, block) { map[it] }
                            }
                        }
                    }
                    Stage.RESOLVE -> {
                        val built = MeshAssembly(grid, next, map, fresh, blocks, reads, kept, field)
                        assembly = built
                        stage = Stage.PLACE
                        return built.resolveTasks()
                    }
                    Stage.PLACE -> {
                        stage = Stage.GATHER
                        val rounds = checkNotNull(assembly).placeRounds()
                        reading = rounds
                        return rounds.tasks()
                    }
                    Stage.GATHER -> {
                        stage = Stage.CUT
                        return checkNotNull(assembly).gatherTasks()
                    }
                    Stage.CUT -> {
                        stage = Stage.FINISH
                        return checkNotNull(assembly).triangleTasks()
                    }
                    Stage.FINISH -> {
                        val mesh = checkNotNull(assembly).mesh()
                        blocks = map
                        field = next
                        kept = mesh
                        done = mesh
                        stage = Stage.DONE
                    }
                    Stage.DONE -> return null
                }
            }
        }

        private fun readTasks(keys: List<Long>, forcedAll: Boolean): List<() -> Unit> {
            results = arrayOfNulls(keys.size)
            needs = Array(keys.size) { emptyList() }
            alike = BooleanArray(keys.size)
            val before = keys.map { map[it] }
            val rounds = ReadRounds(reads, next.members, keys.size, if (reads.gathers) GATHERED_CHUNK else 1) { i ->
                val key = keys[i]
                val block = BlockReading.read(this, grid, next, index, key, blocks[key], forcedAll || key in forced, lent, exact = key in exact)
                    results[i] = block
                    if (block != null) {
                        block.needed = BlockReading.neighboursNeeded(grid, block)
                        needs[i] = block.needed
                        val old = before[i]
                        alike[i] = old != null && block.channels !== old.channels && block.placesAs(old)
                    }
            }
            reading = rounds
            return rounds.tasks()
        }

        private fun takeResults() {
            pending.forEachIndexed { i, key ->
                val block = results[i]
                val before = map[key]
                if (block == null) map.remove(key) else map[key] = block
                when {
                    block == null || before == null -> if (block !== before) fresh += key
                    block.channels === before.channels -> {}
                    alike[i] -> block.takePlacement(before)
                    else -> fresh += key
                }
            }
        }

        /** Blocks next to a read block across a face the surface crosses that the run holds none of. */
        private fun missingNeighbours(): List<Long> {
            val missing = HashSet<Long>()
            pending.forEachIndexed { i, key -> for (n in needs[i]) if (n !in map && n !in forced) missing += n }
            for (key in pending) if (key !in map) around(key) { n ->
                val block = map[n]
                if (block != null && n !in pendingSet && key in block.needed && key !in forced) missing += key
            }
            return missing.sorted()
        }

        private inline fun around(key: Long, each: (Long) -> Unit) {
            val bx = grid.bx(key)
            val by = grid.by(key)
            val bz = grid.bz(key)
            for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) each(grid.key(bx + dx, by + dy, bz + dz))
        }

        /**
         * Points every kept block at [next]'s members, copying those whose members moved in the list. A block not read again holds only
         * members of the last field, by their place in it, so it needs a copy only where one of those has another place in [next].
         */
        private fun rebindClean() {
            val last = field ?: return
            val moved = IntArray(last.members.size) { j -> index[last.members[j].key] ?: -1 }
            if (moved.withIndex().all { (j, i) -> i == j || i < 0 }) return
            for ((key, block) in map.entries.toList()) {
                if (key in pendingSet) continue
                if (block.members.all { it < 0 || moved[it] == it }) continue
                map[key] = block.copy(placing = false).also { it.bind(next, index) }
            }
        }

        /** Every block a member that changed since the last field can reach, or every block near the field when there was none. */
        private fun dirtyKeys(): Set<Long> {
            val last = field
            val keys = HashSet<Long>()
            val margin = grid.halfDiagonal * DIRTY_MARGINS
            if (last == null || kept == null) {
                next.members.forEachIndexed { i, member -> addBox(member, next.reaches[i] + margin, keys) }
                return keys
            }
            val before = last.members.withIndex().associate { (i, m) -> m.key to i }
            val near = grid.halfDiagonal + grid.cell
            next.members.forEachIndexed { i, member ->
                val j = before[member.key]
                val same = j != null && last.members[j].blend == member.blend && parentKey(last, j) == parentKey(next, i)
                if (!same) {
                    val reach = maxOf(next.reaches[i], j?.let { last.reaches[it] } ?: 0f)
                    addBox(member, reach + margin, keys, keptOnly = true)
                    addBox(member, reach + near, keys, keptOnly = false)
                }
            }
            last.members.forEachIndexed { j, member -> if (member.key !in index) addBox(member, last.reaches[j] + margin, keys, keptOnly = true) }
            for ((key, block) in map) if (block.keys.any { it !in index }) keys += key
            return keys
        }

        private fun parentKey(of: JoinedField, i: Int): MemberKey? = of.members[i].parent.takeIf { it >= 0 }?.let { of.members[it].key }

        /**
         * Every block within [grow] of [member]'s box, or with [keptOnly] only those the last surface crossed: a block it did not cross gains
         * surface only where a member's term falls below zero, inside the member's box grown by its reach.
         */
        private fun addBox(member: JoinedMember, grow: Float, into: MutableSet<Long>, keptOnly: Boolean = false) {
            val g = Vec3(grow, grow, grow)
            grid.blocksIn(member.low - g, member.high + g) { if (!keptOnly || it in map) into += it }
        }
    }

    private enum class Stage { READ, READ_DONE, SEAMS, SEAMS_DONE, QUADS, RESOLVE, PLACE, GATHER, CUT, FINISH, DONE }
}
