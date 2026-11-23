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
    private fun tube(rings: Int = RINGS, length: Float = LENGTH, normal: (Float, Float) -> Vec3): Triple<FloatArray, FloatArray, IntArray> {
        val positions = FloatArray(rings * AROUND * 3)
        val normals = FloatArray(rings * AROUND * 3)
        for (ring in 0 until rings) for (k in 0 until AROUND) {
            val angle = 2.0 * PI * (k + 0.5) / AROUND
            val at = (ring * AROUND + k) * 3
            val y = cos(angle).toFloat()
            val z = sin(angle).toFloat()
            positions[at] = length * ring / (rings - 1)
            positions[at + 1] = y
            positions[at + 2] = z
            val n = normal(y, z)
            normals[at] = n.x
            normals[at + 1] = n.y
            normals[at + 2] = n.z
        }
        val triangles = ArrayList<Int>()
        for (ring in 0 until rings - 1) for (k in 0 until AROUND) {
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

    @Test
    fun aShortFoldAlongTheCutEdgeIsAFleckAndALongOneTheHemsOutline() {
        val onLip = { positions: FloatArray -> FloatArray(positions.size / 3) { 0.01f } }
        val deep = { positions: FloatArray -> FloatArray(positions.size / 3) { 100f } }
        val (short, shortNormals, shortTriangles) = tube { _, _ -> Vec3(0f, 0f, 1f) }
        assertTrue(TriangleLines(short, shortNormals, shortTriangles, deep(short)).foldContours(toViewer, emptyList()).isNotEmpty(), "a short fold deep in the cloth stays")
        assertTrue(TriangleLines(short, shortNormals, shortTriangles, onLip(short)).foldContours(toViewer, emptyList()).isEmpty(), "a short fold on the hem's lip is a fleck")
        val (long, longNormals, longTriangles) = tube(rings = RINGS * 3, length = LENGTH * 3) { _, _ -> Vec3(0f, 0f, 1f) }
        assertTrue(TriangleLines(long, longNormals, longTriangles, onLip(long)).foldContours(toViewer, emptyList()).isNotEmpty(), "a long fold on the hem's lip is its outline")
    }

    @Test
    fun aSmoothContourWhereNoFaceTurnsEdgeOnIsLeftOut() {
        val (positions, normals, triangles) = tube { y, z ->
            if (z > 0.6f && abs(y) < 0.6f) Vec3(0f, y, -z) else Vec3(0f, y, z)
        }
        val lines = TriangleLines(positions, normals, triangles)
        val points = lines.smoothContours(toViewer).flatMap { run -> run.points.map { it.position } }
        assertTrue(points.isNotEmpty(), "the tube's sides still turn edge on")
        assertTrue(points.all { abs(it.y) > 0.6f }, "no line runs round the front where only the normals turn away")
    }
}
