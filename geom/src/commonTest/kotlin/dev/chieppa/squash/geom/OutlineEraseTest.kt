package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals

class OutlineEraseTest {
    private fun straight(from: Float, to: Float) =
        CubicBezier(Vec2(from, 0f), Vec2(from + (to - from) / 3f, 0f), Vec2(from + 2f * (to - from) / 3f, 0f), Vec2(to, 0f))

    private val path = BezierPath((0 until 4).map { straight(it * 10f, (it + 1) * 10f) })

    @Test
    fun aStrokeFarFromTheCurveErasesNothing() {
        assertEquals(EraseReach.Nothing, eraseReach(path, listOf(Vec2(5f, 9f), Vec2(25f, 9f)), reach = 1f))
    }

    @Test
    fun aDabErasesTheSectionUnderIt() {
        assertEquals(EraseReach.Sections(setOf(1)), eraseReach(path, listOf(Vec2(15f, 0.5f)), reach = 1f))
    }

    @Test
    fun aStrokeAcrossSectionsErasesThoseItPasses() {
        assertEquals(EraseReach.Sections(setOf(0, 1)), eraseReach(path, listOf(Vec2(5f, 0.5f), Vec2(15f, 0.5f)), reach = 1f))
    }

    @Test
    fun aStrokeOverMostOfTheCurveErasesAllOfIt() {
        assertEquals(EraseReach.Whole, eraseReach(path, listOf(Vec2(5f, 0f), Vec2(35f, 0f)), reach = 1f))
    }

    @Test
    fun aFastStrokeStillCountsWhatItCrossesBetweenSamples() {
        val crossing = eraseReach(path, listOf(Vec2(15f, -5f), Vec2(15f, 5f)), reach = 1f)
        assertEquals(EraseReach.Sections(setOf(1)), crossing)
    }
}
