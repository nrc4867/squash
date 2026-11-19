package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SIDE = 6

/** A flat square grid in the xy plane, [SIDE] points a side over 0 to 1, two triangles a square, facing +z. */
private fun grid(): TriangleLines {
    val positions = FloatArray(SIDE * SIDE * 3)
    val normals = FloatArray(SIDE * SIDE * 3)
    for (j in 0 until SIDE) for (i in 0 until SIDE) {
        val v = j * SIDE + i
        positions[v * 3] = i / (SIDE - 1f)
        positions[v * 3 + 1] = j / (SIDE - 1f)
        normals[v * 3 + 2] = 1f
    }
    val triangles = ArrayList<Int>()
    for (j in 0 until SIDE - 1) for (i in 0 until SIDE - 1) {
        val a = j * SIDE + i
        triangles += listOf(a, a + 1, a + SIDE + 1, a, a + SIDE + 1, a + SIDE)
    }
    return TriangleLines(positions, normals, triangles.toIntArray())
}

/** A zero curve carries a second value read where it crosses each edge, and keeps only the triangles asked for. */
class CarriedCurvesTest {
    private val lines = grid()
    private val xs = FloatArray(SIDE * SIDE) { (it % SIDE) / (SIDE - 1f) - 0.45f }
    private val ys = FloatArray(SIDE * SIDE) { (it / SIDE) / (SIDE - 1f) }

    @Test
    fun aCurveCarriesTheValueWhereItCrosses() {
        val curve = lines.zeroCurvesCarrying(xs, ys) { true }.single()
        assertEquals(curve.points.size, curve.carried.size)
        for ((i, point) in curve.points.withIndex()) {
            assertEquals(0.45f, point.position.x, 1e-5f)
            assertEquals(point.position.y, curve.carried[i], 1e-5f)
        }
    }

    @Test
    fun onlyTheTrianglesKeptAreCrossed() {
        val triangles = lines.triangles
        val curves = lines.zeroCurvesCarrying(xs, ys) { t -> (0 until 3).all { ys[triangles[t * 3 + it]] <= 0.4f } }
        assertTrue(curves.isNotEmpty())
        assertTrue(curves.all { curve -> curve.carried.all { abs(it) <= 0.4f + 1e-5f } })
    }
}
