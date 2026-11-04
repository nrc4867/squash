package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

private const val FRAME_MICROS = 8_333L
private const val WARMUP_ROUNDS = 200
private const val MEASURED_ROUNDS = 200
private const val STROKE_SAMPLES = 960

class StrokeReadingTest {
    /** Out along the top, back under itself in a hook, on again, then home along the bottom. */
    private val hooked = resample(
        listOf(
            Vec2(0f, 0.5f),
            Vec2(1.2f, 0.5f),
            Vec2(0.6f, 0.2f),
            Vec2(1.6f, 0.2f),
            Vec2(1.6f, -0.5f),
            Vec2(0f, -0.5f),
        ),
        2f * TEST_PIXEL,
    )

    private val polyline = Polyline2(FloatArray(hooked.size) { hooked[it].x }, FloatArray(hooked.size) { hooked[it].y })

    @Test
    fun aHooksInnerRunIsUnreadAndTheOutermostCrossingsAreRead() {
        val reading = assertNotNull(crossingsReading(hooked, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        assertTrue(reading.isRead(Vec2(0.5f, 0.5f)), "the top")
        assertTrue(reading.isRead(Vec2(0.8f, -0.5f)), "the bottom")
        assertTrue(reading.isRead(Vec2(1.4f, 0.2f)), "the top past the hook")
        assertTrue(!reading.isRead(Vec2(0.9f, 0.35f)), "the run back under the top")
        assertTrue(!reading.isRead(Vec2(0.8f, 0.2f)), "the run on again, under the top")
    }

    @Test
    fun aSplitAccountsForAllOfTheLine() {
        val reading = assertNotNull(crossingsReading(hooked, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        val split = reading.split(hooked)
        assertTrue(split.unread.isNotEmpty() && split.read.size >= 2)
        val total = (split.read + split.unread).sumOf { arcLength(it).toDouble() }
        assertEquals(arcLength(hooked).toDouble(), total, 1e-2)
    }

    @Test
    fun theShadeIsTheProfilesCrossings() {
        val profile = assertNotNull(OutlineProfile.of(polyline))
        val reading = assertNotNull(crossingsReading(hooked, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        val spans = reading.spans(2f * TEST_PIXEL)
        assertEquals(0f, spans.first().x - spans.first().width / 2f, 1e-4f)
        assertEquals(1.6f, spans.last().x + spans.last().width / 2f, 1e-4f)
        for (span in spans) {
            assertEquals(profile.lowAt(span.x), span.low)
            assertEquals(profile.highAt(span.x), span.high)
        }
    }

    @Test
    fun whatLiesPastTheSocketPlaneIsUnreadEvenOnAnOutermostCrossing() {
        val past = resample(listOf(Vec2(-0.3f, 0.5f), Vec2(1f, 0.5f), Vec2(1f, -0.5f), Vec2(-0.3f, -0.5f)), 2f * TEST_PIXEL)
        val reading = assertNotNull(crossingsReading(past, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        assertTrue(!reading.isRead(Vec2(-0.2f, 0.5f)))
        assertTrue(reading.isRead(Vec2(0.2f, 0.5f)))
        assertTrue(reading.spans(2f * TEST_PIXEL).all { it.x >= 0f })
    }

    @Test
    fun aStrokeStoppingShortOfThePlaneShadesToThePlaneAtItsEndsHeightAsTheBuildHoldsIt() {
        val short = resample(listOf(Vec2(0.4f, 0.5f), Vec2(1f, 0.5f), Vec2(1f, -0.5f), Vec2(0.4f, -0.5f)), 2f * TEST_PIXEL)
        val spans = assertNotNull(crossingsReading(short, StrokeEnds.CutAtPlane(), TEST_PIXEL)).spans(2f * TEST_PIXEL)
        assertEquals(0f, spans.first().x - spans.first().width / 2f, 1e-4f)
        assertEquals(0.5f, spans.first().high, 1e-3f)
    }

    @Test
    fun anEndOpenAtTheFarSideReadsUpToThePlane() {
        val under = resample(listOf(Vec2(-1f, 0.5f), Vec2(0.2f, 0.5f), Vec2(0.2f, -0.5f), Vec2(-1f, -0.5f)), 2f * TEST_PIXEL)
        val spans = assertNotNull(crossingsReading(under, StrokeEnds.CutAtPlane(above = false), TEST_PIXEL)).spans(2f * TEST_PIXEL)
        assertTrue(spans.all { it.x <= 0f })
    }

    @Test
    fun aSquareEndsWallIsRead() {
        val box = resample(listOf(Vec2(0f, 0.5f), Vec2(1f, 0.5f), Vec2(1f, -0.5f), Vec2(0f, -0.5f)), 2f * TEST_PIXEL)
        val reading = assertNotNull(crossingsReading(box, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        assertTrue(box.all(reading::isRead), "every point of a box")
    }

    @Test
    fun aWallLeaningOffSquareIsRead() {
        val leaning = through(0f to 0.5f, 0.45f to 0.5f, 0.448f to -0.5f, 0f to -0.5f)
        val preview = strokePreview(leaning, StrokeEnds.CutAtPlane(), TEST_PIXEL)
        val reading = assertNotNull(crossingsReading(preview.shape, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        assertTrue(preview.drawn.flatten().all(reading::isRead), "the hand's line along a wall the snap straightened")
    }

    @Test
    fun aStepsWallIsRead() {
        val step = resample(listOf(Vec2(0f, 0.5f), Vec2(0.6f, 0.5f), Vec2(0.6f, 0.2f), Vec2(1f, 0.2f), Vec2(1f, -0.5f), Vec2(0f, -0.5f)), 2f * TEST_PIXEL)
        val reading = assertNotNull(crossingsReading(step, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        assertTrue(step.all(reading::isRead), "the silhouette steps there")
    }

    @Test
    fun aSectionIsAllReadAndFillsWhatItEncloses() {
        val notch = resample(listOf(Vec2(-1f, -1f), Vec2(-1f, 1f), Vec2(0f, 0f), Vec2(1f, 1f), Vec2(1f, -1f), Vec2(-1f, -1f)), 2f * TEST_PIXEL)
        val reading = assertNotNull(sectionReading(notch))
        assertTrue(notch.all(reading::isRead))
        val at = reading.spans(4f * TEST_PIXEL).filter { kotlin.math.abs(it.x - 0.5f) < 4f * TEST_PIXEL }
        assertTrue(at.isNotEmpty())
        for (span in at) {
            assertEquals(-1f, span.low, 0.03f)
            assertEquals(0.5f, span.high, 0.03f)
        }
    }

    @Test
    fun readingAStrokeKeepsUpAtStrokeRate() {
        val long = List(STROKE_SAMPLES) {
            val angle = 2.0 * PI * it / (STROKE_SAMPLES - 1)
            Vec2(1.6f + 1.5f * cos(angle).toFloat(), 1.2f * sin(angle).toFloat())
        }
        val shape = strokePreview(long, StrokeEnds.CutAtPlane(), TEST_PIXEL).shape
        fun read() {
            val reading = checkNotNull(crossingsReading(shape, StrokeEnds.CutAtPlane(), TEST_PIXEL))
            reading.spans(2f * TEST_PIXEL)
            reading.split(shape)
        }
        repeat(WARMUP_ROUNDS) { read() }
        val micros = List(MEASURED_ROUNDS) { measureTime { read() }.inWholeMicroseconds }.sorted()
        val median = micros[micros.size / 2]
        println("reading a stroke: median $median us over ${shape.size} points")
        assertTrue(median < FRAME_MICROS, "takes more than a frame: $median us")
    }

    @Test
    fun aHalfStrokePreviewKeepsUpAtStrokeRate() {
        val long = List(STROKE_SAMPLES) {
            val angle = PI * it / (STROKE_SAMPLES - 1)
            Vec2(1.6f + 1.5f * cos(angle).toFloat(), 1.2f * sin(angle).toFloat())
        }
        val options = StrokeOptions(half = CentreLine.ALONG_X, marks = listOf(StrokeMark(long[300], StrokeMarkKind.CORNER)))
        repeat(WARMUP_ROUNDS) { strokePreview(long, StrokeEnds.CutAtPlane(), TEST_PIXEL, options) }
        val micros = List(MEASURED_ROUNDS) { measureTime { strokePreview(long, StrokeEnds.CutAtPlane(), TEST_PIXEL, options) }.inWholeMicroseconds }.sorted()
        val median = micros[micros.size / 2]
        println("half strokePreview: median $median us")
        assertTrue(median < FRAME_MICROS, "takes more than a frame: $median us")
    }
}
