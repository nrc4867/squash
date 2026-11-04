package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShapeCutTest {
    private fun rectangle(uStart: Float, uEnd: Float, vStart: Float, vEnd: Float) = ColorShape.Rectangle(uStart, uEnd, vStart, vEnd)

    @Test
    fun aRectangleAcrossCellsKeepsItsAreaAndLiesOnTheSurface() {
        val grid = sheetGrid()
        val cut = cutShape(rectangle(0.13f, 0.81f, 0.27f, 0.66f), grid, FINE)
        assertEquals(0.68f * 0.39f, cut.parameterArea(), 1e-5f)
        assertEquals(2f * 0.68f * 0.39f, cut.surfaceArea(), 1e-5f)
        assertOnMesh(cut, grid)
    }

    @Test
    fun aCurvedBorderOverSeveralCellsLandsOnTheMesh() {
        val grid = sheetGrid()
        val around = listOf(SurfaceUv(0.1f, 0.1f), SurfaceUv(0.9f, 0.2f), SurfaceUv(0.3f, 0.9f))
        val cut = cutShape(ColorShape.Border(around), grid, 1e-4f)
        assertOnMesh(cut, grid)
        assertTrue(cut.parameterArea() > 0.25f && cut.parameterArea() < 0.5f, "area ${cut.parameterArea()}")
    }

    @Test
    fun onABentCellEveryVertexLiesOnTheDrawnTrianglesNotTheBilinearSurface() {
        val grid = gridOf(5, 5, false) { u, v -> Vec3(2f * u, v, 0.4f * sin(9f * u) * sin(7f * v)) }
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.35f), SurfaceUv(0.7f, 0.6f)), 0.2f), grid, 1e-3f)
        assertOnMesh(cut, grid)
        val gap = (0 until cut.mesh.vertexCount).maxOf { (grid.patch.positionAt(cut.us[it], cut.vs[it]) - cut.mesh.position(it)).length }
        assertTrue(gap > 0.01f, "a bilinear point would float off this mesh: $gap")
    }

    @Test
    fun exactFitAtAGridVertex() {
        val grid = tubeGrid()
        val cut = cutShape(ColorShape.WholePatch, grid, FINE)
        val expected = grid.patch.nu * grid.patch.nv
        assertEquals(expected, cut.mesh.vertexCount)
        for (v in 0 until cut.mesh.vertexCount) {
            val ring = grid.gridU(cut.us[v].toDouble())
            val column = grid.gridV(cut.vs[v].toDouble())
            val point = grid.patch.point(grid.patch.index(round(ring).toInt(), round(column).toInt() % grid.columns))
            assertEquals(0f, (point - cut.mesh.position(v)).length, "a grid vertex is the mesh's own, exactly")
        }
    }

    @Test
    fun wholeBandAroundTheSeamCoversTheTube() {
        val grid = tubeGrid(rings = 5, segments = 8)
        val whole = cutShape(ColorShape.WholePatch, grid, FINE)
        val band = cutShape(rectangle(0.25f, 0.75f, 0f, 1f), grid, FINE)
        assertEquals(1f, whole.parameterArea(), 1e-4f)
        assertEquals(0.5f, band.parameterArea(), 1e-4f)
        assertEquals(band.surfaceArea() * 2f, whole.surfaceArea(), 1e-3f)
    }

    @Test
    fun aShapeAcrossTheVSeamCutsTheSameAsOneElsewhere() {
        val grid = tubeGrid()
        val across = cutShape(rectangle(0.2f, 0.7f, 0.9f, 1.1f), grid, FINE)
        val away = cutShape(rectangle(0.2f, 0.7f, 0.4f, 0.6f), grid, FINE)
        assertEquals(away.parameterArea(), across.parameterArea(), 1e-4f)
        assertEquals(away.surfaceArea(), across.surfaceArea(), 1e-4f)
        assertTrue((0 until across.mesh.vertexCount).all { across.vs[it] in 0f..1f }, "v stays on the patch")
        assertTrue((0 until across.mesh.vertexCount).any { across.vs[it] < 0.11f } && (0 until across.mesh.vertexCount).any { across.vs[it] > 0.89f }, "it reaches both sides of the seam")
        assertOnMesh(across, grid)
    }

    @Test
    fun aBorderGoingOnceRoundHoldsTheSideTowardTheLastRing() {
        val grid = tubeGrid()
        val around = List(8) { SurfaceUv(0.25f, it / 8f) }
        val cut = cutShape(ColorShape.Border(around), grid, FINE)
        assertEquals(0.75f, cut.parameterArea(), 1e-3f)
        assertTrue((0 until cut.mesh.vertexCount).all { cut.us[it] >= 0.25f - 1e-4f })
    }

    @Test
    fun aDabOverAPoleCoversItAsARoundCap() {
        val grid = domeGrid()
        val radius = 0.5f
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.97f, 0.1f)), radius), grid, 1e-3f)
        assertFalse(cut.isEmpty)
        val pole = Vec3(1f, 0f, 0f)
        assertTrue((0 until cut.mesh.vertexCount).any { (cut.mesh.position(it) - pole).length < 1e-4f }, "it reaches the pole")
        assertOnMesh(cut, grid)
        val centre = grid.positionOn(grid.gridU(0.97), grid.gridV(0.1))
        val farthest = (0 until cut.mesh.vertexCount).maxOf { (cut.mesh.position(it) - centre).length }
        assertTrue(farthest <= radius * 1.25f, "it stays near the brush's reach: $farthest")
        assertTrue(cut.surfaceArea() in (PI.toFloat() * radius * radius * 0.7f)..(PI.toFloat() * radius * radius * 1.3f), "area ${cut.surfaceArea()}")
    }

    @Test
    fun aWholePoleCellCoversItsFanOfTriangles() {
        val grid = domeGrid()
        val whole = cutShape(ColorShape.WholePatch, grid, FINE)
        assertEquals(2f * PI.toFloat(), whole.surfaceArea(), 0.12f)
    }

    @Test
    fun anEdgeInAPoleCellRunsStraightOnItsTriangle() {
        val grid = domeGrid(rings = 3, segments = 8)
        val cut = cutShape(ColorShape.Rectangle(0.7f, 1.5f, 0.1f, 0.3f), grid, 1e-3f)
        assertOnMesh(cut, grid)
        val inCell = (0 until cut.mesh.vertexCount).filter { cut.us[it] in 0.7001f..0.9999f && abs(cut.vs[it] - 0.1f) < 1e-4f }.map { cut.mesh.position(it) }
        assertTrue(cut.surfaceArea() > 0f && inCell.size <= 2, "a line of fixed v has no vertex on a diagonal: $inCell")
    }

    @Test
    fun anOpenPatchStopsAtItsEdges() {
        val grid = sheetGrid()
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.02f)), 0.2f), grid, 1e-3f)
        assertOnMesh(cut, grid)
        assertTrue((0 until cut.mesh.vertexCount).all { cut.vs[it] in 0f..1f })
        val full = PI.toFloat() * 0.2f * 0.2f
        assertTrue(cut.surfaceArea() in (full * 0.45f)..(full * 0.7f), "about half of it lands: ${cut.surfaceArea()} of $full")
    }

    @Test
    fun aMirroredGridHoldsTheSameCutFacingOut() {
        val plain = tubeGrid()
        val mirrored = tubeGrid(mirrored = true)
        val shape = ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f), SurfaceUv(0.6f, 0.3f)), 0.2f)
        val left = cutShape(shape, plain, 1e-3f)
        val right = cutShape(shape, mirrored, 1e-3f)
        assertEquals(left.mesh.vertexCount, right.mesh.vertexCount)
        for (v in 0 until left.mesh.vertexCount) assertEquals(left.mesh.position(v).mirrored, right.mesh.position(v))
        assertOnMesh(right, mirrored)
    }

    @Test
    fun cutsFaceTheWayTheMeshDoes() {
        val grid = tubeGrid()
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f), SurfaceUv(0.6f, 0.3f)), 0.2f), grid, 1e-3f)
        for (t in 0 until cut.mesh.indices.size / 3) {
            val a = cut.mesh.position(cut.mesh.indices[t * 3])
            val b = cut.mesh.position(cut.mesh.indices[t * 3 + 1])
            val c = cut.mesh.position(cut.mesh.indices[t * 3 + 2])
            val outward = Vec3(0f, a.y, a.z)
            assertTrue((((b - a) cross (c - a)) dot outward) >= -1e-6f)
        }
    }

    @Test
    fun neighbouringTrianglesMeetVertexToVertexInsideTheShape() {
        val grid = tubeGrid()
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.2f, 0.9f), SurfaceUv(0.5f, 0.05f), SurfaceUv(0.8f, 0.2f)), 0.15f), grid, 1e-3f)
        val centre = grid.positionOn(grid.gridU(0.5), grid.gridV(0.05))
        val edges = cut.boundaryEdges()
        for ((a, b) in edges) {
            val middle = (cut.mesh.position(a) + cut.mesh.position(b)) * 0.5f
            assertTrue((middle - centre).length > 0.1f, "an open edge at $middle inside the stroke")
        }
    }

    @Test
    fun oneShapesCutDoesNotDependOnAnother() {
        val grid = tubeGrid()
        val shape = ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f)), 0.1f)
        val first = cutShape(shape, grid, 1e-3f)
        cutShape(ColorShape.Rectangle(0.1f, 0.9f, 0f, 1f), grid, 1e-3f)
        val again = cutShape(shape, grid, 1e-3f)
        assertTrue(first.mesh.vertices.contentEquals(again.mesh.vertices))
        assertTrue(first.mesh.indices.contentEquals(again.mesh.indices))
    }

    @Test
    fun aThinSliverAtACellCornerStaysClosed() {
        val grid = sheetGrid()
        val cut = cutShape(rectangle(0.25f, 0.5f, 0.25f, 0.5f), grid, FINE)
        assertEquals(0.25f * 0.25f, cut.parameterArea(), 1e-6f)
        assertTrue(abs(cut.mesh.vertexCount - 4) <= 1, "a rectangle on the grid's lines has its corners and the diagonal's none: ${cut.mesh.vertexCount}")
    }
}
