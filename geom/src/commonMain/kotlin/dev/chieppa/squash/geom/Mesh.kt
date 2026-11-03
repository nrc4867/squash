package dev.chieppa.squash.geom

private const val POLE_TOLERANCE = 1e-6f

/** The triangles of one patch over its grid, with every vertex's bone, u and v. A pole's triangles share one vertex. */
class PatchMesh(
    val patch: Patch,
    val indices: IntArray,
    val bones: IntArray,
    val us: FloatArray,
    val vs: FloatArray,
) {
    val vertexCount: Int get() = patch.nu * patch.nv
    val triangleCount: Int get() = indices.size / 3
}

/** One grid per patch of a part. */
data class PartMesh(val part: String, val patches: List<PatchMesh>)

object MeshBuilder {
    /** Triangulates [patch], with each vertex's bone from [boneAt] of its u. Faces point away from the axis. */
    fun build(patch: Patch, boneAt: (Float) -> Int): PatchMesh {
        val nu = patch.nu
        val nv = patch.nv
        val ringU = FloatArray(nu) { patch.slices?.get(it)?.u ?: (it.toFloat() / (nu - 1)) }
        val vertices = nu * nv
        val us = FloatArray(vertices) { ringU[it / nv] }
        val vs = FloatArray(vertices) { (it % nv).toFloat() / (if (patch.closedV) nv else nv - 1) }
        val bones = IntArray(vertices) { boneAt(us[it]) }
        val pole = BooleanArray(nu) { isPole(patch, it) }
        fun vertex(ring: Int, segment: Int) = patch.index(ring, if (pole[ring]) 0 else segment)

        val indices = ArrayList<Int>(vertices * 6)
        val segmentCount = if (patch.closedV) nv else nv - 1
        for (ring in 0 until nu - 1) {
            for (segment in 0 until segmentCount) {
                val next = (segment + 1) % nv
                val a = vertex(ring, segment)
                val b = vertex(ring, next)
                val c = vertex(ring + 1, next)
                val d = vertex(ring + 1, segment)
                if (a != b) indices += listOf(a, c, b)
                if (c != d) indices += listOf(a, d, c)
            }
        }
        return PatchMesh(patch, indices.toIntArray(), bones, us, vs)
    }

    private fun isPole(patch: Patch, ring: Int): Boolean {
        val first = patch.point(patch.index(ring, 0))
        return (1 until patch.nv).all { (patch.point(patch.index(ring, it)) - first).length < POLE_TOLERANCE }
    }
}
