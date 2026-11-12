package dev.chieppa.squash.geom

private const val MIN_TRIANGLE_AREA = 1e-9f

/** Mesh checks. [check] returns what it found wrong, empty when the mesh holds. */
object MeshInvariants {
    /** Finite points, no degenerate triangles, and no holes except along the rings in [openRings] and an open patch's first and last columns. */
    fun check(mesh: PatchMesh, openRings: Set<Int>): List<String> =
        finite(mesh) + degenerate(mesh) + watertight(mesh, openRings)

    /** Which points of [patch] are corners of a cell with a buried corner, or null when it buries none. */
    private fun nearBuried(patch: Patch): BooleanArray? {
        val buried = patch.buried ?: return null
        val near = BooleanArray(buried.size)
        for (ring in 0 until patch.nu - 1) for (segment in 0 until patch.segments) {
            val next = (segment + 1) % patch.nv
            val corners = intArrayOf(patch.index(ring, segment), patch.index(ring, next), patch.index(ring + 1, segment), patch.index(ring + 1, next))
            if (corners.any { buried[it] }) {
                corners.forEach { near[it] = true }
                if (patch.isPole(ring)) near[patch.index(ring, 0)] = true
                if (patch.isPole(ring + 1)) near[patch.index(ring + 1, 0)] = true
            }
        }
        return near
    }

    private fun finite(mesh: PatchMesh): List<String> =
        if (mesh.patch.points.all { it.isFinite() }) emptyList() else listOf("${mesh.patch.name}: a point is not finite")

    private fun degenerate(mesh: PatchMesh): List<String> {
        val count = (0 until mesh.triangleCount).count { t ->
            val (a, b, c) = triangle(mesh, t)
            ((b - a) cross (c - a)).length / 2f < MIN_TRIANGLE_AREA
        }
        return if (count == 0) emptyList() else listOf("${mesh.patch.name}: $count degenerate triangles")
    }

    /**
     * Every edge is used once in each direction, except edges along an open ring, a patch's end ring where it meets other surfaces, an open
     * patch's edge columns, and the edge of the faces left out round its buried points, which have no reverse.
     */
    private fun watertight(mesh: PatchMesh, openRings: Set<Int>): List<String> {
        val uses = HashMap<Long, Int>()
        val nv = mesh.patch.nv
        val open = if (mesh.patch.openEnds) openRings + setOf(0, mesh.patch.nu - 1) else openRings
        val nearBuried = nearBuried(mesh.patch)
        val edgeColumns = setOf(0, nv - 1)
        for (t in 0 until mesh.triangleCount) {
            for (corner in 0 until 3) {
                val key = edge(mesh.indices[t * 3 + corner], mesh.indices[t * 3 + (corner + 1) % 3])
                uses[key] = (uses[key] ?: 0) + 1
            }
        }
        return uses.mapNotNull { (key, count) ->
            val from = (key ushr Int.SIZE_BITS).toInt()
            val to = key.toInt()
            val onOpenRing = from / nv == to / nv && from / nv in open
            val byBuried = nearBuried != null && nearBuried[from] && nearBuried[to]
            val onEdge = !mesh.patch.closedV && from % nv in edgeColumns && to % nv in edgeColumns
            if (count == 1 && (edge(to, from) in uses || onOpenRing || onEdge || byBuried)) null
            else "${mesh.patch.name}: edge $from to $to is used $count times without a match"
        }
    }

    /** Six times the signed volume of the triangles; positive when a closed surface faces outward. */
    fun signedVolume(mesh: PatchMesh): Float =
        (0 until mesh.triangleCount).fold(0f) { sum, t ->
            val (a, b, c) = triangle(mesh, t)
            sum + (a dot (b cross c))
        }

    private fun triangle(mesh: PatchMesh, t: Int): Triple<Vec3, Vec3, Vec3> = Triple(
        mesh.patch.point(mesh.indices[t * 3]),
        mesh.patch.point(mesh.indices[t * 3 + 1]),
        mesh.patch.point(mesh.indices[t * 3 + 2]),
    )

    private fun edge(from: Int, to: Int): Long = (from.toLong() shl Int.SIZE_BITS) or to.toLong()
}
