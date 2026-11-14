package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField

/** Vertices one piece of placing work takes, blocks one piece of gathering takes and quads one piece of cutting takes. */
private const val PLACE_CHUNK = 128
private const val GATHER_CHUNK = 64
private const val TRIANGLE_CHUNK = 8192

/**
 * The blocks of one run gathered into one mesh: every block's vertices numbered in key order, its quads joined into a vertex graph, the
 * vertices of the blocks read again in [fresh] and their neighbours relaxed and placed, each taken from the block as last meshed in
 * [previous] where that block placed it alike, and the quads cut into triangles.
 */
internal class MeshAssembly(
    private val grid: BlockGrid,
    private val field: JoinedField,
    private val map: Map<Long, SurfaceBlock>,
    private val fresh: Set<Long>,
    private val previous: Map<Long, SurfaceBlock>,
    private val last: SurfaceMesh? = null,
    private val lastField: JoinedField? = null,
) {
    private val order: List<SurfaceBlock> = map.keys.sorted().map(map::getValue)
    private val bases = IntArray(order.size + 1)
    private val quadStarts = IntArray(order.size + 1)
    private val total: Int
    private val blockOf: IntArray
    private val allQuads: IntArray
    private val allFacing: ByteArray
    private var quads = IntArray(0)
    private var facing = ByteArray(0)

    init {
        order.forEachIndexed { b, block ->
            bases[b + 1] = bases[b] + block.vertexCount
            quadStarts[b + 1] = quadStarts[b] + block.quadCount
        }
        total = bases[order.size]
        blockOf = IntArray(total)
        allQuads = IntArray(quadStarts[order.size] * 4)
        allFacing = ByteArray(quadStarts[order.size])
    }

    /** Pieces of work numbering every block's vertices and joining its quads' corners to them, -1 for a corner no block holds. */
    fun resolveTasks(): List<() -> Unit> {
        val position = HashMap<Long, Int>(order.size * 2)
        order.forEachIndexed { b, block -> position[block.key] = b }
        return order.indices.chunked(GATHER_CHUNK).map { chunk -> { for (b in chunk) resolve(b, position) } }
    }

    private fun resolve(b: Int, position: Map<Long, Int>) {
        val block = order[b]
        blockOf.fill(b, bases[b], bases[b + 1])
        val nearBase = IntArray(8)
        val near = arrayOfNulls<SurfaceBlock>(8)
        for (code in 0 until 8) {
            val key = grid.key(block.bx - (code and 1), block.by - ((code shr 1) and 1), block.bz - ((code shr 2) and 1))
            val at = if (code == 0) b else position[key]
            near[code] = at?.let { order[it] }
            nearBase[code] = at?.let { bases[it] } ?: -1
        }
        for (k in 0 until block.quadCount) {
            val q = quadStarts[b] + k
            for (c in 0 until 4) {
                val ref = block.quads[k * SurfaceBlock.QUAD_INTS + c]
                val code = -SurfaceBlock.refDx(ref) or (-SurfaceBlock.refDy(ref) shl 1) or (-SurfaceBlock.refDz(ref) shl 2)
                val first = near[code]?.cellFirst?.get(SurfaceBlock.refCell(ref)) ?: -1
                allQuads[q * 4 + c] = if (first < 0) -1 else nearBase[code] + first + SurfaceBlock.refSheet(ref)
            }
            allFacing[q] = block.quads[k * SurfaceBlock.QUAD_INTS + 4].toByte()
        }
    }

    /** Keeps the quads whose four corners all have a vertex, in order. */
    private fun compact() {
        var q = 0
        for (k in allFacing.indices) {
            if (allQuads[k * 4] < 0 || allQuads[k * 4 + 1] < 0 || allQuads[k * 4 + 2] < 0 || allQuads[k * 4 + 3] < 0) continue
            allQuads.copyInto(allQuads, q * 4, k * 4, k * 4 + 4)
            allFacing[q] = allFacing[k]
            q++
        }
        quads = allQuads.copyOf(q * 4)
        facing = allFacing.copyOf(q)
        triangles = IntArray(q * 6)
    }

    private fun block(v: Int): SurfaceBlock = order[blockOf[v]]
    private fun local(v: Int): Int = v - bases[blockOf[v]]

    private fun initial(v: Int): Vec3 {
        val block = block(v)
        val l = local(v)
        return Vec3(block.initial[l * 3], block.initial[l * 3 + 1], block.initial[l * 3 + 2])
    }

    /** Pieces of work placing every vertex of a fresh block and every vertex beside one. */
    fun placeTasks(): List<() -> Unit> {
        compact()
        val placing = BooleanArray(total)
        order.forEachIndexed { b, block -> if (block.key in fresh) placing.fill(true, bases[b], bases[b + 1]) }
        val quadCount = quads.size / 4
        val beside = placing.copyOf()
        for (q in 0 until quadCount) for (c in 0 until 4) if (placing[quads[q * 4 + c]]) {
            beside[quads[q * 4 + (c + 1) % 4]] = true
            beside[quads[q * 4 + (c + 3) % 4]] = true
        }
        beside.copyInto(placing)
        val start = IntArray(total + 1)
        for (q in 0 until quadCount) for (c in 0 until 4) {
            val v = quads[q * 4 + c]
            if (placing[v]) start[v + 1] += 2
        }
        for (v in 0 until total) start[v + 1] += start[v]
        val links = IntArray(start[total])
        val filled = IntArray(total)
        for (q in 0 until quadCount) for (c in 0 until 4) {
            val v = quads[q * 4 + c]
            if (!placing[v]) continue
            links[start[v] + filled[v]++] = quads[q * 4 + (c + 1) % 4]
            links[start[v] + filled[v]++] = quads[q * 4 + (c + 3) % 4]
        }
        var chosenCount = 0
        for (v in 0 until total) if (placing[v]) chosenCount++
        val chosen = IntArray(chosenCount)
        var at = 0
        for (v in 0 until total) if (placing[v]) chosen[at++] = v
        val placer = VertexPlacement(grid, field)
        return (0 until chosenCount step PLACE_CHUNK).map { from ->
            {
                for (i in from until minOf(chosenCount, from + PLACE_CHUNK)) {
                    val v = chosen[i]
                    val block = block(v)
                    val l = local(v)
                    val own = initial(v)
                    var sum = Vec3.ZERO
                    var n = 0
                    var k = start[v]
                    while (k < start[v + 1]) {
                        val other = links[k]
                        var seen = false
                        for (j in start[v] until k) if (links[j] == other) seen = true
                        if (!seen) {
                            sum += initial(other)
                            n++
                        }
                        k++
                    }
                    val mean = if (n == 0) null else sum * (1f / n)
                    val begin = VertexPlacement.relaxed(own, mean, block.cellLow(grid, block.vertexCell[l]), grid.cell)
                    val before = previous[block.key]
                    if (before == null || before === block || !block.takeVertex(l, begin, before)) placer.place(block, l, begin)
                }
            }
        }
    }

    private val positions = FloatArray(total * 3)
    private val normals = FloatArray(total * 3)
    private val winners = IntArray(total)
    private val parents = IntArray(total)
    private val shares = FloatArray(total)
    private val winnerOwn = FloatArray(total)
    private val parentOwn = FloatArray(total)
    private val held = IntArray(total * HELD)
    private val heldShares = FloatArray(total * HELD)
    private val heldU = FloatArray(total * HELD)
    private val heldV = FloatArray(total * HELD)
    private val heldOwn = FloatArray(total * HELD)
    private var triangles = IntArray(0)

    /** Pieces of work gathering every block's placed vertices into the mesh's arrays. */
    fun gatherTasks(): List<() -> Unit> = order.indices.chunked(GATHER_CHUNK).map { blocks -> { blocks.forEach(::gather) } }

    private fun gather(b: Int) {
        val block = order[b]
        val first = bases[b]
        val n = block.vertexCount
        block.positions.copyInto(positions, first * 3, 0, n * 3)
        block.normals.copyInto(normals, first * 3, 0, n * 3)
        val c = block.coordinates
        for (v in 0 until n) {
            val g = first + v
            winners[g] = c.winners[v].let { if (it >= 0) block.members[it] else -1 }
            parents[g] = c.parents[v].let { if (it >= 0) block.members[it] else -1 }
            shares[g] = c.shares[v]
            winnerOwn[g] = c.winnerOwn[v]
            parentOwn[g] = c.parentOwn[v]
            for (k in 0 until HELD) held[g * HELD + k] = c.held[v * HELD + k].let { if (it >= 0) block.members[it] else -1 }
        }
        c.heldShares.copyInto(heldShares, first * HELD, 0, n * HELD)
        c.heldU.copyInto(heldU, first * HELD, 0, n * HELD)
        c.heldV.copyInto(heldV, first * HELD, 0, n * HELD)
        c.heldOwn.copyInto(heldOwn, first * HELD, 0, n * HELD)
    }

    private val keys = LongArray(order.size) { order[it].key }
    private val previousOf = IntArray(total) { -1 }

    /**
     * Pieces of work cutting each quad along its shorter diagonal into two triangles facing out, once every vertex is gathered, and
     * matching the vertices against the last mesh.
     */
    fun triangleTasks(): List<() -> Unit> {
        val count = quads.size / 4
        val cutting = (0 until count step TRIANGLE_CHUNK).map { from -> { for (q in from until minOf(count, from + TRIANGLE_CHUNK)) cut(q) } }
        val matching = if (last != null && lastField != null) matchTasks(keys, bases, gatheredView, field, last, lastField, previousOf) else emptyList()
        return cutting + matching
    }

    /** The gathered vertices as a mesh, for matching; its triangles are not read. */
    private val gatheredView: SurfaceMesh by lazy {
        SurfaceMesh(grid.cell, positions, normals, IntArray(0), winners, parents, shares, winnerOwn, parentOwn, held, heldShares, heldU, heldV, heldOwn, order.size)
    }

    private fun apart(a: Int, b: Int): Float {
        val dx = positions[a * 3] - positions[b * 3]
        val dy = positions[a * 3 + 1] - positions[b * 3 + 1]
        val dz = positions[a * 3 + 2] - positions[b * 3 + 2]
        return dx * dx + dy * dy + dz * dz
    }

    private fun cut(q: Int) {
        val outward = facing[q].toInt() and 1 == 1
        val a = quads[q * 4]
        val b = quads[q * 4 + if (outward) 1 else 3]
        val c = quads[q * 4 + 2]
        val d = quads[q * 4 + if (outward) 3 else 1]
        val o = q * 6
        val across = apart(a, c) <= apart(b, d)
        triangles[o] = a
        triangles[o + 1] = b
        triangles[o + 2] = if (across) c else d
        triangles[o + 3] = if (across) a else b
        triangles[o + 4] = c
        triangles[o + 5] = d
    }

    /** The mesh, once its vertices are gathered and its quads cut. */
    fun mesh(): SurfaceMesh = SurfaceMesh(
        grid.cell, positions, normals, triangles, winners, parents, shares, winnerOwn, parentOwn, held, heldShares, heldU, heldV, heldOwn, order.size,
        ChannelSource(grid.cell, map), keys, bases,
    ).also { mesh ->
        if (last != null && lastField != null) {
            last.change = null
            mesh.change = MeshChange(last, previousOf)
        }
    }
}
