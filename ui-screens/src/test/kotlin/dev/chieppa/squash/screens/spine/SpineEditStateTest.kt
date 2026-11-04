package dev.chieppa.squash.screens.spine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.SnapGuide
import dev.chieppa.squash.geom.SpineSample
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.screens.sheet.PaneLine
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

    private class Settings(stabilizer: Float, snap: Boolean = true) {
        var stabilizer by mutableStateOf(stabilizer)
        var snap by mutableStateOf(snap)
    }

    private fun straight(settings: Settings, guides: SpineGuides? = null): SpineEditState {
        val spine = SpineEditState({ settings.stabilizer }, { settings.snap }) { 0f }
        spine.begin(tail, PLANE, PIXEL, guides)
        for (i in 0..100) spine.add(SpineSample(Vec2(i / 100f, 0f)))
        return spine
    }

    private fun guideAcrossAt(x: Float) = SpineGuides(
        listOf(PaneLine(Offset(x, 0f), Offset(x, 1f))),
        listOf(SnapGuide(Vec2(x, -1f), Vec2(x, 1f))),
    )

    @Test
    fun theStabilizerLeavesAStringFromTheTrailingLineToThePointerUntilRelease() {
        val spine = straight(Settings(1f))
        val string = assertNotNull(spine.string)
        assertEquals(1f, string.second.x, 1e-4f, "the string ends at the pointer")
        assertEquals(1f - 24f * PIXEL, string.first.x, 1e-4f, "and it is as long as the slider says")
        assertEquals(1f, assertNotNull(spine.preview).shape.line.points.last().x, 1e-4f, "the spine runs on to the pointer, where release finishes it")
        spine.endStroke()
        assertNull(spine.string)
    }

    @Test
    fun noStabilizerLeavesNoString() {
        assertNull(straight(Settings(0f)).string)
    }

    @Test
    fun theStabilizerSteadiesASpineAsItDoesAStroke() {
        fun drawn(stabilizer: Float): List<Vec2> {
            val spine = SpineEditState({ stabilizer }, { false }) { 0f }
            spine.begin(tail, PLANE, PIXEL)
            for (i in 0..200) spine.add(SpineSample(Vec2(i / 200f, if (i % 2 == 0) 0.006f else -0.006f)))
            return assertNotNull(spine.preview).shape.line.points
        }
        fun wander(points: List<Vec2>) = points.subList(10, points.size - 40).maxOf { kotlin.math.abs(it.y) }
        assertTrue(wander(drawn(1f)) < wander(drawn(0f)) / 2f, "${wander(drawn(1f))} against ${wander(drawn(0f))}")
    }

    @Test
    fun theFarEndCatchesAGuideWithinReachAndNotPastIt() {
        val near = straight(Settings(0f), guideAcrossAt(1f + 5f * PIXEL))
        assertEquals(1f + 5f * PIXEL, assertNotNull(near.preview).shape.line.points.last().x, 1e-4f)
        assertEquals(1, near.caughtGuides.size)
        val far = straight(Settings(0f), guideAcrossAt(1f + 12f * PIXEL))
        assertEquals(1f, assertNotNull(far.preview).shape.line.points.last().x, 1e-4f)
        assertTrue(far.caughtGuides.isEmpty())
    }

    @Test
    fun snapToGuidesOffCatchesNothingEvenOnAStrokeInHand() {
        val settings = Settings(0f)
        val spine = straight(settings, guideAcrossAt(1f + 5f * PIXEL))
        assertEquals(1, spine.caughtGuides.size)
        settings.snap = false
        assertTrue(spine.caughtGuides.isEmpty())
        assertEquals(1f, assertNotNull(spine.preview).shape.line.points.last().x, 1e-4f)
    }

    @Test
    fun theStabilizerReshapesAStrokeInHand() {
        val settings = Settings(1f)
        val spine = straight(settings)
        assertNotNull(spine.string)
        settings.stabilizer = 0f
        assertNull(spine.string)
    }

    @Test
    fun aStrokeDrawnTowardsThePlaneCatchesAtItsStartWhichIsTheFarEnd() {
        val spine = SpineEditState({ 0f }, { true }) { 0f }
        spine.begin(tail, PLANE, PIXEL, guideAcrossAt(1f + 5f * PIXEL))
        for (i in 100 downTo 0) spine.add(SpineSample(Vec2(i / 100f, 0f)))
        assertEquals(1f + 5f * PIXEL, assertNotNull(spine.preview).shape.line.points.last().x, 1e-4f)
    }

    @Test
    fun aDragOfTheWidthAnswersToThePointerWithoutAString() {
        val spine = straight(Settings(1f))
        spine.endStroke()
        assertTrue(spine.pressWidth(Vec2(0.5f, 0f), 10f * PIXEL))
        val half = assertNotNull(spine.widthPixels) / 2f * PIXEL
        spine.dragWidth(Vec2(0.5f, 3f * PIXEL))
        assertEquals((half + 3f * PIXEL) * 2f / PIXEL, assertNotNull(spine.widthPixels), 1.5f, "three pixels out is three pixels on the half-width")
    }
}
