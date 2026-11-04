package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CurveMorphTest {
    private val open = listOf(Vec2(0f, 0f), Vec2(1f, 1f), Vec2(2f, 1f), Vec2(3f, 0f))
    private val closed = listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(2f, 0f), Vec2(3f, 0f))

    @Test
    fun theEndsAreTheCurvesThemselves() {
        assertSame(open, morphCurve(open, closed, 0f))
        assertSame(closed, morphCurve(open, closed, 1f))
    }

    @Test
    fun halfwayIsHalfwayAtEveryControlPoint() {
        val half = morphCurve(open, closed, 0.5f)
        assertEquals(listOf(Vec2(0f, 0f), Vec2(1f, 0.5f), Vec2(2f, 0.5f), Vec2(3f, 0f)), half)
    }

    @Test
    fun aCurveMorphedToItselfStaysPut() {
        assertEquals(open, morphCurve(open, open, 0.37f))
    }

    @Test
    fun unequalCountsAreRefusedAndSaySo() {
        val failure = assertFailsWith<IllegalArgumentException> { morphCurve(open, closed.drop(1), 0.5f) }
        assertEquals("Morphing curves of 4 and 3 control points", failure.message)
    }

    @Test
    fun pathsMorphSectionBySection() {
        val from = BezierPath(listOf(straightCubic(Vec2(0f, 0f), Vec2(3f, 0f)), straightCubic(Vec2(3f, 0f), Vec2(6f, 0f))))
        val to = BezierPath(listOf(straightCubic(Vec2(0f, 2f), Vec2(3f, 2f)), straightCubic(Vec2(3f, 2f), Vec2(6f, 2f))))
        val half = from.morphedTo(to, 0.5f)
        assertEquals(1f, half.segments[0].p0.y)
        assertEquals(1f, half.segments[1].p3.y)
        assertEquals(6f, half.segments[1].p3.x)
    }

    @Test
    fun pathsOfUnequalSectionsAreRefused() {
        val one = BezierPath(listOf(straightCubic(Vec2(0f, 0f), Vec2(3f, 0f))))
        val two = BezierPath(one.segments + one.segments)
        assertFailsWith<IllegalArgumentException> { one.morphedTo(two, 0.5f) }
    }
}
