package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FINE_CUT = 1e-3f

class SoftBrushCutTest {
    private val across = listOf(SurfaceUv(0.2f, 0.9f), SurfaceUv(0.5f, 0.05f), SurfaceUv(0.8f, 0.2f))

    private fun PatchCut.alpha(vertex: Int) = assertNotNull(alphas)[vertex]

    /** Vertices of the cut as (distance from [center], alpha). */
    private fun PatchCut.profile(center: Vec3) = (0 until mesh.vertexCount).map { (mesh.position(it) - center).length to alpha(it) }

    /** The alpha a triangle's interior takes at its corners' mean, against the distance there. */
    private fun PatchCut.centroidProfile(center: Vec3) = (0 until mesh.indices.size / 3).map { t ->
        val i = mesh.indices[t * 3]
        val j = mesh.indices[t * 3 + 1]
        val k = mesh.indices[t * 3 + 2]
        val at = (mesh.position(i) + mesh.position(j) + mesh.position(k)) * (1f / 3f)
        (at - center).length to (alpha(i) + alpha(j) + alpha(k)) / 3f
    }

    /** Edges one triangle uses, with vertices closer in space than a float's own noise taken as one. */
    private fun PatchCut.weldedOpenEdges(): List<Pair<Int, Int>> {
        val firsts = ArrayList<Int>()
        val welded = IntArray(mesh.vertexCount) { v ->
            val at = mesh.position(v)
            firsts.firstOrNull { (mesh.position(it) - at).length < 2e-6f } ?: v.also { firsts += v }
        }
        val uses = HashMap<Pair<Int, Int>, Int>()
        for (t in 0 until mesh.indices.size / 3) {
            for (i in 0 until 3) {
                val a = welded[mesh.indices[t * 3 + i]]
                val b = welded[mesh.indices[t * 3 + (i + 1) % 3]]
                if (a == b) continue
                val key = if (a < b) a to b else b to a
                uses[key] = (uses[key] ?: 0) + 1
            }
        }
        return uses.filterValues { it == 1 }.keys.toList()
    }

    private fun assertFadesLinearly(profile: List<Pair<Float, Float>>, radius: Float, core: Float, within: Float) {
        for ((distance, alpha) in profile) {
            val expected = ((radius - distance) / (radius - core)).coerceIn(0f, 1f)
            assertEquals(expected, alpha, within, "at $distance of a fade from $core to $radius")
        }
    }

    @Test
    fun aDabFallsFromOneAtItsCoreToZeroAtItsEdgeAcrossACell() {
        val grid = sheetGrid()
        val radius = 0.2f
        val center = grid.positionOn(grid.gridU(0.5), grid.gridV(0.5))
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.5f)), radius, 0.5f), grid, FINE_CUT)
        val profile = cut.profile(center)
        assertTrue(profile.any { (d, a) -> a == 1f && abs(d - 0.1f) < 2e-3f }, "the core's contour is at full opacity")
        assertTrue(profile.any { (d, a) -> a == 0f && abs(d - radius) < 2e-3f }, "the edge's contour is at none")
        assertFadesLinearly(profile, radius, 0.1f, 0.02f)
        assertFadesLinearly(cut.centroidProfile(center), radius, 0.1f, 0.05f)
        assertOnMesh(cut, grid)
    }

    @Test
    fun softnessOneFadesFromTheCurveItself() {
        val grid = sheetGrid()
        val center = grid.positionOn(grid.gridU(0.4), grid.gridV(0.4))
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.4f, 0.4f)), 0.22f, 1f), grid, FINE_CUT)
        assertFadesLinearly(cut.profile(center), 0.22f, 0f, 0.04f)
        assertTrue(assertNotNull(cut.alphas).max() > 0.97f, "it reaches full color at the curve")
        assertEquals(0f, assertNotNull(cut.alphas).min())
    }

    @Test
    fun aFadeWithinOneCellHoldsItsNestedContours() {
        val grid = sheetGrid(rings = 2, segments = 2)
        val center = grid.positionOn(grid.gridU(0.3), grid.gridV(0.3))
        val radius = 0.08f
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.3f)), radius, 1f), grid, FINE_CUT)
        assertFadesLinearly(cut.profile(center), radius, 0f, 0.04f)
        assertEquals(discArea(radius), cut.surfaceArea(), discArea(radius) * 0.1f)
    }

    private fun discArea(radius: Float) = kotlin.math.PI.toFloat() * radius * radius

    @Test
    fun aBrushWiderThanItsReachAroundVStillFadesToNothingAtItsEdge() {
        val grid = sheetGrid()
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.4f, 0.4f)), 0.4f, 1f), grid, FINE_CUT)
        for ((a, b) in cut.boundaryEdges()) assertTrue(cut.alpha(a) < 1e-3f && cut.alpha(b) < 1e-3f, "an edge of the fade holds ${cut.alpha(a)}, ${cut.alpha(b)}")
        val reach = BRUSH_V_REACH.toFloat() * grid.columns
        for (v in 0 until cut.mesh.vertexCount) {
            val around = abs(grid.gridV(cut.vs[v].toDouble()).toFloat() - grid.gridV(0.4).toFloat())
            assertTrue(cut.alpha(v) <= (1f - around / reach) * 32f / 31f + 1e-3f, "at $around of a cell around it holds ${cut.alpha(v)}")
        }
    }

    @Test
    fun aFadeRunsTheSameAcrossTheSeam() {
        val grid = tubeGrid(rings = 21, segments = 24)
        val shape = { v: Float -> ColorShape.Brush(listOf(SurfaceUv(0.5f, v)), 0.25f, 0.8f) }
        val onSeam = cutShape(shape(0f), grid, FINE_CUT)
        val away = cutShape(shape(0.5f), grid, FINE_CUT)
        assertEquals(away.surfaceArea(), onSeam.surfaceArea(), 1e-3f)
        assertTrue((0 until onSeam.mesh.vertexCount).any { onSeam.vs[it] < 0.1f } && (0 until onSeam.mesh.vertexCount).any { onSeam.vs[it] > 0.9f })
        assertOnMesh(onSeam, grid)
        for (v in 0 until onSeam.mesh.vertexCount) {
            val around = if (onSeam.vs[v] > 0.5f) onSeam.vs[v] - 1f else onSeam.vs[v]
            val nearest = (0 until away.mesh.vertexCount).minBy { abs(away.us[it] - onSeam.us[v]) + abs(away.vs[it] - 0.5f - around) }
            assertEquals(away.alpha(nearest), onSeam.alpha(v), 0.05f, "at the same place from the brush")
        }
    }

    @Test
    fun aFadeOverAPoleFallsAwayFromTheCurve() {
        val grid = domeGrid(rings = 17, segments = 24)
        val pole = Vec3(1f, 0f, 0f)
        val radius = 0.6f
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(1f, 0f)), radius, 1f), grid, FINE_CUT)
        assertOnMesh(cut, grid)
        for ((distance, alpha) in cut.profile(pole)) {
            val expected = ((radius - distance) / radius).coerceIn(0f, 1f)
            assertEquals(expected, alpha, 0.12f, "at $distance from the pole")
        }
        val atPole = (0 until cut.mesh.vertexCount).filter { (cut.mesh.position(it) - pole).length < 1e-4f }.map { cut.alpha(it) }
        assertTrue(atPole.isNotEmpty() && atPole.all { it > 0.9f }, "the pole's own vertices agree: $atPole")
    }

    @Test
    fun aFadeNearAPoleCoversTheCapWithNoHole() {
        val grid = domeGrid(rings = 17, segments = 24)
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.9f, 0.2f)), 0.5f, 0.7f), grid, FINE_CUT)
        val hard = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.9f, 0.2f)), 0.5f), grid, FINE_CUT)
        assertEquals(hard.surfaceArea(), cut.surfaceArea(), hard.surfaceArea() * 0.25f, "the fade ends about where the hard edge lies")
        assertOnMesh(cut, grid)
    }

    @Test
    fun softnessZeroIsTodaysCutExactly() {
        fun digest(cut: PatchCut) = listOf(cut.mesh.vertices.contentHashCode(), cut.mesh.indices.contentHashCode(), cut.us.contentHashCode(), cut.vs.contentHashCode(), cut.mesh.vertexCount, cut.mesh.indices.size)
        val tube = cutShape(ColorShape.Brush(across, 0.15f, 0f), tubeGrid(), FINE_CUT)
        assertNull(tube.alphas)
        assertEquals(listOf(1599410091, -2034642748, -1481240797, 403553938, 69, 237), digest(tube))
        assertEquals(listOf(820680409, -1924438222, 1301778950, 1792189740, 21, 57), digest(cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.02f)), 0.2f), sheetGrid(), FINE_CUT)))
        assertEquals(listOf(524594346, 950633737, -1575406143, -800116414, 186, 816), digest(cutShape(ColorShape.Brush(listOf(SurfaceUv(0.97f, 0.1f)), 0.5f), domeGrid(), FINE_CUT)))
        assertEquals(listOf(362936588, -621622190, -1597731148, 159261018, 64, 210), digest(cutShape(ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f), SurfaceUv(0.6f, 0.3f)), 0.2f), tubeGrid(mirrored = true), FINE_CUT)))
    }

    @Test
    fun coreAndBandsMeetWithNoCracks() {
        for ((name, grid, shape) in listOf(
            Triple("tube", tubeGrid(), ColorShape.Brush(across, 0.15f, 0.7f)),
            Triple("tight bend", tubeGrid(rings = 17, segments = 24), ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f), SurfaceUv(0.6f, 0.22f), SurfaceUv(0.32f, 0.26f)), 0.18f, 1f)),
            Triple("sheet", sheetGrid(), ColorShape.Brush(listOf(SurfaceUv(0.2f, 0.3f), SurfaceUv(0.7f, 0.7f)), 0.15f, 0.5f)),
        )) {
            val cut = cutShape(shape, grid, FINE_CUT)
            for ((a, b) in cut.weldedOpenEdges()) {
                assertTrue(cut.alpha(a) < 1e-3f && cut.alpha(b) < 1e-3f, "$name has an open edge inside its fade at ${cut.mesh.position(a)}: ${cut.alpha(a)}, ${cut.alpha(b)}")
            }
        }
    }

    @Test
    fun aFadeReachingAnOpenPatchsEdgeStopsThere() {
        val grid = sheetGrid()
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.02f)), 0.2f, 0.6f), grid, FINE_CUT)
        assertOnMesh(cut, grid)
        assertTrue((0 until cut.mesh.vertexCount).all { cut.vs[it] in 0f..1f })
        val atEdge = (0 until cut.mesh.vertexCount).filter { cut.vs[it] == 0f }.map { cut.alpha(it) }
        assertTrue(atEdge.any { it == 1f } && atEdge.any { it == 0f }, "the patch's edge runs through core and band: $atEdge")
    }

    @Test
    fun aTightBendReadsTheNearestCurveNotASumOfItsSides() {
        val grid = tubeGrid(rings = 17, segments = 24)
        val points = listOf(SurfaceUv(0.3f, 0.2f), SurfaceUv(0.6f, 0.22f), SurfaceUv(0.32f, 0.26f))
        val soft = cutShape(ColorShape.Brush(points, 0.18f, 1f), grid, FINE_CUT)
        val hard = cutShape(ColorShape.Brush(points, 0.18f), grid, FINE_CUT)
        assertEquals(hard.surfaceArea(), soft.surfaceArea(), hard.surfaceArea() * 0.15f, "a fade covers each place once")
    }

    @Test
    fun noBandTriangleLiesFlatAlongAContourWhileItsBandHasWidth() {
        val grid = sheetGrid()
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.5f)), 0.3f, 1f), grid, 1e-4f)
        var flatArea = 0f
        var fadingArea = 0f
        for (t in 0 until cut.mesh.indices.size / 3) {
            val i = cut.mesh.indices[t * 3]
            val j = cut.mesh.indices[t * 3 + 1]
            val k = cut.mesh.indices[t * 3 + 2]
            val area = ((cut.mesh.position(j) - cut.mesh.position(i)) cross (cut.mesh.position(k) - cut.mesh.position(i))).length / 2f
            val alphas = listOf(cut.alpha(i), cut.alpha(j), cut.alpha(k))
            if (alphas.max() - alphas.min() < 1e-3f && alphas.max() < 1f) flatArea += area else fadingArea += area
        }
        assertTrue(flatArea < fadingArea * 1e-3f, "flat $flatArea of $fadingArea")
    }

    @Test
    fun aSoftStrokesTrianglesAreBoundedByItsBandsAndAHardOnesCount() {
        val grid = tubeGrid(rings = 17, segments = 24)
        val points = listOf(SurfaceUv(0.2f, 0.1f), SurfaceUv(0.5f, 0.2f), SurfaceUv(0.8f, 0.15f))
        for (softness in listOf(0.25f, 0.5f, 1f)) {
            val soft = cutShape(ColorShape.Brush(points, 0.1f, softness), grid, FINE_CUT)
            val hard = cutShape(ColorShape.Brush(points, 0.1f), grid, FINE_CUT)
            assertTrue(soft.mesh.indices.size / 3 <= hard.mesh.indices.size / 3 * 8, "softness $softness: ${soft.mesh.indices.size / 3} triangles against ${hard.mesh.indices.size / 3}")
        }
    }

    @Test
    fun aMirroredGridHoldsTheSameFade() {
        val shape = ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f), SurfaceUv(0.6f, 0.3f)), 0.2f, 0.6f)
        val left = cutShape(shape, tubeGrid(), FINE_CUT)
        val right = cutShape(shape, tubeGrid(mirrored = true), FINE_CUT)
        assertEquals(left.mesh.vertexCount, right.mesh.vertexCount)
        for (v in 0 until left.mesh.vertexCount) {
            assertEquals(left.mesh.position(v).mirrored, right.mesh.position(v))
            assertEquals(left.alpha(v), right.alpha(v))
        }
    }

    @Test
    fun softCutsFaceTheWayTheMeshDoes() {
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f), SurfaceUv(0.6f, 0.3f)), 0.2f, 0.6f), tubeGrid(), FINE_CUT)
        for (t in 0 until cut.mesh.indices.size / 3) {
            val a = cut.mesh.position(cut.mesh.indices[t * 3])
            val b = cut.mesh.position(cut.mesh.indices[t * 3 + 1])
            val c = cut.mesh.position(cut.mesh.indices[t * 3 + 2])
            assertTrue((((b - a) cross (c - a)) dot Vec3(0f, a.y, a.z)) >= -1e-6f)
        }
    }

    @Test
    fun oneSoftCutDoesNotDependOnAnother() {
        val grid = tubeGrid()
        val shape = ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f)), 0.1f, 0.5f)
        val first = cutShape(shape, grid, FINE_CUT)
        cutShape(ColorShape.Brush(listOf(SurfaceUv(0.4f, 0.2f)), 0.3f, 1f), grid, FINE_CUT)
        val again = cutShape(shape, grid, FINE_CUT)
        assertTrue(first.mesh.vertices.contentEquals(again.mesh.vertices))
        assertTrue(first.mesh.indices.contentEquals(again.mesh.indices))
        assertTrue(assertNotNull(first.alphas).contentEquals(again.alphas))
    }

    @Test
    fun aFadeTooNarrowToResolveKeepsItsEdgeAndStaysItsOwn() {
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f)), 0.1f, 0.001f), tubeGrid(), FINE_CUT)
        assertTrue(assertNotNull(cut.alphas).all { it == 1f })
    }

    @Test
    fun softnessOutsideItsRangeIsRefused() {
        assertFailsWith<IllegalArgumentException> { ColorShape.Brush(across, 0.1f, 1.5f) }
        assertFailsWith<IllegalArgumentException> { ColorShape.Brush(across, 0.1f, -0.1f) }
    }

    @Test
    fun softCutsStayApartWhileHardNeighboursStillJoin() {
        val grid = tubeGrid()
        fun cut(u: Float, softness: Float) = SlottedCut("fur", cutShape(ColorShape.Brush(listOf(SurfaceUv(u, 0.2f)), 0.1f, softness), grid, FINE_CUT))
        assertEquals(1, mergedCuts(listOf(cut(0.2f, 0f), cut(0.4f, 0f))).size, "hard neighbours join")
        val merged = mergedCuts(listOf(cut(0.2f, 0f), cut(0.3f, 0f), cut(0.4f, 0.5f), cut(0.5f, 0.5f), cut(0.6f, 0f)))
        assertEquals(listOf(false, true, true, false), merged.map { it.cut.alphas != null }, "each soft stroke keeps its place in the order")
        assertFalse(merged.first().cut.isEmpty)
    }
}
