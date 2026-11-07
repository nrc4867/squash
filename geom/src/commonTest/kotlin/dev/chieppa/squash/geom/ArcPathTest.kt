package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class ArcPathTest {
    private val quarter = ArcPath(List(201) { i -> (i / 200.0 * PI / 2).let { Vec2(cos(it).toFloat(), sin(it).toFloat()) } })

    @Test
    fun aQuarterCircleMeasuresItsArc() {
        assertEquals((PI / 2).toFloat(), quarter.length, 1e-4f)
        val middle = quarter.pointAt(quarter.length / 2)
        assertEquals(cos(PI / 4).toFloat(), middle.x, 1e-4f)
        val tangent = quarter.tangentAt(quarter.length / 2)
        assertEquals(-sin(PI / 4).toFloat(), tangent.x, 1e-3f)
    }

    @Test
    fun aReachLandsOnThePathAtTheChord() {
        val start = quarter.pointAt(0f)
        val s = quarter.reach(start, 0f, 1f)
        val point = quarter.pointAt(s)
        assertEquals(1f, (point - start).magnitude, 1e-4f)
        assertEquals((PI / 3).toFloat(), s, 1e-3f)
    }

    @Test
    fun aReachPastTheEndRunsOnAlongItsTangent() {
        val line = ArcPath(listOf(Vec2(0f, 0f), Vec2(1f, 0f)))
        val s = line.reach(Vec2(0.5f, 0f), 0.5f, 2f)
        assertEquals(2.5f, s, 1e-5f)
        assertEquals(2.5f, line.pointAt(s).x, 1e-5f)
    }

    @Test
    fun aJoinedCubicPathIsMeasuredFromItsFirstPoint() {
        val path = ArcPath.of(BezierPath(listOf(straightCubic(Vec2(0f, 0f), Vec2(3f, 4f)), straightCubic(Vec2(3f, 4f), Vec2(3f, 6f)))))
        assertEquals(7f, path.length, 1e-4f)
        assertEquals(0f, path.distanceTo(Vec2(3f, 5f)), 1e-5f)
        assertEquals(Vec2(0f, 1f), path.endTangent)
    }
}
