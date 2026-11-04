package dev.chieppa.squash.screens.spine

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.SpineSample
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIXEL = 0.002f
private val PLANE = StrokeEnds.CutAtPlane()

/** The stroke in hand, without a window. */
class SpineEditStateTest {
    private val tail = PartId("tail")
    private fun state() = SpineEditState { 0f }

    private fun SpineEditState.draw(pressure: (Float) -> Float?, at: (Float) -> Vec2) {
        begin(tail, PLANE, PIXEL)
        for (i in 0..100) add(SpineSample(at(i / 100f), pressure(i / 100f)))
        endStroke()
    }

    @Test
    fun aPensPressureSetsTheWidthAlongTheStroke() {
        val spine = state()
        spine.draw({ 0.2f + 0.6f * it }) { Vec2(it, 0f) }
        assertTrue(spine.byPen)
        val halves = assertNotNull(spine.preview).shape.halfWidths
        val points = assertNotNull(spine.preview).shape.line.points
        val early = halves[points.indexOfFirst { it.x > 0.3f }]
        val late = halves[points.indexOfFirst { it.x > 0.7f }]
        assertTrue(late > early * 1.4f, "pressing harder widens it: $early, $late")
        assertEquals(41.5f * PIXEL * (0.2f + 0.6f * 0.7f), late, 3f * PIXEL)
        assertEquals(0f, halves.last(), "it closes at the far end")
        assertFalse(spine.pressWidth(Vec2(0.5f, 0f), 0.1f), "nothing is left to drag")
    }

    @Test
    fun aMouseStrokeHasADefaultWidthUntilDragged() {
        val spine = state()
        spine.draw({ null }) { Vec2(it, 0f) }
        assertFalse(spine.byPen)
        assertEquals(0.3f / PIXEL, assertNotNull(spine.widthPixels), 2f)
    }

    @Test
    fun theDrawerSetsTheWidthAtTheHandle() {
        val spine = state()
        spine.draw({ null }) { Vec2(it, 0f) }
        spine.setWidthPixels(51.5f)
        assertEquals(51.5f, assertNotNull(spine.widthPixels), 1e-2f)
    }

    @Test
    fun aDragAlongTheSpineLeavesEachPlaceItsWidth() {
        val spine = state()
        spine.draw({ null }) { Vec2(it, 0f) }
        assertTrue(spine.pressWidth(Vec2(0.25f, 0.05f), 0.01f))
        spine.dragWidth(Vec2(0.25f, 0.2f))
        spine.dragWidth(Vec2(0.75f, 0.05f))
        val shape = assertNotNull(spine.preview).shape
        val at = { x: Float -> shape.halfWidths[shape.line.points.indexOfFirst { it.x >= x }] }
        assertTrue(at(0.25f) > at(0.75f) * 1.5f, "${at(0.25f)}, ${at(0.75f)}")
        assertEquals(0.75f, spine.handleAt, 0.02f)
    }

    @Test
    fun aBentSpineShowsWhatTheBuildWillNotRead() {
        val spine = state()
        spine.draw({ null }) { Vec2(it * 0.3f, 1.5f * it) }
        assertTrue(spine.pressWidth(Vec2(0.09f, 0.45f), 2f))
        spine.dragWidth(Vec2(0.09f + 0.3f, 0.45f))
        val preview = assertNotNull(spine.preview)
        assertTrue(preview.unread.isNotEmpty(), "doubling back is marked")
        assertTrue(preview.read.isNotEmpty())
    }

    @Test
    fun aGentleSpineIsAllRead() {
        val spine = state()
        spine.draw({ null }) { Vec2(it, 0.1f * sin(it * 2f)) }
        val preview = assertNotNull(spine.preview)
        assertEquals(emptyList(), preview.unread)
        assertEquals(1, preview.read.size)
        assertTrue(preview.sideSpans.isNotEmpty() && preview.topSpans.isNotEmpty())
    }

    @Test
    fun clearingLeavesNothing() {
        val spine = state()
        spine.draw({ null }) { Vec2(it, 0f) }
        spine.clear()
        assertFalse(spine.active)
        assertNull(spine.preview)
        assertNull(spine.widthPixels)
    }
}
