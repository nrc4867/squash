package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIXEL = 0.005f
private const val SEEDS = 12

/** How far a hand strays from where it means to go, in plane units: a little under two pixels. */
private const val JITTER = 0.008f

class StrokeClassifierTest {
    private fun jittered(points: List<Vec2>, seed: Int, amount: Float = JITTER): List<Vec2> {
        val random = Random(seed)
        var drift = Vec2(0f, 0f)
        return points.map {
            drift = drift * 0.7f + Vec2(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f) * amount
            it + drift
        }
    }

    private fun rotated(points: List<Vec2>, angle: Float) = points.map {
        Vec2(it.x * cos(angle) - it.y * sin(angle), it.x * sin(angle) + it.y * cos(angle))
    }

    private fun line(from: Vec2, to: Vec2, count: Int = 80) = List(count) { from + (to - from) * (it.toFloat() / (count - 1)) }

    /** From angle 0 around by [turns], so more than one overshoots the start and less stops short of it. */
    private fun ellipse(a: Float, b: Float, turns: Float = 1f, count: Int = 160) = List(count) {
        val angle = 2f * PI.toFloat() * turns * it / (count - 1)
        Vec2(a * cos(angle), b * sin(angle))
    }

    private fun capsule(run: Float, radius: Float) = CapsuleShape(Vec2(-run, 0f), Vec2(run, 0f), radius, Vec2(run, -radius), true).outline(0.01f)

    private val angles = listOf(0f, 0.4f, 1.1f, 2.3f)

    @Test
    fun aNoisyLineIsALineFromItsStartToItsEnd() {
        for (seed in 0 until SEEDS) for (angle in angles) {
            val stroke = jittered(rotated(line(Vec2(-1f, 0f), Vec2(1f, 0f)), angle), seed)
            val shape = assertIs<LineShape>(classifyStroke(stroke, PIXEL), "seed $seed at $angle")
            assertEquals(stroke.first(), shape.from)
            assertEquals(stroke.last(), shape.to)
        }
    }

    @Test
    fun aNoisyEllipseIsAnEllipseOfItsSizeAndTurn() {
        for (seed in 0 until SEEDS) for (angle in angles) for ((a, b) in listOf(1f to 1f, 1f to 0.7f, 1f to 0.45f)) {
            val stroke = jittered(rotated(ellipse(a, b), angle), seed)
            val shape = assertIs<EllipseShape>(classifyStroke(stroke, PIXEL), "seed $seed at $angle, $a by $b")
            assertEquals(a, shape.major.magnitude, 0.05f)
            assertEquals(b, shape.minorRadius, 0.05f)
            assertEquals(0f, shape.center.magnitude, 0.05f)
            if (a != b) assertTrue(abs(shape.major.unit() dotWith Vec2(cos(angle), sin(angle))) > 0.99f, "seed $seed at $angle")
        }
    }

    @Test
    fun aLoopThatOvershootsOrStopsShortStillClosesAsAnEllipse() {
        for (seed in 0 until SEEDS) for (turns in listOf(0.93f, 1.08f)) {
            val shape = classifyStroke(jittered(ellipse(1f, 0.7f, turns), seed), PIXEL)
            assertIs<EllipseShape>(shape, "seed $seed, $turns turns")
        }
    }

    @Test
    fun aNoisyCapsuleIsACapsuleOfItsSizeAndAxis() {
        for (seed in 0 until SEEDS) for (angle in angles) for ((run, radius) in listOf(1f to 0.3f, 0.8f to 0.5f)) {
            val stroke = jittered(rotated(capsule(run, radius), angle), seed)
            val shape = assertIs<CapsuleShape>(classifyStroke(stroke, PIXEL), "seed $seed at $angle, $run by $radius")
            assertEquals(radius, shape.radius, 0.05f)
            assertEquals(2f * run, (shape.to - shape.from).magnitude, 0.1f)
        }
    }

    @Test
    fun aShapeRunsTheWayTheStrokeRanFromWhereItStarted() {
        for (forward in listOf(true, false)) {
            val stroke = ellipse(1f, 0.7f).let { if (forward) it else it.reversed() }
            val outline = assertNotNull(classifyStroke(stroke, PIXEL)).outline(0.01f)
            assertTrue((outline.first() - stroke.first()).magnitude < 0.03f)
            assertEquals(outline.first(), outline.last())
            val area = (1 until outline.size).sumOf { (outline[it - 1].x * outline[it].y - outline[it].x * outline[it - 1].y).toDouble() }
            assertEquals(forward, area > 0.0)
        }
    }

    private fun zigzag(): List<Vec2> = List(120) { Vec2(-1f + it / 60f, if (it / 15 % 2 == 0) (it % 15) * 0.02f else (15 - it % 15) * 0.02f) }

    private fun polygon(vararg corners: Pair<Float, Float>): List<Vec2> {
        val at = corners.map { Vec2(it.first, it.second) }
        return (at + at.first()).zipWithNext().flatMap { (a, b) -> line(a, b, 40).dropLast(1) } + at.first()
    }

    private val freehand: Map<String, List<Vec2>> = mapOf(
        "a shallow arc" to List(100) {
            val x = -1f + it / 49.5f
            Vec2(x, 0.2f * (1f - x * x))
        },
        "an S" to List(120) {
            val x = -1f + it / 59.5f
            Vec2(x, 0.2f * sin(x * PI.toFloat()))
        },
        "a hook" to line(Vec2(-1f, 0f), Vec2(1f, 0f)) + line(Vec2(1f, 0f), Vec2(1f, 0.5f), 30).drop(1),
        "an open U" to ellipse(1f, 0.4f, 0.5f),
        "three quarters of a turn" to ellipse(1f, 0.7f, 0.75f),
        "a zigzag" to zigzag(),
        "a square" to polygon(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f),
        "a long box" to polygon(-1.5f to -0.5f, 1.5f to -0.5f, 1.5f to 0.5f, -1.5f to 0.5f),
        "a triangle" to polygon(-1f to -0.8f, 1f to -0.8f, 0f to 1f),
        "a double turn" to List(240) {
            val r = 1f - it / 480f
            val t = 4f * PI.toFloat() * it / 239f
            Vec2(r * cos(t), r * sin(t))
        },
        "a kidney" to List(160) {
            val t = 2f * PI.toFloat() * it / 159f
            val r = 1f + 0.35f * cos(2f * t) + 0.15f * cos(t)
            Vec2(r * cos(t), 0.8f * r * sin(t))
        },
        "a leg" to polygon(0f to 0.2f, 0.8f to 0.15f, 1f to 0.3f, 1.2f to 0.1f, 1.2f to -0.1f, 0f to -0.2f),
    )

    @Test
    fun whatIsNoCleanShapeStaysFreehand() {
        for ((name, stroke) in freehand) {
            assertNull(classifyStroke(stroke, PIXEL), name)
            for (seed in 0 until SEEDS) assertNull(classifyStroke(jittered(stroke, seed), PIXEL), "$name, seed $seed")
        }
    }

    @Test
    fun aStrokeTooShortToReadHasNoShape() {
        assertNull(classifyStroke(line(Vec2(0f, 0f), Vec2(0.1f, 0f)), PIXEL))
        assertNull(classifyStroke(listOf(Vec2(0f, 0f)), PIXEL))
    }

    @Test
    fun theSameStrokeReadsTheSameAtAnyScaleOfPixel() {
        val stroke = ellipse(1f, 0.7f)
        assertIs<EllipseShape>(classifyStroke(stroke.map { it * 4f }, PIXEL * 4f))
        assertIs<EllipseShape>(classifyStroke(stroke.map { it * 0.25f }, PIXEL * 0.25f))
    }

    @Test
    fun aLinesEndFollowsThePointerAndItsStartStays() {
        val shape = LineShape(Vec2(0f, 0f), Vec2(1f, 0f)).adjusted(Vec2(1f, 0f), Vec2(1.5f, 0.5f))
        assertEquals(LineShape(Vec2(0f, 0f), Vec2(1.5f, 0.5f)), shape)
    }

    @Test
    fun aClosedShapesSizeFollowsThePointersDistanceFromItsCentre() {
        val ellipse = EllipseShape(Vec2(1f, 0f), Vec2(1f, 0f), 0.5f, Vec2(2f, 0f), true).adjusted(Vec2(2f, 0f), Vec2(3f, 0f))
        assertEquals(Vec2(2f, 0f), ellipse.major)
        assertEquals(1f, ellipse.minorRadius)
        assertEquals(Vec2(1f, 0f), ellipse.center)
        val capsule = CapsuleShape(Vec2(-1f, 0f), Vec2(1f, 0f), 0.5f, Vec2(1.5f, 0f), true).adjusted(Vec2(1.5f, 0f), Vec2(0.75f, 0f))
        assertEquals(Vec2(-0.5f, 0f), capsule.from)
        assertEquals(Vec2(0.5f, 0f), capsule.to)
        assertEquals(0.25f, capsule.radius)
    }

    @Test
    fun aHeldShapeGoesThroughTheSameEndsAsAStroke() {
        val shape = EllipseShape(Vec2(0.6f, 0f), Vec2(1f, 0f), 0.4f, Vec2(1.6f, 0f), true)
        val plain = StrokeOptions(smoothing = 0f)
        val cut = assertNotNull(cleanStroke(shape.samples(PIXEL), StrokeEnds.CutAtPlane(), PIXEL, plain))
        val flat = cut.path.flatten()
        assertEquals(0f, flat.xs.first())
        assertEquals(0f, flat.xs.last())
        assertTrue(flat.xs.all { it >= -1e-3f }, "cut at the socket plane")
        assertTrue(flat.xs.max() in 1.59f..1.61f)
        val closed = assertNotNull(cleanStroke(shape.samples(PIXEL), StrokeEnds.Closed, PIXEL, plain))
        assertEquals(closed.path.segments.first().p0, closed.path.segments.last().p3)
        assertTrue(closed.maxError < 2f * PIXEL)
    }
}
