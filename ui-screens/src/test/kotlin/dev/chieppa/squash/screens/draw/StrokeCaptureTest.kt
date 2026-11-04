package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.geom.StrokeMarkKind
import dev.chieppa.squash.geom.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StrokeCaptureTest {
    @Test
    fun aHeldPointerLeavesTheLineWhereItIs() {
        val capture = StrokeCapture()
        capture.begin(Vec2(0f, 0f), 0.1f, alt = false, shift = false)
        capture.moveTo(Vec2(0.05f, 0f))
        assertEquals(Vec2(0f, 0f), capture.lineEnd, "slack")
        capture.moveTo(Vec2(0.5f, 0f))
        assertEquals(0.4f, assertNotNull(capture.lineEnd).x, 1e-5f)
        val line = capture.samples
        capture.moveTo(Vec2(0.5f, 0f))
        assertEquals(line, capture.samples)
        assertEquals(Vec2(0.5f, 0f), capture.samples.last(), "the tip is the pointer")
    }

    @Test
    fun aModifierPressedOnceMarksOnceWhereThePointerIs() {
        val capture = StrokeCapture()
        capture.begin(Vec2(0f, 0f), 0.1f, alt = false, shift = false)
        capture.moveTo(Vec2(0.5f, 0f))
        assertEquals(null, capture.modifiers(alt = false, shift = false))
        val kind = assertNotNull(capture.modifiers(alt = true, shift = false))
        capture.mark(kind)
        assertEquals(Vec2(0.5f, 0f), capture.marks.single().at)
        assertEquals(Vec2(0.5f, 0f), capture.lineEnd, "the line reaches the mark")
        assertEquals(null, capture.modifiers(alt = true, shift = false), "held on, it marks no more")
        capture.modifiers(alt = false, shift = false)
        assertEquals(StrokeMarkKind.SMOOTH, capture.modifiers(alt = false, shift = true))
    }

    @Test
    fun aModifierHeldFromThePressMarksNothing() {
        val capture = StrokeCapture()
        capture.begin(Vec2(0f, 0f), 0f, alt = true, shift = true)
        assertEquals(null, capture.modifiers(alt = true, shift = true))
    }
}
