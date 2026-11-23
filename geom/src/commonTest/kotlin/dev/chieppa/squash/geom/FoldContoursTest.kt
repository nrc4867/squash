package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

private const val RINGS = 11
private const val AROUND = 24
private const val LENGTH = 2f

/** The faces' own contour where the smooth contour misses it, as a fold whose vertex normals never turn edge on does. */
class FoldContoursTest {
    private fun tube(normal: (Float, Float) -> Vec3): Triple<FloatArray, FloatArray, IntArray> {
        val positions = FloatArray(RINGS * AROUND * 3)
        val normals = FloatArray(RINGS * AROUND * 3)
        for (ring in 0 until RINGS) for (k in 0 until AROUND) {
            val angle = 2.0 * PI * (k + 0.5) / AROUND
            val at = (ring * AROUND + k) * 3
            val y = cos(angle).toFloat()
            val z = sin(angle).toFloat()
            positions[at] = LENGTH * ring / (RINGS - 1)
            positions[at + 1] = y
            positions[at + 2] = z
            val n = normal(y, z)
            normals[at] = n.x
            normals[at + 1] = n.y
            normals[at + 2] = n.z
        }
        val triangles = ArrayList<Int>()
        for (ring in 0 until RINGS - 1) for (k in 0 until AROUND) {
            val a = ring * AROUND + k
            val b = ring * AROUND + (k + 1) % AROUND
            triangles += listOf(a, a + AROUND, b, b, a + AROUND, b + AROUND)
        }
        return Triple(positions, normals, triangles.toIntArray())
    }

    private val toViewer = { _: Vec3 -> Vec3(0f, 0f, 1f) }

    @Test
    fun whereTheSmoothContourRunsAlongTheFacesThereIsNoFold() {
        val (positions, normals, triangles) = tube { y, z -> Vec3(0f, y, z) }
        val lines = TriangleLines(positions, normals, triangles)
        val smooth = lines.smoothContours(toViewer)
        assertTrue(smooth.isNotEmpty(), "the tube's sides turn edge on")
        assertTrue(lines.foldContours(toViewer, smooth).isEmpty(), "the faces' contour lies along the smooth one")
    }

    @Test
    fun whereTheVertexNormalsNeverTurnEdgeOnTheFacesContourIsAFold() {
        val (positions, normals, triangles) = tube { _, _ -> Vec3(0f, 0f, 1f) }
        val lines = TriangleLines(positions, normals, triangles)
        val smooth = lines.smoothContours(toViewer)
        assertTrue(smooth.isEmpty(), "normals all facing the viewer turn edge on nowhere")
        val folds = lines.foldContours(toViewer, smooth)
        val points = folds.flatMap { run -> run.points.map { it.position } }
        assertTrue(folds.size >= 2, "both sides of the tube fold: ${folds.size}")
        assertTrue(points.all { abs(abs(it.y) - 1f) < 0.05f }, "each fold runs along a side the fill ends at")
        assertTrue(points.maxOf { it.x } - points.minOf { it.x } > LENGTH * 0.9f, "a fold runs the tube's length")
    }
}
