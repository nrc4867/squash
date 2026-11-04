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

private val ABOVE = StrokeEnds.CutAtPlane()

class StrokeAcrossPlaneTest {
    private fun jittered(points: List<Vec2>, seed: Int): List<Vec2> {
        val random = Random(seed)
        var drift = Vec2(0f, 0f)
        return points.map {
            drift = drift * 0.7f + Vec2(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f) * JITTER
            it + drift
        }
    }

    /** Straight in along y = [half] from x = [from], round a half-circle reaching x = [far], and straight back along y = -[half]. */
    private fun capsuleU(far: Float, half: Float, from: Float = -0.1f, backTo: Float = from): List<Vec2> {
        val turn = far - half
        val cap = List(61) {
            val angle = PI / 2.0 - PI * it / 60.0
            Vec2(turn + half * cos(angle).toFloat(), half * sin(angle).toFloat())
        }
        return through(from to half, turn to half) + cap.drop(1) + through(turn to -half, backTo to -half).drop(1)
    }

    /** The ellipse with half axes [a] along x and [b] along y around ([cx], 0), between where it meets x = [cut] on either side, the far way round. */
    private fun ellipseU(cx: Float, a: Float, b: Float, cut: Float = -0.05f): List<Vec2> {
        val from = kotlin.math.acos(((cut - cx) / a).toDouble())
        return List(240) {
            val angle = from - 2.0 * from * it / 239.0
            Vec2(cx + a * cos(angle).toFloat(), b * sin(angle).toFloat())
        }
    }

    private fun tilted(points: List<Vec2>, slope: Float) = points.map { Vec2(it.x, it.y + slope * it.x) }

    /** [shape] as release takes it across [plane]. */
    private fun released(shape: StrokeShape, plane: StrokeEnds.CutAtPlane = ABOVE) =
        assertNotNull(cleanStroke(shape.samples(PIXEL), plane, PIXEL, StrokeOptions(smoothing = 0f))).path.flatten()

    @Test
    fun aLongUAcrossThePlaneIsACapsuleCutThere() {
        for (seed in 0 until SEEDS) {
            val shape = assertIs<CapsuleShape>(classifyStroke(jittered(capsuleU(1.2f, 0.3f), seed), PIXEL, ABOVE), "seed $seed")
            assertEquals(0.3f, shape.radius, 0.03f)
            val path = released(shape)
            assertEquals(0f, path.xs.first())
            assertEquals(0f, path.xs.last())
            assertTrue(path.xs.all { it >= -1e-3f }, "nothing behind the plane")
            assertEquals(1.2f, path.xs.max(), 0.03f, "it reaches as far as the stroke")
            assertEquals(0.6f, abs(path.ys.first() - path.ys.last()), 0.06f, "it meets the plane where the stroke did")
        }
    }

    @Test
    fun aRoundUAcrossThePlaneIsAnEllipseCutThereWhereverItsCentreLies() {
        for ((cx, a) in listOf(0f to 0.8f, 0.2f to 0.8f, -0.25f to 1f)) for (seed in 0 until SEEDS) {
            val shape = assertIs<EllipseShape>(classifyStroke(jittered(ellipseU(cx, a, 0.45f), seed), PIXEL, ABOVE), "centre $cx seed $seed")
            assertEquals(cx, shape.center.x, 0.12f, "centre $cx seed $seed")
            assertEquals(cx + a, released(shape).xs.max(), 0.04f, "centre $cx seed $seed")
        }
    }

    @Test
    fun aUReadsTheSameDrawnEitherWayRound() {
        assertIs<CapsuleShape>(classifyStroke(capsuleU(1.2f, 0.3f).reversed(), PIXEL, ABOVE))
        assertIs<EllipseShape>(classifyStroke(ellipseU(0f, 0.8f, 0.45f).reversed(), PIXEL, ABOVE))
    }

    @Test
    fun aULeaningAcrossThePlaneKeepsItsLean() {
        val stroke = tilted(capsuleU(1.2f, 0.3f), 0.5f)
        val shape = assertIs<CapsuleShape>(classifyStroke(stroke, PIXEL, ABOVE))
        val axis = shape.to - shape.from
        assertEquals(0.5f, axis.y / axis.x, 0.08f)
        val path = released(shape)
        assertEquals(0f, path.xs.first())
        assertEquals(0f, path.xs.last())
    }

    @Test
    fun aUOnTheOtherSideOfAPlaneIsCutThereToo() {
        val below = StrokeEnds.CutAtPlane(above = false)
        val shape = assertNotNull(classifyStroke(capsuleU(1.2f, 0.3f).map { Vec2(0f - it.x, it.y) }, PIXEL, below))
        val path = released(shape, below)
        assertEquals(-1.2f, path.xs.min(), 0.03f)
        assertTrue(path.xs.all { it <= 1e-3f })
    }

    @Test
    fun endsWithinClosingReachOfThePlaneCount() {
        val reach = 10f * PIXEL
        val path = released(assertNotNull(classifyStroke(capsuleU(1.2f, 0.3f, from = reach), PIXEL, ABOVE)))
        assertEquals(0f, path.xs.first())
        assertEquals(0f, path.xs.last())
    }

    @Test
    fun oneEndPastThePlaneAndOneOnItReadsToo() {
        assertNotNull(classifyStroke(capsuleU(1.2f, 0.3f, from = -0.2f, backTo = 0f), PIXEL, ABOVE))
    }

    @Test
    fun aUShortOfThePlaneStaysFreehand() {
        val short = 0.2f
        assertNull(classifyStroke(capsuleU(1.2f, 0.3f, from = short), PIXEL, ABOVE))
        assertNull(classifyStroke(capsuleU(1.2f, 0.3f, from = -0.1f, backTo = short), PIXEL, ABOVE), "one end short is short")
    }

    @Test
    fun aUWithNoPlaneStaysFreehand() {
        assertNull(classifyStroke(capsuleU(1.2f, 0.3f), PIXEL))
    }

    @Test
    fun aWigglyUStaysFreehand() {
        val stroke = capsuleU(1.2f, 0.3f)
        val wiggly = stroke.mapIndexed { i, p ->
            val t = i.toFloat() / stroke.lastIndex
            p + Vec2(0.09f * sin(t * 30f), 0.09f * cos(t * 30f))
        }
        assertNull(classifyStroke(wiggly, PIXEL, ABOVE))
    }

    @Test
    fun aSquareOrPointedUStaysFreehand() {
        assertNull(classifyStroke(through(-0.1f to 0.5f, 1f to 0.5f, 1f to -0.5f, -0.1f to -0.5f), PIXEL, ABOVE), "square")
        assertNull(classifyStroke(through(-0.1f to 0.55f, 1f to 0f, -0.1f to -0.55f), PIXEL, ABOVE), "pointed")
    }

    @Test
    fun aStrokeWithMoreThanOneRunPastThePlaneStaysFreehand() {
        val twice = capsuleU(0.8f, 0.2f).map { Vec2(it.x, it.y + 0.5f) } + capsuleU(0.8f, 0.2f).map { Vec2(it.x, it.y - 0.5f) }
        assertNull(classifyStroke(twice, PIXEL, ABOVE))
    }

    @Test
    fun aShallowBumpOnThePlaneStaysFreehand() {
        assertNull(classifyStroke(ellipseU(-0.8f, 1f, 1f, cut = -0.05f), PIXEL, ABOVE))
    }

    @Test
    fun aStrokeThatComesBackToItsStartReadsAsItDoesWithoutAPlane() {
        val loop = List(160) {
            val angle = 2f * PI.toFloat() * it / 159
            Vec2(0.3f + 0.8f * cos(angle), 0.5f * sin(angle))
        }
        assertEquals(classifyStroke(loop, PIXEL), classifyStroke(loop, PIXEL, ABOVE))
    }
}
