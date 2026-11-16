package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
import dev.chieppa.squash.geom.field.JoinedField

/**
 * Meshes a joined field into one surface with cells of several sizes: the body at [cell], and around each member whose radius asks for
 * it (see [CellLevels]) cells of half or a quarter of that, each level by a [SurfaceMesher] of its own over the blocks its level holds,
 * the levels joined across their boundaries by [LevelSeams]. With every member at the body's cell the surface is level 0's mesh itself.
 * Each level keeps its blocks, so the next field meshes again only where it differs. Not safe for two runs at once.
 */
class AdaptiveMesher(val cell: Float, private val reads: FieldReads = CpuFieldReads) {
    private val meshers = ArrayList<SurfaceMesher>()
    private var last: SurfaceMesh? = null
    private var lastParts: List<SurfaceMesh> = emptyList()
    private var lastBases: IntArray = IntArray(0)

    private fun mesher(level: Int): SurfaceMesher {
        while (meshers.size <= level) meshers += SurfaceMesher(cell / (1 shl meshers.size), reads)
        return meshers[level]
    }

    /**
     * A run meshing [next] with its members at [levels] (see [CellLevels.memberLevels]): hand out its batches of work with [Run.next],
     * each batch's pieces independent of one another, until it has none, then read [Run.mesh]. Level 0 reads grown members' channels from
     * [lent] where it can, as [SurfaceMesher.begin] says.
     */
    fun begin(next: JoinedField, levels: IntArray, lent: ChannelSource? = null): Run {
        val plan = CellLevels.of(next, cell, levels)
        val runs = List(plan.count) { level -> mesher(level).begin(next, if (level == 0) lent else null, plan.region(level)) }
        return Run(plan, runs)
    }

    /** One run over a field, its levels' runs side by side. */
    inner class Run internal constructor(private val plan: CellLevels, private val runs: List<SurfaceMesher.MeshRun>) {
        private val running = BooleanArray(runs.size) { true }
        private var joining: Joining? = null
        private var done: SurfaceMesh? = null

        /** The mesh the run made, once [next] has given null. */
        val mesh: SurfaceMesh get() = checkNotNull(done) { "The run has work left" }

        /** The next batch of work, or null when the run is done. */
        fun next(): List<() -> Unit>? {
            if (done != null) return null
            val batch = ArrayList<() -> Unit>()
            for (level in runs.indices) {
                if (!running[level]) continue
                val work = runs[level].next()
                if (work == null) running[level] = false else batch += work
            }
            if (batch.isNotEmpty()) return batch
            if (runs.size == 1) {
                finish(listOf(runs[0].mesh), intArrayOf(0), null)
                return null
            }
            val pending = joining
            if (pending == null) {
                val made = Joining(runs.map { it.mesh })
                joining = made
                return made.tasks()
            }
            finish(pending.parts, pending.bases, pending.mesh())
            return null
        }

        private fun finish(parts: List<SurfaceMesh>, bases: IntArray, joined: SurfaceMesh?) {
            val mesh = joined ?: parts[0]
            if (joined != null) {
                val before = last
                if (before != null) {
                    val previousOf = IntArray(mesh.vertexCount) { -1 }
                    parts.forEachIndexed { level, part ->
                        val was = lastParts.getOrNull(level) ?: return@forEachIndexed
                        val from = bases[level]
                        val to = lastBases[level]
                        when {
                            part === was -> for (v in 0 until part.vertexCount) previousOf[from + v] = to + v
                            part.change?.previous === was -> {
                                val map = checkNotNull(part.change).previousOf
                                for (v in map.indices) if (map[v] >= 0) previousOf[from + v] = to + map[v]
                            }
                        }
                    }
                    before.change = null
                    mesh.change = MeshChange(before, previousOf)
                }
            }
            last = mesh
            lastParts = parts
            lastBases = bases
            done = mesh
        }

        /** The levels' meshes joined: their vertices one after another, their triangles and the seams between neighbouring levels. */
        private inner class Joining(val parts: List<SurfaceMesh>) {
            val bases = IntArray(parts.size + 1).also { b -> parts.forEachIndexed { i, m -> b[i + 1] = b[i] + m.vertexCount } }
            private val total = bases[parts.size]
            private val positions = FloatArray(total * 3).also { out -> parts.forEachIndexed { i, m -> m.positions.copyInto(out, bases[i] * 3) } }
            private val seams = arrayOfNulls<LevelSeams>(parts.size - 1)

            fun tasks(): List<() -> Unit> = (0 until parts.size - 1).map { level ->
                {
                    val coarse = mesher(level)
                    val fine = mesher(level + 1)
                    seams[level] = LevelSeams(
                        LevelSeams.Side(coarse.keptGrid, coarse.keptBlocks, parts[level], bases[level]),
                        LevelSeams.Side(fine.keptGrid, fine.keptBlocks, parts[level + 1], bases[level + 1]),
                        plan.region(level + 1),
                        positions,
                    ).also { it.join() }
                }
            }

            fun mesh(): SurfaceMesh {
                val normals = FloatArray(total * 3)
                val winners = IntArray(total)
                val parents = IntArray(total)
                val shares = FloatArray(total)
                val winnerOwn = FloatArray(total)
                val parentOwn = FloatArray(total)
                val held = IntArray(total * HELD)
                val heldShares = FloatArray(total * HELD)
                val heldU = FloatArray(total * HELD)
                val heldV = FloatArray(total * HELD)
                val heldOwn = FloatArray(total * HELD)
                var triangleCount = seams.sumOf { (it?.quads?.size ?: 0) + (it?.triangles?.size ?: 0) }
                for (m in parts) triangleCount += m.triangles.size
                val triangles = IntArray(triangleCount)
                var quadCount = seams.sumOf { (it?.quads?.size ?: 0) / 6 }
                var t = 0
                parts.forEachIndexed { i, m ->
                    val b = bases[i]
                    m.normals.copyInto(normals, b * 3)
                    m.winners.copyInto(winners, b)
                    m.parents.copyInto(parents, b)
                    m.shares.copyInto(shares, b)
                    m.winnerOwn.copyInto(winnerOwn, b)
                    m.parentOwn.copyInto(parentOwn, b)
                    m.held.copyInto(held, b * HELD)
                    m.heldShares.copyInto(heldShares, b * HELD)
                    m.heldU.copyInto(heldU, b * HELD)
                    m.heldV.copyInto(heldV, b * HELD)
                    m.heldOwn.copyInto(heldOwn, b * HELD)
                    for (k in 0 until m.quadCount * 6) triangles[t++] = m.triangles[k] + b
                    quadCount += m.quadCount
                }
                for (seam in seams) if (seam != null) for (k in 0 until seam.quads.size) triangles[t++] = seam.quads[k]
                parts.forEachIndexed { i, m -> for (k in m.quadCount * 6 until m.triangles.size) triangles[t++] = m.triangles[k] + bases[i] }
                for (seam in seams) if (seam != null) for (k in 0 until seam.triangles.size) triangles[t++] = seam.triangles[k]
                return SurfaceMesh(
                    cell, positions, normals, triangles, winners, parents, shares, winnerOwn, parentOwn, held, heldShares, heldU, heldV, heldOwn,
                    parts.sumOf { it.blocks }, parts[0].channels, quadCount = quadCount,
                )
            }
        }
    }
}
