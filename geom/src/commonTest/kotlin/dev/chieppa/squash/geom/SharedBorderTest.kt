package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedBorderTest {
    private val tolerance = 1e-4f

    /** A two-tone face: a light muzzle under a dark top, meeting along a curve through [edge], the junctions at its ends. */
    private val edge = listOf(SurfaceUv(0.35f, 0.38f), SurfaceUv(0.45f, 0.43f), SurfaceUv(0.5f, 0.5f), SurfaceUv(0.45f, 0.57f), SurfaceUv(0.35f, 0.62f))
    private val below = listOf(SurfaceUv(0.15f, 0.64f), SurfaceUv(0.15f, 0.36f))
    private val above = listOf(SurfaceUv(0.6f, 0.68f), SurfaceUv(0.72f, 0.5f), SurfaceUv(0.6f, 0.32f))

    private val muzzle = ColorShape.Border(edge + below, corners = setOf(0, edge.lastIndex))
    private val top = ColorShape.Border(edge.reversed() + above.reversed(), corners = setOf(0, edge.lastIndex))
    private val both = ColorShape.Border(below + edge.take(1) + above.reversed() + edge.takeLast(1), corners = setOf(2, 6))

    @Test
    fun twoRegionsThroughOneDrawnEdgeCoverItOnceWithNoGap() {
        val grid = tubeGrid(rings = 17, segments = 24)
        val cuts = listOf(cutShape(muzzle, grid, tolerance), cutShape(top, grid, tolerance))
        val union = cutShape(both, grid, tolerance)
        assertEquals(union.parameterArea(), cuts.sumOf { it.parameterArea().toDouble() }.toFloat(), 1e-5f)
        var inside = 0
        for (i in 0 until SAMPLES) for (j in 0 until SAMPLES) {
            val u = 0.1f + 0.7f * (i + 0.37f) / SAMPLES
            val v = 0.25f + 0.5f * (j + 0.61f) / SAMPLES
            val covered = cuts.count { it.holds(u, v) }
            if (union.holds(u, v)) {
                inside++
                assertEquals(1, covered, "covered $covered times at $u, $v")
            } else {
                assertEquals(0, covered, "covered outside both at $u, $v")
            }
        }
        assertTrue(inside > SAMPLES * SAMPLES / 4, "only $inside samples inside")
    }

    @Test
    fun onTheOneSurfaceTwoCurvedRegionsThroughOneEdgeHoldEachPlaceOnce() {
        val reads = listOf(muzzle, top).map { UvShapeDistance(it.copy(curved = true), closedV = true) }
        val union = UvShapeDistance(both.copy(curved = true), closedV = true)
        var inside = 0
        for (i in 0 until SAMPLES) for (j in 0 until SAMPLES) {
            val u = 0.1f + 0.7f * (i + 0.37f) / SAMPLES
            val v = 0.25f + 0.5f * (j + 0.61f) / SAMPLES
            val holding = reads.count { it.at(u, v, 1f, 1f) > 0f }
            val expected = if (union.at(u, v, 1f, 1f) > 0f) 1 else 0
            inside += expected
            assertEquals(expected, holding, "held $holding times at $u, $v")
        }
        assertTrue(inside > SAMPLES * SAMPLES / 4, "only $inside samples inside")
    }

    @Test
    fun aCurvedRegionReadsRoundOnTheOneSurface() {
        val few = listOf(SurfaceUv(0.3f, 0.5f), SurfaceUv(0.5f, 0.75f), SurfaceUv(0.7f, 0.5f), SurfaceUv(0.5f, 0.25f))
        val curved = UvShapeDistance(ColorShape.Border(few, curved = true), closedV = true)
        val straight = UvShapeDistance(ColorShape.Border(few), closedV = true)
        assertTrue(curved.at(0.6f, 0.64f, 1f, 1f) > 0f, "the curve bulges past the straight edge")
        assertTrue(straight.at(0.6f, 0.64f, 1f, 1f) < 0f)
    }

    @Test
    fun theSharedEdgeIsTheSameWhicheverWayItRuns() {
        val grid = tubeGrid(rings = 17, segments = 24)
        val one = flattenedThrough(unwrapped(muzzle.points, true), grid, tolerance, 0.0, muzzle.corners)
        val other = flattenedThrough(unwrapped(top.points, true), grid, tolerance, 0.0, top.corners)
        val stretch = one.subList(0, one.indexOfFirst { it.u == edge.last().u.toDouble() && it.v == edge.last().v.toDouble() } + 1)
        val back = other.subList(0, other.indexOfFirst { it.u == edge.first().u.toDouble() && it.v == edge.first().v.toDouble() } + 1).reversed()
        assertEquals(stretch, back)
    }

    @Test
    fun aFewPointRegionHasASmoothBoundary() {
        val grid = sheetGrid(rings = 11, segments = 11)
        val few = listOf(SurfaceUv(0.3f, 0.5f), SurfaceUv(0.5f, 0.75f), SurfaceUv(0.7f, 0.5f), SurfaceUv(0.5f, 0.25f))
        val curve = flattenedThrough(unwrapped(few, false), grid, tolerance, 0.0)
        assertTrue(curve.size > 4 * 8, "${curve.size} edges")
        assertTrue(largestTurn(curve) < SMOOTH_TURN, "turns ${largestTurn(curve)} degrees")
        val polygon = 0.5f * 0.4f * 0.5f
        assertTrue(cutShape(ColorShape.Border(few), grid, tolerance).parameterArea() > polygon * 1.2f)
    }

    @Test
    fun aCornerTurnsSharplyAndTheRestStaysSmooth() {
        val grid = sheetGrid(rings = 11, segments = 11)
        val few = listOf(SurfaceUv(0.3f, 0.5f), SurfaceUv(0.5f, 0.75f), SurfaceUv(0.7f, 0.5f), SurfaceUv(0.5f, 0.25f))
        val curve = flattenedThrough(unwrapped(few, false), grid, tolerance, 0.0, corners = setOf(2))
        val at = curve.indexOfFirst { it.u == 0.7f.toDouble() && abs(it.v - 0.5) < 1e-9 }
        assertTrue(turnAt(curve, at) > 45.0, "corner turns ${turnAt(curve, at)} degrees")
        assertTrue(largestTurn(curve.subList(0, at)) < SMOOTH_TURN)
    }

    private fun largestTurn(curve: List<UnwrappedUv>): Double = (1 until curve.lastIndex).maxOf { turnAt(curve, it) }

    private fun turnAt(curve: List<UnwrappedUv>, i: Int): Double {
        val a = curve[i - 1]
        val b = curve[i]
        val c = curve[(i + 1) % curve.size]
        val x1 = 2.0 * (b.u - a.u)
        val y1 = b.v - a.v
        val x2 = 2.0 * (c.u - b.u)
        val y2 = c.v - b.v
        val cos = (x1 * x2 + y1 * y2) / (sqrt(x1 * x1 + y1 * y1) * sqrt(x2 * x2 + y2 * y2))
        return acos(cos.coerceIn(-1.0, 1.0)) * 180.0 / PI
    }

    /** Whether one of this cut's triangles holds [u], [v] in its own u and v. */
    private fun PatchCut.holds(u: Float, v: Float): Boolean {
        val indices = mesh.indices
        for (t in 0 until indices.size / 3) {
            val i = indices[t * 3]
            val j = indices[t * 3 + 1]
            val k = indices[t * 3 + 2]
            val d1 = side(u, v, us[i], vs[i], us[j], vs[j])
            val d2 = side(u, v, us[j], vs[j], us[k], vs[k])
            val d3 = side(u, v, us[k], vs[k], us[i], vs[i])
            if ((d1 >= 0f && d2 >= 0f && d3 >= 0f) || (d1 <= 0f && d2 <= 0f && d3 <= 0f)) return true
        }
        return false
    }

    private fun side(u: Float, v: Float, au: Float, av: Float, bu: Float, bv: Float) = (bu - au) * (v - av) - (bv - av) * (u - au)

    private companion object {
        const val SAMPLES = 120
        const val SMOOTH_TURN = 12.0
    }
}
