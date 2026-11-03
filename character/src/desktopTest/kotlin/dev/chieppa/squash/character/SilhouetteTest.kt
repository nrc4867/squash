package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec2
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAMPLES = 160
private const val STATIONS = 400

/** About three pixels at the Sheet's scale for a part one unit long. */
private const val TOLERANCE = 0.012f
private const val CROSSING_TOLERANCE = 3e-3f
private const val MIN_EXTENT = 0.002f

/** A drawn Side outline is the mesh's silhouette. See HLD: data-model, Views set slices. */
class SilhouetteTest {
    private class Shape(val name: String, val template: PartTemplate, val outline: OutlineCurve)

    /** Up one run and back along the other, from a = 0 to 1. */
    private fun around(upper: (Float) -> Float, lower: (Float) -> Float): OutlineCurve {
        fun at(i: Int) = i.toFloat() / SAMPLES
        val top = List(SAMPLES + 1) { at(it) to upper(at(it)) }
        val under = List(SAMPLES) { at(SAMPLES - 1 - it) to lower(at(SAMPLES - 1 - it)) }
        return outline(*(top + under).toTypedArray())
    }

    private fun dome(a: Float) = sqrt((1f - (2f * a - 1f) * (2f * a - 1f)).coerceAtLeast(0f))

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun shoulder(a: Float) = 0.2f * smooth((a - 0.45f) / 0.06f) * dome(a)

    private val blob = around({ 0.25f * dome(it) + shoulder(it) }, { -0.2f * dome(it) + shoulder(it) })

    private val lumpyTorso = around(
        { dome(it) * (0.22f * (1f + 0.2f * sin(14f * it)) + 0.05f * sin(9f * it)) },
        { dome(it) * (-0.2f * (1f + 0.2f * sin(11f * it + 1f)) + 0.05f * sin(9f * it)) },
    )

    private fun footHalf(a: Float): Float {
        val across = (a - FOOT_CENTRE) / FOOT_LENGTH
        return FOOT_HEIGHT * sqrt((1f - across * across).coerceAtLeast(0f))
    }

    /** A tube running straight into a round foot set forward of it, open at the socket plane. */
    private val roundFoot = around(
        { a -> max(if (a < FOOT_CENTRE) TUBE_HALF else 0f, FOOT_RISE + footHalf(a)) },
        { a -> if (a < FOOT_CENTRE - FOOT_LENGTH) -TUBE_HALF else min(if (a < FOOT_CENTRE) -TUBE_HALF else FOOT_RISE, FOOT_RISE - footHalf(a)) },
    )

    private val lumpyLeg = around(
        { (0.12f + 0.04f * sin(12f * it) + 0.06f * sin(PI.toFloat() * it)) * sqrt(1f - it * it) },
        { (-0.12f + 0.05f * sin(9f * it + 2f) + 0.06f * sin(PI.toFloat() * it)) * sqrt(1f - it * it) },
    )

    private val shapes = listOf(
        Shape("steep shoulder on the torso", template("torso"), blob),
        Shape("steep shoulder on the leg", template("leg"), blob),
        Shape("lumpy torso", template("torso"), lumpyTorso),
        Shape("round foot on a tube", template("leg"), roundFoot),
        Shape("lumpy open leg", template("leg"), lumpyLeg),
    )

    private fun body(shape: Shape): Patch =
        PartBuilder.build(shape.template, outlines = mapOf(View.SIDE to shape.outline)).first()

    /** A pole is the point a ring closes to; it shares its plane with a flat end's last ring. */
    private fun rings(patch: Patch) = (0 until patch.nu).filter { patch.slices!![it].height > 0f }

    @Test
    fun ringPlanesStrictlyRiseAlongTheAxis() {
        for (shape in shapes) {
            val axes = PartBuilder.axes(shape.template)
            val patch = body(shape)
            for ((ring, next) in rings(patch).zipWithNext()) {
                val highest = patch.ring(ring).maxOf { it dot axes.along }
                val lowest = patch.ring(next).minOf { it dot axes.along }
                assertTrue(highest < lowest, "${shape.name}: ring $ring reaches $highest, past ring $next at $lowest")
            }
        }
    }

    @Test
    fun theMeshSilhouetteInSideStaysOnTheOutline() {
        for (shape in shapes) {
            val axes = PartBuilder.axes(shape.template)
            val patch = body(shape)
            val drawnEdges = sideEdges(shape.outline)
            val meshEdges = sideEdges(patch, axes)
            val from = patch.vertices().minOf { it dot axes.along }
            val to = patch.vertices().maxOf { it dot axes.along }
            var off = 0f
            var missed = 0f
            for (i in 0..STATIONS) {
                val a = from + (to - from) * i / STATIONS
                val mesh = meshEdges.crossings(a) ?: continue
                off = max(off, drawnEdges.distanceTo(Vec2(a, mesh.start)))
                off = max(off, drawnEdges.distanceTo(Vec2(a, mesh.endInclusive)))
                val drawn = drawnEdges.crossings(a) ?: continue
                missed = max(missed, meshEdges.distanceTo(Vec2(a, drawn.start)))
                missed = max(missed, meshEdges.distanceTo(Vec2(a, drawn.endInclusive)))
            }
            assertTrue(off <= TOLERANCE, "${shape.name}: the silhouette leaves the outline by $off")
            assertTrue(missed <= TOLERANCE, "${shape.name}: the mesh misses the outline by $missed")
        }
    }

    @Test
    fun aRingsEndsAreTheOutlinesCrossingsAtItsPlane() {
        for (shape in shapes) {
            val axes = PartBuilder.axes(shape.template)
            val patch = body(shape)
            val drawn = sideEdges(shape.outline)
            for (ring in rings(patch).drop(1).dropLast(1)) {
                val points = patch.ring(ring)
                val crossings = assertNotNull(drawn.crossings(points.first() dot axes.along))
                assertEquals(crossings.endInclusive, points.maxOf { it dot axes.up }, CROSSING_TOLERANCE, "${shape.name} ring $ring")
                assertEquals(crossings.start, points.minOf { it dot axes.up }, CROSSING_TOLERANCE, "${shape.name} ring $ring")
            }
        }
    }

    @Test
    fun aRoundFootReachesTheCurvesTipWithNoFlatBottom() {
        val leg = template("leg")
        val axes = PartBuilder.axes(leg)
        val patch = PartBuilder.build(leg, outlines = mapOf(View.SIDE to roundFoot)).first()
        val tip = patch.vertices().maxBy { it dot axes.along }
        assertEquals(1f, tip dot axes.along, 1e-4f)
        assertEquals(FOOT_RISE, tip dot axes.up, CROSSING_TOLERANCE)
        val lastRing = patch.ring(rings(patch).last())
        val across = lastRing.maxOf { it dot axes.up } - lastRing.minOf { it dot axes.up }
        assertTrue(across < 3f * MIN_EXTENT, "the foot ends in a flat $across across")
    }

    @Test
    fun theToesOfADrawnPawStandInTheirPlanesInsideTheOutline() {
        val paw = template("paw")
        val axes = PartBuilder.axes(paw)
        val drawn = sideEdges(blob)
        val toes = PartBuilder.build(paw, outlines = mapOf(View.SIDE to blob)).filter { it.name.startsWith("toes:") }
        for (toe in toes) {
            for ((ring, next) in rings(toe).zipWithNext()) {
                val highest = toe.ring(ring).maxOf { it dot axes.along }
                assertTrue(highest < toe.ring(next).minOf { it dot axes.along }, "${toe.name} ring $ring")
            }
            for (p in toe.vertices()) {
                val crossings = assertNotNull(drawn.crossings(p dot axes.along))
                assertTrue((p dot axes.up) in (crossings.start - TOLERANCE)..(crossings.endInclusive + TOLERANCE), "${toe.name} at $p")
            }
        }
    }

    @Test
    fun noVertexPassesTheOutlinesExtents() {
        for (shape in shapes) {
            val axes = PartBuilder.axes(shape.template)
            val drawn = sideEdges(shape.outline)
            val a = min(drawn.minOf { it.from.x }, 0f)..drawn.maxOf { it.from.x }
            val b = drawn.minOf { it.from.y }..drawn.maxOf { it.from.y }
            for (p in body(shape).vertices()) {
                val along = p dot axes.along
                val up = p dot axes.up
                assertTrue(along >= a.start - TOLERANCE && along <= a.endInclusive + TOLERANCE, "${shape.name}: a = $along outside $a")
                assertTrue(up >= b.start - TOLERANCE && up <= b.endInclusive + TOLERANCE, "${shape.name}: b = $up outside $b")
            }
        }
    }

    private companion object {
        const val TUBE_HALF = 0.07f
        const val FOOT_CENTRE = 0.8f
        const val FOOT_LENGTH = 0.2f
        const val FOOT_HEIGHT = 0.2f
        const val FOOT_RISE = 0.12f
    }
}
