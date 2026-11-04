package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrushSweepTest {
    @Test
    fun aDabIsADiscOfItsRadius() {
        val grid = sheetGrid(rings = 11, segments = 11)
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.5f)), 0.2f), grid, 1e-4f)
        assertEquals(PI.toFloat() * 0.04f, cut.surfaceArea(), 1e-3f)
    }

    @Test
    fun aStraightStrokeIsACapsule() {
        val grid = sheetGrid(rings = 11, segments = 11)
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.25f, 0.5f), SurfaceUv(0.75f, 0.5f)), 0.1f), grid, 1e-4f)
        assertEquals(1f * 0.2f + PI.toFloat() * 0.01f, cut.surfaceArea(), 1e-3f)
    }

    @Test
    fun aHairpinCoversWhatABruteCountCovers() {
        val grid = sheetGrid(rings = 21, segments = 21)
        val points = listOf(SurfaceUv(0.2f, 0.45f), SurfaceUv(0.6f, 0.48f), SurfaceUv(0.2f, 0.52f))
        val radius = 0.08f
        val cut = cutShape(ColorShape.Brush(points, radius), grid, 2e-4f)
        val centers = flattenedThrough(unwrapped(points, false), grid, 2e-4f).map { Vec3(2f * it.u.toFloat(), it.v.toFloat(), 0f) }
        var inside = 0
        val samples = 400
        for (i in 0 until samples) for (j in 0 until samples) {
            val p = Vec3(2f * (i + 0.5f) / samples, (j + 0.5f) / samples, 0f)
            if (centers.zipWithNext().any { (a, b) -> distance(p, a, b) <= radius }) inside++
        }
        val counted = inside * 2f / (samples * samples)
        assertEquals(counted, cut.surfaceArea(), 0.002f)
    }

    private fun distance(p: Vec3, a: Vec3, b: Vec3): Float {
        val along = b - a
        val t = (((p - a) dot along) / (along dot along)).coerceIn(0f, 1f)
        return (a + along * t - p).length
    }

    @Test
    fun aStrokeCrossingTheSeamIsWholeOnBothSides() {
        val grid = tubeGrid(rings = 17, segments = 24)
        val across = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.95f), SurfaceUv(0.5f, 0.05f)), 0.1f), grid, 1e-4f)
        val away = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.45f), SurfaceUv(0.5f, 0.55f)), 0.1f), grid, 1e-4f)
        assertEquals(away.surfaceArea(), across.surfaceArea(), 1e-3f)
        assertOnMesh(across, grid)
    }

    @Test
    fun aStrokeLongerThanTheTubeIsRoundStaysOneFill() {
        val grid = tubeGrid(rings = 17, segments = 24)
        val turns = List(25) { SurfaceUv(0.5f, it * 1.5f / 24f) }
        val cut = cutShape(ColorShape.Brush(turns, 0.1f), grid, 1e-3f)
        val circumference = 2f * PI.toFloat() * 0.5f
        assertTrue(abs(cut.surfaceArea() - circumference * 0.2f) < 0.03f, "area ${cut.surfaceArea()}")
    }
}

class CurveFlattenTest {
    private val grid = sheetGrid(rings = 11, segments = 11)
    private val arc = List(6) { i ->
        val angle = i * PI.toFloat() / 5f
        SurfaceUv(0.5f + 0.2f * kotlin.math.cos(angle), 0.5f + 0.4f * kotlin.math.sin(angle))
    }

    @Test
    fun aFinerToleranceTakesMoreEdgesAndPassesThroughTheAnchors() {
        val coarse = flattenedThrough(unwrapped(arc, false), grid, 1e-2f)
        val fine = flattenedThrough(unwrapped(arc, false), grid, 1e-4f)
        assertTrue(fine.size > coarse.size * 3, "${fine.size} against ${coarse.size}")
        for (anchor in arc) assertTrue(fine.any { abs(it.u - anchor.u) < 1e-6 && abs(it.v - anchor.v) < 1e-6 })
    }

    @Test
    fun edgesStayWithinTolerance() {
        val tolerance = 1e-3f
        val edges = flattenedThrough(unwrapped(arc, false), grid, tolerance)
        val finest = flattenedThrough(unwrapped(arc, false), grid, 1e-6f)
        fun Pair<UnwrappedUv, UnwrappedUv>.gap(p: UnwrappedUv): Double {
            val ax = 2.0 * first.u
            val ay = first.v
            val bx = 2.0 * second.u
            val by = second.v
            val px = 2.0 * p.u
            val py = p.v
            val t = (((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / ((bx - ax) * (bx - ax) + (by - ay) * (by - ay))).coerceIn(0.0, 1.0)
            return kotlin.math.hypot(ax + (bx - ax) * t - px, ay + (by - ay) * t - py)
        }
        val chords = edges.zipWithNext()
        for (p in finest) assertTrue(chords.minOf { it.gap(p) } <= tolerance * 1.5, "the curve leaves its edges at $p")
    }

    @Test
    fun aClosedCurveEndsWhereItBeganOrATurnOn() {
        val tube = tubeGrid()
        val round = unwrapped(List(6) { SurfaceUv(0.5f, it / 6f) }, true)
        val closed = flattenedThrough(round, tube, 1e-3f, turn = 1.0)
        assertEquals(closed.first().u, closed.last().u, 1e-9)
        assertEquals(closed.first().v + 1.0, closed.last().v, 1e-9)
    }

    @Test
    fun detailRisesAtOnceAndFallsOnlyWellBelow() {
        val step = ColorDetail.stepFor(1000f, ColorDetail.COARSEST)
        assertEquals(10, step)
        assertEquals(11, ColorDetail.stepFor(1500f, step))
        assertEquals(10, ColorDetail.stepFor(600f, step))
        assertEquals(step, ColorDetail.stepFor(513f, step))
        assertEquals(8, ColorDetail.stepFor(200f, step))
        assertTrue(ColorDetail.tolerance(11) < ColorDetail.tolerance(10))
    }
}

class CutMergeTest {
    private val grid = sheetGrid()
    private fun cut(u: Float) = cutShape(ColorShape.Rectangle(u, u + 0.1f, 0.1f, 0.2f), grid, FINE)

    @Test
    fun neighboursOfOneSlotJoinAndAnotherSlotKeepsItsPlace() {
        val merged = mergedCuts(listOf(SlottedCut("fur", cut(0.1f)), SlottedCut("fur", cut(0.3f)), SlottedCut("pad", cut(0.5f)), SlottedCut("fur", cut(0.7f))))
        assertEquals(listOf("fur", "pad", "fur"), merged.map { it.slot })
        assertEquals(2f * cut(0.1f).parameterArea(), merged[0].cut.parameterArea(), 1e-5f)
        assertEquals(cut(0.1f).mesh.vertexCount + cut(0.3f).mesh.vertexCount, merged[0].cut.mesh.vertexCount)
        assertEquals(merged[0].cut.mesh.vertexCount, merged[0].cut.us.size)
        assertTrue(merged[0].cut.mesh.indices.max() < merged[0].cut.mesh.vertexCount)
    }

    @Test
    fun anEmptyCutGoesAndNoLongerSeparatesItsNeighbours() {
        val empty = cutShape(ColorShape.Rectangle(5f, 6f, 0.1f, 0.2f), grid, FINE)
        val merged = mergedCuts(listOf(SlottedCut("fur", cut(0.1f)), SlottedCut("pad", empty), SlottedCut("fur", cut(0.3f))))
        assertEquals(listOf("fur"), merged.map { it.slot })
    }
}

class ProxyNormalTest {
    private fun ring(u: Float, width: Float, height: Float, section: ClosedSpline, x: Float): Slice {
        val frame = Frame(Vec3(x, 0f, 0f), Vec3.X, Vec3.Y)
        return Slice(u, frame, height, width, section, frame.rotation)
    }

    @Test
    fun aRoundTubesProxyNormalPointsStraightOut() {
        val section = SectionProfile.CIRCLE.spline(12)
        val slices = List(9) { ring(it / 8f, 1f, 1f, section, it / 8f) }
        val patch = SliceBuilder.loft("body", slices, 12)
        val proxies = requireNotNull(patch.proxyNormals)
        for (v in 0 until patch.nu * patch.nv) {
            val normal = Vec3(proxies[v * 3], proxies[v * 3 + 1], proxies[v * 3 + 2])
            val point = patch.point(v)
            assertEquals(1f, normal.length, 1e-4f)
            assertTrue((normal - Vec3(0f, point.y, point.z).normalized()).length < 0.03f, "at $point: $normal")
        }
    }

    @Test
    fun aBumpInTheSectionDoesNotReachTheProxyNormal() {
        val section = SectionProfile.CIRCLE.spline(16)
        val bumped = ClosedSpline(section.controlPoints.mapIndexed { i, p -> if (i == 4) p * 1.25f else p })
        val plain = SliceBuilder.loft("body", List(9) { ring(it / 8f, 1f, 1f, section, it / 8f) }, 16)
        val rough = SliceBuilder.loft("body", List(9) { ring(it / 8f, 1f, 1f, bumped, it / 8f) }, 16)
        fun meshTurn(patch: Patch): Float {
            val mesh = RenderMesh.of(PartMesh("p", listOf(MeshBuilder.build(patch) { 0 })))
            val a = patch.index(4, 3) * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.NORMAL_OFFSET_FLOATS
            val b = patch.index(4, 5) * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.NORMAL_OFFSET_FLOATS
            val va = Vec3(mesh.vertices[a], mesh.vertices[a + 1], mesh.vertices[a + 2])
            val vb = Vec3(mesh.vertices[b], mesh.vertices[b + 1], mesh.vertices[b + 2])
            return (va - vb).length
        }
        fun proxyTurn(patch: Patch): Float {
            val p = requireNotNull(patch.proxyNormals)
            val a = patch.index(4, 3) * 3
            val b = patch.index(4, 5) * 3
            return (Vec3(p[a], p[a + 1], p[a + 2]) - Vec3(p[b], p[b + 1], p[b + 2])).length
        }
        assertTrue(meshTurn(rough) > meshTurn(plain) * 1.2f, "the bump turns the mesh normal: ${meshTurn(rough)} against ${meshTurn(plain)}")
        val meshMoved = meshTurn(rough) - meshTurn(plain)
        assertTrue(proxyTurn(rough) - proxyTurn(plain) < meshMoved, "the proxy normal moves less than the mesh's: ${proxyTurn(rough) - proxyTurn(plain)} against $meshMoved")
    }

    @Test
    fun aClosedEndsProxyNormalLeansOutAlongTheAxis() {
        val section = SectionProfile.CIRCLE.spline(12)
        val radii = listOf(1f, 1f, 1f, 1f, 1f, 0.9f, 0.6f, 0.3f, 0f)
        val patch = SliceBuilder.loft("body", radii.mapIndexed { i, r -> ring(i / 8f, r, r, section, i / 8f) }, 12)
        val proxies = requireNotNull(patch.proxyNormals)
        val end = patch.index(8, 3) * 3
        assertTrue(proxies[end] > 0.7f, "the tip faces along the axis: ${proxies[end]}")
    }

    @Test
    fun aMirroredMeshMirrorsItsProxyNormals() {
        val slices = List(5) { ring(it / 4f, 1f, 1f, SectionProfile.CIRCLE.spline(8), it / 4f) }
        val mesh = RenderMesh.of(PartMesh("p", listOf(MeshBuilder.build(SliceBuilder.loft("body", slices, 8)) { 0 })))
        val flipped = mesh.mirrored()
        val z = RenderMesh.PROXY_OFFSET_FLOATS + 2
        for (v in 0 until mesh.vertexCount) assertEquals(-mesh.vertices[v * RenderMesh.FLOATS_PER_VERTEX + z], flipped.vertices[v * RenderMesh.FLOATS_PER_VERTEX + z])
    }
}
