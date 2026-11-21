package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.geom.Vec3

/** A surface as a grid of [nu] rings by [nv] [points] each, ring after ring, closed round each ring where [closedV]. */
class PointGrid(val nu: Int, val nv: Int, val closedV: Boolean, val points: List<Vec3>) {
    /** Its faces as triangles, three indices into [points] each, two to a cell of the grid. */
    fun triangles(): IntArray {
        val segments = if (closedV) nv else nv - 1
        val out = IntArray(maxOf(0, nu - 1) * maxOf(0, segments) * 6)
        var k = 0
        for (i in 0 until nu - 1) for (j in 0 until segments) {
            val a = i * nv + j
            val b = (i + 1) * nv + j
            val c = (i + 1) * nv + (j + 1) % nv
            val d = i * nv + (j + 1) % nv
            out[k++] = a; out[k++] = b; out[k++] = c
            out[k++] = a; out[k++] = c; out[k++] = d
        }
        return out
    }
}
