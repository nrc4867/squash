package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.surface.SurfaceMesh

/**
 * A mesh's quads, each two of its triangles, cut instead into four triangles round a new vertex at the quad's middle, so that light and
 * shade read across a quad the same whichever way its diagonal ran: the mesh's [SurfaceMesh.vertexCount]
 * vertices first, then one a quad. A middle vertex takes the mean of its four corners' values.
 */
class QuadFan(mesh: SurfaceMesh) {
    /** How many vertices the mesh had, and how many the fan has. */
    val corners = mesh.vertexCount
    val count: Int = corners + mesh.triangleCount / 2

    /** Each middle vertex's four corners, in order round its quad. */
    private val around = IntArray((count - corners) * 4)
    val triangles = IntArray((count - corners) * 12)

    /** Each vertex's place: the mesh's own at once, each middle's once [build] and then [fill] have set it. */
    val positions: FloatArray

    private val meshTriangles = mesh.triangles

    init {
        positions = FloatArray(count * 3).also { mesh.positions.copyInto(it, 0, 0, corners * 3) }
    }

    /** Sets out quads [from] until [to]: each one's corners and its four triangles round its middle. */
    fun build(from: Int, to: Int) {
        for (q in from until to) {
            for (k in 0 until 4) around[q * 4 + k] = corner(q, k)
            for (k in 0 until 4) for (c in 0 until 3) triangles[q * 12 + k * 3 + c] = triangleCorner(q * 4 + k, c)
        }
    }

    /** Corner [k] of quad [q], read from the mesh, so it is known before [build]. */
    fun corner(q: Int, k: Int): Int = meshTriangles[q * 6 + QUAD_CORNERS[k]]

    /** Corner [c] of the fan's triangle [tri], read from the mesh, so it is known before [build]. */
    fun triangleCorner(tri: Int, c: Int): Int {
        val q = tri / 4
        val k = tri % 4
        return when (c) {
            0 -> corner(q, k)
            1 -> corner(q, (k + 1) % 4)
            else -> corners + q
        }
    }

    /** [values], [width] a vertex over the mesh's vertices, over the fan's: each middle left for [fill] to set. */
    fun spread(values: FloatArray, width: Int): FloatArray = FloatArray(count * width).also { out -> values.copyInto(out, 0, 0, corners * width) }

    /** The mean of quad [q]'s four corners' values in [values], three a corner, written into [out]. */
    fun mean(values: FloatArray, q: Int, out: FloatArray) {
        for (c in 0 until 3) {
            var sum = 0f
            for (k in 0 until 4) sum += values[around[q * 4 + k] * 3 + c]
            out[c] = sum / 4f
        }
    }

    /** The mean of quad [q]'s four corners' values in [values], one a corner. */
    fun mean(values: FloatArray, q: Int): Float {
        var sum = 0f
        for (k in 0 until 4) sum += values[around[q * 4 + k]]
        return sum / 4f
    }

    /** Sets the middles [from] until [to] of [values], [width] a vertex over the fan, to the mean of their corners. */
    fun fill(values: FloatArray, width: Int, from: Int, to: Int) {
        for (q in from until to) for (c in 0 until width) {
            var sum = 0f
            for (k in 0 until 4) sum += values[around[q * 4 + k] * width + c]
            values[(corners + q) * width + c] = sum / 4f
        }
    }

    private companion object {
        /** Where a quad's four corners lie in order among its two triangles' six. */
        val QUAD_CORNERS = intArrayOf(0, 1, 4, 5)
    }
}
