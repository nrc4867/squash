package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

private const val RINGS = 21
private const val AROUND = 24
private const val LENGTH = 4f
private const val HALF = 1.7f

/** A tube's smooth contours kept to where a value is at least zero end where that value does, cut across the faces. */
class KeptContoursTest {
    @Test
    fun aContourKeptToHalfTheTubeEndsWhereItsValueCrossesZero() {
        val positions = FloatArray(RINGS * AROUND * 3)
        val normals = FloatArray(RINGS * AROUND * 3)
        for (ring in 0 until RINGS) for (k in 0 until AROUND) {
            val angle = 2.0 * PI * (k + 0.5) / AROUND
            val at = (ring * AROUND + k) * 3
            positions[at] = LENGTH * ring / (RINGS - 1)
            positions[at + 1] = cos(angle).toFloat()
            positions[at + 2] = sin(angle).toFloat()
            normals[at + 1] = cos(angle).toFloat()
            normals[at + 2] = sin(angle).toFloat()
        }
        val triangles = ArrayList<Int>()
        for (ring in 0 until RINGS - 1) for (k in 0 until AROUND) {
            val a = ring * AROUND + k
            val b = ring * AROUND + (k + 1) % AROUND
            val c = a + AROUND
            val d = b + AROUND
            triangles += listOf(a, c, b, b, c, d)
        }
        val kept = FloatArray(RINGS * AROUND) { positions[it * 3] - HALF }
        val toViewer = { _: Vec3 -> Vec3(0f, 0f, 1f) }
        val whole = TriangleLines(positions, normals, triangles.toIntArray()).smoothContours(toViewer)
        val cut = TriangleLines(positions, normals, triangles.toIntArray(), kept).smoothContours(toViewer)
        assertTrue(whole.isNotEmpty() && cut.isNotEmpty(), "whole ${whole.size} cut ${cut.size}")
        val xs = cut.flatMap { run -> run.points.map { it.position.x } }
        assertTrue(xs.all { it >= HALF - 1e-4f }, "every kept point lies where the value is at least zero")
        assertTrue(xs.any { it < HALF + 1e-3f }, "a kept contour reaches where the value crosses zero")
        assertTrue(whole.flatMap { run -> run.points.map { it.position.x } }.any { it < 1f }, "the whole contour runs the tube's length")
    }
}
