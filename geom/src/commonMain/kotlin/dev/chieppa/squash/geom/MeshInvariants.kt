package dev.chieppa.squash.geom

private const val MIN_TRIANGLE_AREA = 1e-9f

/** Mesh checks. [check] returns what it found wrong, empty when the mesh holds. */
object MeshInvariants {
    /** Finite points, no degenerate triangles, and no holes except along the rings in [openRings] and an open patch's first and last columns. */
    fun check(mesh: PatchMesh, openRings: Set<Int>): List<String> =
        finite(mesh) + degenerate(mesh) + watertight(mesh, openRings)

    private fun finite(mesh: PatchMesh): List<String> =
        if (mesh.patch.points.all { it.isFinite() }) emptyList() else listOf("${mesh.patch.name}: a point is not finite")

    private fun degenerate(mesh: PatchMesh): List<String> {
        val count = (0 until mesh.triangleCount).count { t ->
            val (a, b, c) = triangle(mesh, t)
            ((b - a) cross (c - a)).length / 2f < MIN_TRIANGLE_AREA
        }
        return if (count == 0) emptyList() else listOf("${mesh.patch.name}: $count degenerate triangles")
    }

    /** Every edge is used once in each direction, except edges along an open ring or an open patch's edge columns, which have no reverse. */
    private fun watertight(mesh: PatchMesh, openRings: Set<Int>): List<String> {
        val uses = HashMap<Long, Int>()
        val nv = mesh.patch.nv
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
            val onOpenRing = from / nv == to / nv && from / nv in openRings
            val onEdge = !mesh.patch.closedV && from % nv in edgeColumns && to % nv in edgeColumns
            if (count == 1 && (edge(to, from) in uses || onOpenRing || onEdge)) null
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
