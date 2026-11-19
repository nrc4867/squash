package dev.chieppa.squash.geom

import dev.chieppa.squash.geom.field.SweptSolid
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SEGMENTS = 40
private const val SAMPLES = 400

/** A thick section with a hollow in its front: seen whole from its centre, its lips at a fixed v, its hollow as deep as asked. */
class ShellSectionTest {
    private val flat = ShellSection.of(SEGMENTS, 0.55f, 0f, 0.2f)
    private val hollow = ShellSection.of(SEGMENTS, 0.55f, 0.4f, 0.2f)

    /** How far round the outline turns as seen from [centre], walking it once: one whole turn, never back. */
    private fun turnsSeenFrom(section: ClosedSpline, centre: Vec2): Pair<Float, Float> {
        var total = 0f
        var leastStep = Float.MAX_VALUE
        var last = section.pointAt(0f) - centre
        for (i in 1..SAMPLES) {
            val p = section.pointAt(i.toFloat() / SAMPLES) - centre
            var step = atan2(last.x * p.y - last.y * p.x, last.x * p.x + last.y * p.y)
            if (step > PI) step -= (2 * PI).toFloat()
            total += step
            leastStep = minOf(leastStep, -step)
            last = p
        }
        return total to leastStep
    }

    @Test
    fun itsCentreSeesTheWholeOutlineOnce() {
        for (section in listOf(flat, hollow, ShellSection.of(SEGMENTS, 0.4f, 0.45f, 0.1f), ShellSection.of(SEGMENTS, 1.2f, 0.45f, 0.4f))) {
            val (total, leastStep) = turnsSeenFrom(section, requireNotNull(section.centre))
            assertEquals(-2 * PI.toFloat(), total, 1e-3f, "the outline goes once round its centre")
            assertTrue(leastStep > 0f, "every step turns the same way round the centre")
        }
    }

    @Test
    fun theLipsSitAtTheSameVAtEveryDepth() {
        for (section in listOf(flat, hollow)) {
            val lip = section.pointAt(SHELL_LIP_V)
            val mirrored = section.pointAt(1f - SHELL_LIP_V)
            assertEquals(lip.x, -mirrored.x, 1e-3f)
            assertEquals(lip.y, mirrored.y, 1e-3f)
        }
        val front = hollow.pointAt(0f)
        val lip = hollow.pointAt(SHELL_LIP_V)
        assertTrue(front.y < lip.y - 0.1f, "the hollow's floor lies ${lip.y - front.y} below its lips")
        assertTrue(flat.pointAt(0f).y >= flat.pointAt(SHELL_LIP_V).y, "a flat shell's front is a dome")
    }

    @Test
    fun itsBackIsFullAndRound() {
        val back = hollow.pointAt(0.5f)
        val spans = hollow.spans()
        assertEquals(2f, spans.x, 0.02f)
        assertTrue(spans.y > 0.4f, "the shell is ${spans.y} deep for a width of 2")
        assertTrue(back.y < -spans.y / 2f + 0.02f, "the back's middle is its deepest point")
    }

    @Test
    fun aRunOfHollowRingsSweepsASolidHollowAtTheFront() {
        val rings = 6
        val section = hollow
        val centre = requireNotNull(section.centre)
        val points = List(rings) { i -> List(SEGMENTS) { k -> section.pointAt(k.toFloat() / SEGMENTS).let { Vec3(it.x, it.y, i * 0.2f) } } }
        val middles = List(rings) { i -> Vec3(centre.x, centre.y, i * 0.2f) }
        val solid = requireNotNull(
            SweptSolid.ofRings(points, FloatArray(SEGMENTS) { it.toFloat() / SEGMENTS }, List(rings) { Vec3.Z }, List(rings) { Vec3.Y }, FloatArray(rings) { it / (rings - 1f) }, middles),
        )
        val floor = section.pointAt(0f)
        val lip = section.pointAt(SHELL_LIP_V)
        val inHollow = Vec3(0f, (floor.y + lip.y) / 2f, 0.5f)
        val inWall = Vec3(0f, (floor.y + section.pointAt(0.5f).y) / 2f, 0.5f)
        assertTrue(solid.distance(inHollow) > 0f, "a point in the hollow lies outside the solid")
        assertTrue(solid.distance(inWall) < 0f, "a point behind the floor lies inside it")
    }
}
