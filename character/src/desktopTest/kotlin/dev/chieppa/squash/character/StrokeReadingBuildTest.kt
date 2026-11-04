package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.OutlineReading
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.ReadStrip
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.cleanStroke
import dev.chieppa.squash.geom.crossingsReading
import dev.chieppa.squash.geom.resample
import dev.chieppa.squash.geom.strokePreview
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A pixel, in plane units, at 500 pixels to the unit. */
private const val PIXEL = 0.002f
private const val STRIP = 2f * PIXEL

/** How far a built ring's end may lie from what was shaded: the fit's tolerance and a table station. */
private const val MATCH = 0.012f

/** Rings this near a step in the silhouette read one side of it or the other. */
private const val STEP_MARGIN = 0.03f

/** The shade on a stroke against the built mesh: what the preview says is read is what the slices take. See HLD: data-model, Views set slices. */
class StrokeReadingBuildTest {
    private val leg = template("leg")
    private val axes = PartBuilder.axes(leg)
    private val ends = StrokeEnds.CutAtPlane(above = true)

    private fun stroke(vararg points: Pair<Float, Float>) = resample(points.map { Vec2(it.first, it.second) }, PIXEL)

    private fun Patch.ring(index: Int): List<Vec3> = List(nv) { v ->
        val o = (index * nv + v) * Patch.COMPONENTS
        Vec3(points[o], points[o + 1], points[o + 2])
    }

    private fun readingOf(samples: List<Vec2>, options: StrokeOptions = StrokeOptions()): OutlineReading =
        assertNotNull(crossingsReading(strokePreview(samples, ends, PIXEL, options).shape, ends, PIXEL))

    private fun List<ReadStrip>.at(x: Float) = minBy { abs(it.x - x) }

    private fun built(samples: List<Vec2>, view: View, options: StrokeOptions = StrokeOptions()): Patch {
        val outline = assertNotNull(cleanStroke(samples, ends, PIXEL, options)).path.toOutline()
        return PartBuilder.build(leg, outlines = mapOf(view to outline)).first()
    }

    private fun assertRingsLandOnTheShade(patch: Patch, reading: OutlineReading, across: Vec3, steps: List<Float> = emptyList(), lastRings: Int = 2) {
        val spans = reading.spans(STRIP)
        var compared = 0
        for (index in 0 until patch.nu - lastRings) {
            val ring = patch.ring(index)
            val a = ring.first() dot axes.along
            if (steps.any { abs(it - a) < STEP_MARGIN }) continue
            val span = spans.at(a)
            assertEquals(span.high, ring.maxOf { it dot across }, MATCH, "top of ring $index at a = $a")
            assertEquals(span.low, ring.minOf { it dot across }, MATCH, "foot of ring $index at a = $a")
            compared++
        }
        assertTrue(compared > patch.nu / 2, "only $compared of ${patch.nu} rings compared")
    }

    @Test
    fun theShadeOfASideStrokeIsTheBuiltSilhouette() {
        val samples = stroke(0f to 0.5f, 0.6f to 0.4f, 1f to 0.2f, 1f to -0.3f, 0.5f to -0.4f, 0f to -0.3f)
        assertRingsLandOnTheShade(built(samples, View.SIDE), readingOf(samples), axes.up)
    }

    @Test
    fun aHooksShadeIsTheBuiltSilhouetteWithItsInnerRunUnread() {
        val samples = stroke(0f to 0.5f, 1.2f to 0.5f, 0.6f to 0.2f, 1.6f to 0.2f, 1.6f to -0.5f, 0f to -0.5f)
        val reading = readingOf(samples)
        assertRingsLandOnTheShade(built(samples, View.SIDE), reading, axes.up, steps = listOf(1.2f))
        assertTrue(reading.split(strokePreview(samples, ends, PIXEL).drawn.flatten()).unread.isNotEmpty(), "the inner run shows unread")
    }

    @Test
    fun aStrokePastThePlaneShadesOnlyWhatTheBuildKeeps() {
        val samples = stroke(-0.4f to 0.5f, 1f to 0.5f, 1f to -0.5f, -0.4f to -0.5f)
        val patch = built(samples, View.SIDE)
        val reading = readingOf(samples)
        assertRingsLandOnTheShade(patch, reading, axes.up)
        assertEquals(0f, patch.ring(0).first() dot axes.along, MATCH, "the first ring on the plane")
        assertTrue(reading.spans(STRIP).all { it.x >= 0f })
    }

    @Test
    fun closeEndOffShadesTheChordAndTheHoldToThePlaneAsTheBuildReadsThem() {
        val options = StrokeOptions(closeEnd = false)
        val samples = stroke(0.3f to 0.5f, 1f to 0.3f, 1f to -0.3f, 0.3f to -0.5f)
        assertRingsLandOnTheShade(built(samples, View.SIDE, options), readingOf(samples, options), axes.up)
    }

    @Test
    fun aTopStrokesShadeHasTheBuiltWidth() {
        val samples = stroke(0f to 0.3f, 1f to 0.2f, 1f to -0.2f, 0f to -0.3f)
        val patch = built(samples, View.TOP)
        val spans = readingOf(samples).spans(STRIP)
        for (index in 0 until patch.nu - 2) {
            val ring = patch.ring(index)
            val span = spans.at(ring.first() dot axes.along)
            assertEquals(span.high - span.low, ring.maxOf { it dot axes.side } - ring.minOf { it dot axes.side }, 2f * MATCH, "ring $index")
        }
    }

    @Test
    fun aHalfOutlinesShadeIsTheBuiltSilhouette() {
        val options = StrokeOptions(half = dev.chieppa.squash.geom.CentreLine.ALONG_X)
        val samples = stroke(0f to 0.5f, 0.6f to 0.4f, 1f to 0f)
        assertRingsLandOnTheShade(built(samples, View.SIDE, options), readingOf(samples, options), axes.up)
    }
}
