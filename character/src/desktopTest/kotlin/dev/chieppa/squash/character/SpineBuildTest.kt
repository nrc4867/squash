package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.SpineSample
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.WidthSample
import dev.chieppa.squash.geom.dragProfile
import dev.chieppa.squash.geom.spineLine
import dev.chieppa.squash.geom.spineShape
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PIXEL = 0.002f

/** How far the built part may stray from the spine and width: a ring's spacing and the fit's tolerance. */
private const val TOLERANCE = 0.02f

private const val HALF_WIDTH = 0.08f

/** A spine and width through the real outlines and the real build. See HLD: data-model, Views set slices. */
class SpineBuildTest {
    private val tail = template("tail")
    private val axes = PartBuilder.axes(tail)
    private val ends = StrokeEnds.CutAtPlane()
    private val spine: (Float) -> Vec2 = { Vec2(it, 0.15f * sin(it * 2f)) }

    private val shape = assertNotNull(
        spineShape(
            assertNotNull(spineLine(List(200) { SpineSample(spine(it / 199f)) }, ends, PIXEL)),
            assertNotNull(dragProfile(listOf(WidthSample(0.4f, HALF_WIDTH)), fromPlane = true)),
            ends,
        ),
    )
    private val fit = shape.fitted(PIXEL)
    private val outlines: Map<View, OutlineCurve> = mapOf(View.SIDE to fit.side.toOutline(), View.TOP to fit.top.toOutline())
    private val patches = PartBuilder.build(tail, outlines = outlines)
    private val body = patches.first()
    private val vertices = body.vertices()

    private fun stationIn(from: Float, to: Float) = shape.line.points.indices.filter { shape.line.points[it].x in from..to }

    @Test
    fun theSideSilhouetteFollowsTheSpineAndItsWidth() {
        val built = sideEdges(body, axes)
        for (index in stationIn(0.1f, 0.8f)) {
            val a = shape.line.points[index].x
            val crossing = assertNotNull(built.crossings(a), "the part reaches $a")
            val drawn = assertNotNull(sideEdges(assertNotNull(outlines[View.SIDE])).crossings(a))
            assertEquals(drawn.start, crossing.start, TOLERANCE, "low at $a")
            assertEquals(drawn.endInclusive, crossing.endInclusive, TOLERANCE, "high at $a")
            assertEquals(shape.line.points[index].y, (crossing.start + crossing.endInclusive) / 2f, TOLERANCE, "the spine runs down the middle at $a")
        }
    }

    @Test
    fun thePartHasTheWidthAlongAInTop() {
        val rings = (0 until body.nu).map { body.ring(it) }
        val inside = rings.filter { ring -> (ring.first() dot axes.along) in 0.1f..0.8f }
        assertTrue(inside.size >= 3, "rings to compare: ${inside.size}")
        for (ring in inside) {
            val a = ring.first() dot axes.along
            val index = shape.line.points.indices.minBy { abs(shape.line.points[it].x - a) }
            val width = ring.maxOf { it dot axes.side } - ring.minOf { it dot axes.side }
            assertEquals(2f * shape.halfWidths[index], width, 2f * TOLERANCE, "at $a")
        }
    }

    @Test
    fun thePartRunsFromTheSocketPlaneToTheSpinesEnd() {
        val along = vertices.map { it dot axes.along }
        assertEquals(0f, along.min(), TOLERANCE)
        assertEquals(shape.line.points.last().x, along.max(), TOLERANCE)
    }

    @Test
    fun theOutlinesBuildTheTemplatesTopology() {
        val undrawn = PartBuilder.build(tail).first()
        assertEquals(undrawn.nu to undrawn.nv, body.nu to body.nv)
        val mesh = PartBuilder.mesh("tail", tail, patches).patches.first()
        assertEquals(emptyList(), MeshInvariants.check(mesh, PartBuilder.openRings(tail, mesh.patch)))
    }
}
