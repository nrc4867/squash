package dev.chieppa.squash.character.keys

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.OutlineScale
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.WidthScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A key's widths read over a part's outlines: eased between places, each side of the axis by its own run, and over whatever the part is. */
class KeyWidthsTest {
    private val places = listOf(WidthScale(0.1f, 1f), WidthScale(0.3f, 1.5f))

    /** A tube's Side outline: out along +y at [radius], round the tip and back along -y. */
    private fun tube(radius: Float) = OutlineCurve(
        listOf(
            CurvePoint(0f, radius), CurvePoint(0.1f, radius), CurvePoint(0.3f, radius), CurvePoint(0.4f, radius),
            CurvePoint(0.45f, radius), CurvePoint(0.45f, -radius), CurvePoint(0.4f, -radius),
            CurvePoint(0.3f, -radius), CurvePoint(0.1f, -radius), CurvePoint(0f, -radius),
        ),
    )

    @Test
    fun aScaleEasesBetweenItsPlacesAndHoldsPastThem() {
        assertEquals(1f, scaleAt(places, 0f))
        assertEquals(1.5f, scaleAt(places, 0.9f))
        assertEquals(1.25f, scaleAt(places, 0.2f), 1e-5f)
        val steps = (0..20).map { scaleAt(places, 0.1f + 0.01f * it) }
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a }, "it rises without turning back: $steps")
        assertTrue(steps[1] - steps[0] < steps[11] - steps[10], "it leaves its places level, so the outline keeps its slope there")
        assertEquals(1f, scaleAt(emptyList(), 0.2f))
    }

    @Test
    fun eachSideOfTheAxisTakesItsOwnRunAndTheAxisStays() {
        val scaled = scaled(tube(0.05f), OutlineScale(plus = places))
        val plus = scaled.points.filter { it.y > 0f }
        val minus = scaled.points.filter { it.y < 0f }
        assertEquals(0.075f, plus.first { it.x == 0.3f }.y, 1e-5f)
        assertTrue(minus.all { it.y == -0.05f }, "the side below the axis keeps its widths")
        assertEquals(tube(0.05f).points.size, scaled.points.size)
    }

    @Test
    fun anEditOfThePartKeepsTheKeysChange() {
        val change = PartChange(PartId("arm-l"), mapOf(View.SIDE to OutlineScale(places, places)))
        val drawn = PartData(outlines = mapOf(View.SIDE to tube(0.05f), View.TOP to tube(0.04f)))
        val edited = PartData(outlines = mapOf(View.SIDE to tube(0.07f), View.TOP to tube(0.04f)))
        val keyedDrawn = keyed(drawn, change).outlines.getValue(View.SIDE).points.first { it.x == 0.3f && it.y > 0f }.y
        val keyedEdited = keyed(edited, change).outlines.getValue(View.SIDE).points.first { it.x == 0.3f && it.y > 0f }.y
        assertEquals(0.075f, keyedDrawn, 1e-5f)
        assertEquals(0.105f, keyedEdited, 1e-5f, "the edited width, scaled as the key scales it")
        assertEquals(edited.outlines.getValue(View.TOP), keyed(edited, change).outlines.getValue(View.TOP), "a view the key leaves is as drawn")
    }
}
