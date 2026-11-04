package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RINGS = 25
private const val SEGMENTS = 24
private const val RADIUS = 1f
private const val LENGTH = 4f
private const val CLOSE = 0.02f
private const val END_CLEAR = 0.5f

/** A tube along x, closed in v, at no pole. */
private fun tube(): Patch {
    val points = FloatArray(RINGS * SEGMENTS * 3)
    var at = 0
    for (ring in 0 until RINGS) {
        for (segment in 0 until SEGMENTS) {
            val angle = 2.0 * PI * segment / SEGMENTS
            points[at++] = LENGTH * ring / (RINGS - 1)
            points[at++] = RADIUS * sin(angle).toFloat()
            points[at++] = RADIUS * cos(angle).toFloat()
        }
    }
    return Patch("body", RINGS, SEGMENTS, closedV = true, slices = null, points = points)
}

class LineGridTest {
    private val grid = LineGrid.of(tube(), Vec3.ZERO) { it }

    private fun alongTheSides(run: List<ContourPoint>) = run.filter { it.position.x > END_CLEAR && it.position.x < LENGTH - END_CLEAR }

    @Test
    fun aTubeSeenFromTheSideTurnsEdgeOnAlongItsSidesAndRoundItsEnds() {
        val runs = grid.contours { Vec3.Z }
        val points = runs.flatMap { it.points }
        val sides = alongTheSides(points)
        sides.forEach { assertTrue(abs(abs(it.position.y) - RADIUS) < CLOSE, "on the side: $it") }
        assertTrue(sides.any { it.position.y > 0f } && sides.any { it.position.y < 0f }, "along both sides")
        assertTrue(points.any { it.position.x == 0f } && points.any { it.position.x == LENGTH }, "from end to end")
    }

    @Test
    fun aContourHoldsNoCornersOfTheRings() {
        val toViewer = Vec3(0.3f, 0.2f, 1f).normalized()
        val run = alongTheSides(grid.contours { toViewer }.flatMap { it.points }.filter { it.position.y > 0f }).map { it.position }
        val steps = run.zipWithNext { a, b -> (b - a).normalized() }
        val sharpest = steps.zipWithNext { a, b -> a dot b }.min()
        assertTrue(sharpest > 0.8f, "a straight tube's contour runs straight: $sharpest")
    }

    @Test
    fun aViewerAlongTheTubeSeesTheRingOfItsNearEnd() {
        val points = grid.contours { Vec3(1f, 0f, 0f) }.flatMap { it.points }
        assertTrue(points.isNotEmpty())
        points.forEach { assertEquals(LENGTH, it.position.x, CLOSE) }
    }

    @Test
    fun aWeightOnTheSurfaceComesThroughTheContour() {
        val stroke = WeightStroke(listOf(Vec3(2f, RADIUS, 0f)), 0.8f, 0.6f)
        val weighted = grid.weighted { lineWeightAt(it, listOf(stroke)) }
        val points = alongTheSides(weighted.contours { Vec3.Z }.flatMap { it.points })
        assertEquals(1.6f, points.filter { it.position.y > 0f }.maxOf { it.weight }, 0.05f)
        assertEquals(1f, points.filter { it.position.y < 0f }.maxOf { it.weight }, 0.001f, "the far side of the tube is out of reach")
    }

    @Test
    fun aWeightEasesToNothingAtItsEdge() {
        val stroke = WeightStroke(listOf(Vec3.ZERO, Vec3.X), 1f, 1f)
        assertEquals(2f, lineWeightAt(Vec3(0.5f, 0.5f, 0f), listOf(stroke)), 0.001f, "within its core")
        assertEquals(1f, lineWeightAt(Vec3(0.5f, 1f, 0f), listOf(stroke)), 0.001f, "at its edge")
        val partWay = lineWeightAt(Vec3(0.5f, 0.8f, 0f), listOf(stroke))
        assertTrue(partWay in 1.2f..1.8f, "between them: $partWay")
    }

    @Test
    fun aContourLiesOnTheEdgeASawtoothShows() {
        val rings = 41
        val points = FloatArray(rings * SEGMENTS * 3)
        var at = 0
        for (ring in 0 until rings) {
            val radius = if (ring % 8 < 4) 0.6f + 0.1f * (ring % 8) else 1f - 0.1f * (ring % 8 - 4)
            for (segment in 0 until SEGMENTS) {
                val angle = 2.0 * PI * segment / SEGMENTS
                points[at++] = 0.1f * ring
                points[at++] = radius * sin(angle).toFloat()
                points[at++] = radius * cos(angle).toFloat()
            }
        }
        val teeth = LineGrid.of(Patch("body", rings, SEGMENTS, closedV = true, slices = null, points = points), Vec3.ZERO) { it }
        val toViewer = Vec3(0.8f, 0f, 0.6f).normalized()
        val reached = teeth.contours { toViewer }.flatMap { it.points }.maxOf { abs(it.position.y) }
        assertTrue(reached > 0.95f, "seen nearly along its axis the line reaches the ridges at 1, not the slopes between: $reached")
    }

    @Test
    fun aMirroredGridFacesTheSameWayOutward() {
        val mirrored = LineGrid.of(tube(), Vec3.ZERO) { Vec3(it.x, it.y, -it.z) }
        val sides = alongTheSides(mirrored.contours { Vec3.Z }.flatMap { it.points })
        assertTrue(sides.isNotEmpty())
        sides.forEach { assertTrue(abs(abs(it.position.y) - RADIUS) < CLOSE) }
    }
}
