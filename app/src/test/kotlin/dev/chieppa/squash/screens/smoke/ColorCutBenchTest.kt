package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.LandedShape
import dev.chieppa.squash.character.build.ShapeCuts
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.geom.ColorDetail
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.cutShape
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.screens.TestWork
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTime
import org.junit.jupiter.api.Tag

private const val STROKES_PER_PART = 24
private const val REGIONS_PER_PART = 6
private const val POINTS_PER_STROKE = 30
private const val POINTS_PER_BORDER = 20
private const val ROUNDS = 5
private const val BRUSH = 0.03f

/** How long a fennec's worth of paint takes to cut, and how far the mesh's triangles lie from the grid's bilinear surface. Numbers go to the test's output. */
@Tag("slow")
class ColorCutBenchTest {
    private fun shapes(id: PartId, seed: Int): List<LandedShape> = buildList {
        repeat(STROKES_PER_PART) { s ->
            val u = 0.1f + 0.8f * s / STROKES_PER_PART
            val points = List(POINTS_PER_STROKE) { i -> SurfaceUv(u + 0.05f * sin(i * 0.4f + seed), (s * 0.13f + i * 0.012f) % 1f) }
            add(LandedShape(3, FUR, id, BODY_PATCH, ColorShape.Brush(points, BRUSH)))
        }
        repeat(REGIONS_PER_PART) { r ->
            val points = List(POINTS_PER_BORDER) { i ->
                val angle = 2.0 * PI * i / POINTS_PER_BORDER
                SurfaceUv(0.2f + 0.1f * r + 0.05f * cos(angle).toFloat(), (r * 0.17f + 0.06f * sin(angle).toFloat() + 1f) % 1f)
            }
            add(LandedShape(2, FUR, id, BODY_PATCH, ColorShape.Border(points)))
        }
    }

    @Test
    fun aFennecsPaintCuts() {
        val work = TestWork()
        val torso = PartInstance(PartId("torso"), seamsRef("torso"))
        fun on(id: String, template: String, socket: String) = PartInstance(PartId(id), seamsRef(template), SocketRef(torso.id, socket))
        val parts = listOf(torso, on("front", "leg", "front-leg"), on("back", "leg", "back-leg"), on("tail", "tail", "tail"), on("head", "head", "neck"))
        val session = seamsSession(work, parts)
        work.settle(session)
        val surfaces = parts.associate { it.id to assertNotNull(session.surface(it.id).output.value) }
        val perPart = parts.mapIndexed { seed, part -> shapes(part.id, seed) }
        val count = perPart.sumOf { it.size } * 2
        for (step in listOf(ColorDetail.START, 12, 14)) {
            var triangles = 0
            var vertices = 0
            val time = measureTime {
                repeat(2) {
                    for (shapes in perPart) {
                        ShapeCuts().cuts(shapes, surfaces, step).forEach {
                            triangles += it.cut.mesh.indices.size / 3
                            vertices += it.cut.mesh.vertexCount
                        }
                    }
                }
            }
            var best = time
            repeat(ROUNDS) {
                val again = measureTime { repeat(2) { perPart.forEach { ShapeCuts().cuts(it, surfaces, step) } } }
                if (again < best) best = again
            }
            println("BENCH step=$step shapes=$count: first ${time.inWholeMilliseconds} ms, best ${best.inWholeMilliseconds} ms, $vertices vertices, $triangles triangles")
            assertTrue(triangles > 0)
        }
        val one = perPart.first().first()
        val single = (0 until ROUNDS).minOf { measureTime { ShapeCuts().cuts(listOf(one), surfaces, ColorDetail.START) } }
        println("BENCH one stroke at step ${ColorDetail.START}: ${single.inWholeMicroseconds} us")
        parts.forEach { println("BENCH bilinear gap on ${it.id.value}: ${bilinearGap(surfaces.getValue(it.id))} over a body of ${surfaces.getValue(it.id).axisLength}") }
        work.close()
    }

    @Test
    fun aSoftStrokeCostsThisAgainstAHardOne() {
        val work = TestWork()
        val torso = PartInstance(PartId("torso"), seamsRef("torso"))
        val session = seamsSession(work, listOf(torso))
        work.settle(session)
        val surface = assertNotNull(session.surface(torso.id).output.value)
        val grid = requireNotNull(surface.grid(BODY_PATCH))
        val long = List(POINTS_PER_STROKE) { i -> SurfaceUv(0.3f + 0.05f * sin(i * 0.4f), 0.2f + i * 0.012f) }
        val dab = listOf(SurfaceUv(0.5f, 0.25f))
        for ((name, points, radius) in listOf(Triple("long thin", long, BRUSH), Triple("long wide", long, 0.15f), Triple("dab", dab, 0.15f))) {
            for (step in listOf(ColorDetail.START, 12, 14)) {
                for (softness in listOf(0f, 0.5f, 1f)) {
                    val shape = ColorShape.Brush(points, radius, softness)
                    val tolerance = ColorDetail.tolerance(step)
                    repeat(ROUNDS) { cutShape(shape, grid, tolerance) }
                    val best = (0 until ROUNDS).minOf { measureTime { cutShape(shape, grid, tolerance) } }
                    val cut = cutShape(shape, grid, tolerance)
                    println("BENCH $name step=$step softness=$softness: ${best.inWholeMicroseconds} us, ${cut.mesh.vertexCount} vertices, ${cut.mesh.indices.size / 3} triangles")
                }
            }
        }
        work.close()
    }

    /** The most the middle of a cell's bilinear surface lies off the two triangles the mesh draws there. */
    private fun bilinearGap(surface: PartSurface): Float {
        val grid = requireNotNull(surface.grid(BODY_PATCH))
        var most = 0f
        for (ring in 0 until grid.rings - 1) {
            for (column in 0 until grid.columns) {
                if (grid.patch.ringUs[ring + 1] <= grid.patch.ringUs[ring] || grid.isPole(ring) || grid.isPole(ring + 1)) continue
                val u = grid.uAt(ring + 0.5)
                val v = (column + 0.5f) / grid.columns
                most = maxOf(most, (surface.shown(grid.patch.positionAt(u, v)) - grid.positionOn(ring + 0.5, column + 0.5)).length)
            }
        }
        return most
    }
}
