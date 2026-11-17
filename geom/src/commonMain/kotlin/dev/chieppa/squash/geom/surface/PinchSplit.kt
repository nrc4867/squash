package dev.chieppa.squash.geom.surface

/**
 * [mesh] with each vertex at an end of an edge more than two triangles share split into one copy for each fan of its triangles, a fan
 * being the triangles round it reached across edges exactly two triangles share. Where a sheet thinner than a cell, a talon's point or a
 * grazing sliver, crosses a cell face along two of its corners, the cells either side each hold one vertex whose sheet crosses that face
 * twice, and the edge between the two vertices collects the quads of all four of the face's edges. Splitting both ends gives each pair
 * of those quads its own edge, as the face's other pairing of its four crossings would: the surface is closed and manifold, pinched where
 * the sheet is thinner than the cell. Each copy holds what its vertex holds and lies where it lies. The mesh itself when no edge is shared
 * by more than two triangles.
 */
internal fun pinchSplit(mesh: SurfaceMesh): SurfaceMesh {
    val triangles = mesh.triangles
    val shared = HashMap<Long, Int>(triangles.size)
    for (t in 0 until mesh.triangleCount) for (c in 0 until 3) shared.merge(edgeKey(triangles[t * 3 + c], triangles[t * 3 + (c + 1) % 3]), 1, Int::plus)
    val pinched = HashSet<Int>()
    for ((key, count) in shared) if (count > 2) {
        pinched += (key ushr Int.SIZE_BITS).toInt()
        pinched += key.toInt()
    }
    if (pinched.isEmpty()) return mesh
    val around = HashMap<Int, MutableList<Int>>()
    for (t in 0 until mesh.triangleCount) for (c in 0 until 3) {
        val v = triangles[t * 3 + c]
        if (v in pinched) around.getOrPut(v) { ArrayList() } += t
    }
    val split = triangles.copyOf()
    val sources = ArrayList<Int>()
    var next = mesh.vertexCount
    for ((v, fan) in around) {
        val fans = fansOf(v, fan, triangles, shared)
        val copies = IntArray(fans.count) { k -> if (k == 0) v else next++.also { sources += v } }
        fan.forEachIndexed { i, t ->
            for (c in 0 until 3) if (triangles[t * 3 + c] == v) split[t * 3 + c] = copies[fans.of[i]]
        }
    }
    if (sources.isEmpty()) return mesh
    return copiedWith(mesh, sources.toIntArray(), split)
}

private fun edgeKey(a: Int, b: Int): Long = (minOf(a, b).toLong() shl Int.SIZE_BITS) or maxOf(a, b).toLong()

/** Which fan each of the triangles [fan] round [v] lies in, and how many fans there are. */
private class Fans(val of: IntArray, val count: Int)

private fun fansOf(v: Int, fan: List<Int>, triangles: IntArray, shared: Map<Long, Int>): Fans {
    val parent = IntArray(fan.size) { it }
    fun root(i: Int): Int {
        var r = i
        while (parent[r] != r) r = parent[r]
        return r
    }
    val byEdge = HashMap<Int, Int>()
    fan.forEachIndexed { i, t ->
        for (c in 0 until 3) {
            val w = triangles[t * 3 + c]
            if (w == v || shared[edgeKey(v, w)] != 2) continue
            val other = byEdge.put(w, i) ?: continue
            val a = root(i)
            val b = root(other)
            if (a != b) parent[a] = b
        }
    }
    val label = HashMap<Int, Int>()
    val of = IntArray(fan.size) { i -> label.getOrPut(root(i)) { label.size } }
    return Fans(of, label.size)
}

/** [mesh] with [triangles] and, after its own vertices, a copy of each vertex in [sources]. */
private fun copiedWith(mesh: SurfaceMesh, sources: IntArray, triangles: IntArray): SurfaceMesh {
    val count = mesh.vertexCount + sources.size
    fun floats(values: FloatArray, width: Int) = values.copyOf(count * width).also { out ->
        sources.forEachIndexed { k, v -> values.copyInto(out, (mesh.vertexCount + k) * width, v * width, (v + 1) * width) }
    }
    fun ints(values: IntArray, width: Int) = values.copyOf(count * width).also { out ->
        sources.forEachIndexed { k, v -> values.copyInto(out, (mesh.vertexCount + k) * width, v * width, (v + 1) * width) }
    }
    return SurfaceMesh(
        mesh.cell, floats(mesh.positions, 3), floats(mesh.normals, 3), triangles, ints(mesh.winners, 1), ints(mesh.parents, 1),
        floats(mesh.shares, 1), floats(mesh.winnerOwn, 1), floats(mesh.parentOwn, 1), ints(mesh.held, HELD), floats(mesh.heldShares, HELD),
        floats(mesh.heldU, HELD), floats(mesh.heldV, HELD), floats(mesh.heldOwn, HELD), mesh.blocks, mesh.channels, quadCount = mesh.quadCount,
    )
}
