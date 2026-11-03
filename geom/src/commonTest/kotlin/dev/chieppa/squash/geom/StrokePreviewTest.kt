package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** A pixel, in plane units, at 200 pixels to the unit. */
private const val PIXEL = 0.005f

/** The fit's tolerance and the simplifier's, and the line the fit rounds a corner by; under half the live stroke's width. */
private const val MATCH_PIXELS = 3f

/** One display frame at 120 Hz, in microseconds. */
private const val FRAME_MICROS = 8_333L
private const val WARMUP_ROUNDS = 200
private const val MEASURED_ROUNDS = 200

/** Four seconds of a pen reporting at 240 Hz. */
private const val STROKE_SAMPLES = 960

class StrokePreviewTest {
    private val noise = Random(11)

    private fun jitter(p: Vec2, pixels: Float = 0.5f) = Vec2(
        p.x + (noise.nextFloat() - 0.5f) * 2f * pixels * PIXEL,
        p.y + (noise.nextFloat() - 0.5f) * 2f * pixels * PIXEL,
    )

    private fun arc(cx: Float, cy: Float, radius: Float, sweep: Double, from: Double = 0.0, samples: Int = 300) = List(samples) {
        val angle = from + sweep * it / (samples - 1)
        jitter(Vec2(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat()))
    }

    private fun corners(vararg points: Pair<Float, Float>) =
        resample(points.map { Vec2(it.first, it.second) }, PIXEL).map { jitter(it) }

    /** Many strokes a hand makes: loops either side of the plane, arcs, overshoots, corners, lines and ribbons. */
    private val shapes: List<List<Vec2>> = buildList {
        for (center in listOf(-0.2f, 0.3f, 0.52f, 0.6f, 1f, 2f)) {
            add(arc(center, 0f, 0.5f, 2.0 * PI))
            add(arc(center, 0f, 0.5f, 2.3 * PI))
            add(arc(center, 0f, 0.5f, 1.85 * PI, from = 0.2))
            add(arc(center, 0f, 0.5f, PI, from = -PI / 2.0))
            add(arc(center, 0f, 0.5f, PI, from = PI / 2.0))
            add(arc(center, 0.2f, 0.3f, 1.5 * PI, from = 1.0))
        }
        add(corners(0.2f to 0.5f, 1f to 0.5f, 1f to -0.5f, 0.2f to -0.5f))
        add(corners(1f to 0f, 1f to 0.5f, 0f to 0.5f, 0f to -0.5f, 1f to -0.5f, 1f to 0f))
        add(corners(-0.5f to 0.5f, 1f to 0.5f, 1f to -0.5f, -0.5f to -0.5f))
        add(corners(0f to 0f, 2f to 0f))
        add(corners(0.5f to 0.1f, 2f to 0.1f, 2f to 0.6f, -0.5f to 0.6f, -0.5f to 0.8f, 1f to 0.8f))
        add(List(300) { val t = it / 299f; jitter(Vec2(0.2f + 1.6f * t, 0.5f * sin(t * 6f * PI.toFloat()))) })
    }

    private val endings = listOf(StrokeEnds.Closed, StrokeEnds.CutAtPlane(), StrokeEnds.CutAtPlane(above = false))

    private val settings = listOf(
        StrokeOptions(),
        StrokeOptions(smoothing = 0f),
        StrokeOptions(smoothing = 1f),
        StrokeOptions(snapLine = false),
        StrokeOptions(closeEnd = false),
    )

    private fun distanceToLine(p: Vec2, line: List<Vec2>) = (1 until line.size).minOf { distanceToSegment(p, line[it - 1], line[it]) }

    @Test
    fun thePreviewedShapeIsWhatReleaseMakesForManyStrokes() {
        for (samples in shapes) for (ends in endings) for (options in settings) {
            val preview = strokePreview(samples, ends, PIXEL, options)
            val stroke = cleanStroke(samples, ends, PIXEL, options)
            if (stroke == null) {
                assertTrue(preview.shape.isEmpty(), "release makes nothing but the preview shows a shape")
                continue
            }
            assertEquals(stroke.closed, preview.closed)
            val released = stroke.path.segments.flatMap { it.sample(24) }
            val worstOut = preview.shape.maxOf { distanceToLine(it, released) }
            val worstBack = released.maxOf { distanceToLine(it, preview.shape) }
            assertTrue(worstOut <= MATCH_PIXELS * PIXEL, "shown ${worstOut / PIXEL} px from the release, $ends $options")
            assertTrue(worstBack <= MATCH_PIXELS * PIXEL, "released ${worstBack / PIXEL} px from what showed, $ends $options")
        }
    }

    @Test
    fun theDrawnConnectorsAndDroppedPartsAccountForTheStroke() {
        for (samples in shapes) for (ends in endings) {
            val preview = strokePreview(samples, ends, PIXEL)
            if (preview.shape.isEmpty()) continue
            val shown = (preview.drawn + preview.dropped).filter { it.size >= 2 }
            for (sample in samples) {
                val worst = shown.minOf { distanceToLine(sample, it) }
                assertTrue(worst <= 6f * PIXEL, "${worst / PIXEL} px of the stroke shows nowhere, $ends")
            }
            val made = (preview.drawn + preview.connectors).filter { it.size >= 2 }
            val worst = preview.shape.maxOf { p -> made.minOf { distanceToLine(p, it) } }
            assertTrue(worst <= MATCH_PIXELS * PIXEL, "${worst / PIXEL} px of the shape is neither drawn nor a connector, $ends")
        }
    }

    @Test
    fun anOpenStrokeInAClosedViewShowsTheRunBackToItsStart() {
        val samples = arc(1f, 1f, 0.5f, PI)
        val preview = strokePreview(samples, StrokeEnds.Closed, PIXEL)
        assertFalse(preview.closes)
        val connector = preview.connectors.single()
        assertEquals(samples.last().x, connector.first().x, 4f * PIXEL)
        assertEquals(samples.first().x, connector.last().x, 4f * PIXEL)
    }

    @Test
    fun theStartLightsOnlyOnceTheStrokeIsWithinClosingReach() {
        val circle = arc(1f, 1f, 0.5f, 2.0 * PI, samples = 400)
        val reach = 16f * PIXEL
        for (count in 20..circle.size step 20) {
            val part = circle.take(count)
            val preview = strokePreview(part, StrokeEnds.Closed, PIXEL)
            assertEquals((part.last() - part.first()).magnitude <= reach && count > 100, preview.closes, "at $count samples")
        }
    }

    @Test
    fun anOpenStrokeOnAnOpenPartShowsARunToThePlaneFromEachEnd() {
        val preview = strokePreview(arc(1f, 0f, 0.5f, PI, from = -PI / 2.0), StrokeEnds.CutAtPlane(), PIXEL)
        assertEquals(2, preview.connectors.size)
        for (connector in preview.connectors) {
            assertEquals(connector.first().y, connector.last().y)
            assertTrue(connector.first().x == 0f || connector.last().x == 0f, "reaches the plane")
        }
    }

    @Test
    fun whatOverRunsTheStartShowsAsDropped() {
        val preview = strokePreview(arc(1f, 1f, 0.5f, 2.4 * PI, samples = 500), StrokeEnds.Closed, PIXEL)
        assertTrue(preview.closes)
        assertTrue(preview.dropped.sumOf { arcLength(it).toDouble() } > 0.5, "the overshoot is about 0.6 long")
    }

    @Test
    fun whatLiesBehindThePlaneShowsAsDropped() {
        val preview = strokePreview(arc(0.6f, 0f, 1f, 2.0 * PI, samples = 600), StrokeEnds.CutAtPlane(), PIXEL)
        val behind = preview.dropped.flatten().minOf { it.x }
        assertTrue(behind < -0.3f, "the stretch behind the plane, down to $behind")
        assertTrue(preview.drawn.flatten().all { it.x >= -PIXEL }, "and nothing behind it stays")
    }

    @Test
    fun theChoppedArcOfABlobShowsAsDropped() {
        val preview = strokePreview(arc(1f, 0f, 0.5f, 2.0 * PI), StrokeEnds.CutAtPlane(), PIXEL)
        assertEquals(0.5f, preview.dropped.flatten().minOf { it.x }, 2f * PIXEL)
        assertEquals(2, preview.connectors.count { c -> c.any { it.x == 0f } })
    }

    @Test
    fun aStrokeWhollyBehindThePlaneIsAllDropped() {
        val samples = arc(-2f, 0f, 0.5f, 2.0 * PI)
        val preview = strokePreview(samples, StrokeEnds.CutAtPlane(), PIXEL)
        assertTrue(preview.shape.isEmpty() && preview.drawn.isEmpty())
        assertEquals(samples, preview.dropped.single())
        assertNull(cleanStroke(samples, StrokeEnds.CutAtPlane(), PIXEL))
    }

    @Test
    fun closeEndOffLeavesTheEndsWhereTheyAreWithNoConnector() {
        val samples = arc(1f, 1f, 0.5f, PI)
        val off = StrokeOptions(closeEnd = false)
        val preview = strokePreview(samples, StrokeEnds.Closed, PIXEL, off)
        assertTrue(preview.connectors.isEmpty())
        assertFalse(preview.closes)
        val stroke = assertNotNull(cleanStroke(samples, StrokeEnds.Closed, PIXEL, off))
        assertFalse(stroke.closed)
        assertEquals(samples.first(), stroke.path.segments.first().p0)
        assertEquals(samples.last(), stroke.path.segments.last().p3)

        val short = arc(1f, 0f, 0.5f, PI, from = -PI / 2.0)
        val onPlane = assertNotNull(cleanStroke(short, StrokeEnds.CutAtPlane(), PIXEL, off))
        assertEquals(short.first(), onPlane.path.segments.first().p0, "the end stays off the plane")
        assertTrue(strokePreview(short, StrokeEnds.CutAtPlane(), PIXEL, off).connectors.isEmpty())
    }

    @Test
    fun closeEndOffStillCutsWhatPassesThePlane() {
        val past = arc(0f, 0f, 0.5f, PI, from = -PI / 2.0 - 0.5)
        val preview = strokePreview(past, StrokeEnds.CutAtPlane(), PIXEL, StrokeOptions(closeEnd = false))
        val stroke = assertNotNull(cleanStroke(past, StrokeEnds.CutAtPlane(), PIXEL, StrokeOptions(closeEnd = false)))
        assertEquals(0f, stroke.path.segments.first().p0.x)
        assertTrue(preview.dropped.isNotEmpty())
    }

    @Test
    fun smoothingIsWiredToTheStroke() {
        val rough = List(400) { Vec2(it * PIXEL, 0.5f + (noise.nextFloat() - 0.5f) * 2f * PIXEL) }
        fun roughness(smoothing: Float): Float {
            val line = strokePreview(rough, StrokeEnds.CutAtPlane(), PIXEL, StrokeOptions(smoothing, snapLine = false)).drawn.flatten()
            return (1 until line.size).sumOf { kotlin.math.abs(line[it].y - line[it - 1].y).toDouble() }.toFloat()
        }
        assertTrue(roughness(1f) < roughness(0f) / 2f, "${roughness(1f)} against ${roughness(0f)}")
    }

    @Test
    fun aSectionNearItsChordBecomesExactlyStraightWithSnapLine() {
        val bow = List(200) { val t = it / 199f; Vec2(0.2f + t, 0.5f + 1.5f * PIXEL * sin(PI.toFloat() * t)) }
        val bent = bow + resample(listOf(bow.last(), Vec2(1.2f, -0.5f)), PIXEL).drop(1)
        val first = assertNotNull(cleanStroke(bent, StrokeEnds.CutAtPlane(), PIXEL)).path.segments
        assertTrue(first.all { it.isStraight() }, "every section between corners is a line")
        assertTrue(first.size <= 5, "one cubic each: ${first.size}")
        val off = assertNotNull(cleanStroke(bent, StrokeEnds.CutAtPlane(), PIXEL, StrokeOptions(snapLine = false))).path.segments
        assertTrue(off.any { !it.isStraight() }, "off, the hand's line stays")
    }

    @Test
    fun aCurvedSectionIsNotSnapped() {
        val curve = arc(1f, 0f, 0.5f, PI, from = -PI / 2.0)
        val stroke = assertNotNull(cleanStroke(curve, StrokeEnds.CutAtPlane(), PIXEL))
        assertTrue(stroke.path.segments.count { !it.isStraight() } >= 2)
    }

    private fun CubicBezier.isStraight(): Boolean {
        val chord = p3 - p0
        val across = { p: Vec2 -> ((p - p0).x * chord.y - (p - p0).y * chord.x) / chord.magnitude }
        return kotlin.math.abs(across(p1)) < 1e-4f && kotlin.math.abs(across(p2)) < 1e-4f
    }

    /** Every tail edge against every head edge, latest in the tail first. */
    private fun firstCrossingByFullSearch(points: List<Vec2>): Vec2? {
        val headEnd = points.size / 3
        for (i in points.lastIndex - 1 downTo points.size - headEnd - 1) {
            for (j in 0 until headEnd) segmentCrossing(points[i], points[i + 1], points[j], points[j + 1])?.let { return it }
        }
        return null
    }

    @Test
    fun theLoopSearchFindsTheCrossingTheFullSearchFinds() {
        val strokes = shapes + listOf(arc(1f, 1f, 0.5f, 3.6 * PI, samples = 900))
        for (samples in strokes) {
            val dense = resample(samples, 2f * PIXEL)
            val crossing = firstCrossingByFullSearch(dense) ?: continue
            assertEquals(crossing, findLoop(dense, 16f * PIXEL, 2f * PIXEL)?.line?.first())
        }
    }

    @Test
    fun thePreviewKeepsUpAtStrokeRate() {
        val long = List(STROKE_SAMPLES) {
            val angle = 2.0 * PI * it / (STROKE_SAMPLES - 1)
            Vec2(1f + 1.5f * cos(angle).toFloat(), 1.2f * sin(angle).toFloat())
        }
        for (ends in listOf(StrokeEnds.Closed, StrokeEnds.CutAtPlane())) {
            repeat(WARMUP_ROUNDS) { strokePreview(long, ends, PIXEL) }
            val micros = List(MEASURED_ROUNDS) { measureTime { strokePreview(long, ends, PIXEL) }.inWholeMicroseconds }.sorted()
            val median = micros[micros.size / 2]
            println("strokePreview $ends: median $median us, p99 ${micros[micros.size * 99 / 100]} us, over $STROKE_SAMPLES samples")
            assertTrue(median < FRAME_MICROS, "$ends takes more than a frame: $median us")
        }
    }

    @Test
    fun aDrawnLoopStaysWithinAFrameOnALargeStroke() {
        val open = List(STROKE_SAMPLES) {
            val angle = 1.9 * PI * it / (STROKE_SAMPLES - 1)
            Vec2(1f + 3f * cos(angle).toFloat(), 2.5f * sin(angle).toFloat())
        }
        repeat(WARMUP_ROUNDS) { strokePreview(open, StrokeEnds.CutAtPlane(), PIXEL) }
        val micros = List(MEASURED_ROUNDS) { measureTime { strokePreview(open, StrokeEnds.CutAtPlane(), PIXEL) }.inWholeMicroseconds }.sorted()
        val median = micros[micros.size / 2]
        println("strokePreview, far gap: median $median us, ${resample(open, 2f * PIXEL).size} resampled points")
        assertTrue(median < FRAME_MICROS, "takes more than a frame: $median us")
    }
}
