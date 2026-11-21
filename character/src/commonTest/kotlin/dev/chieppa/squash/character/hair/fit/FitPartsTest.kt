package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.geom.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pieces a hair fit is made of: an outline's tips and valleys, the cheapest pairing, the compass search, and the trace of an image. */
class FitPartsTest {
    /** A star of [spikes] points about the origin, counter-clockwise, its points out at 1 and its notches in at 0.5. */
    private fun star(spikes: Int): List<Vec2> = List(spikes * 2) { i ->
        val a = PI * i / spikes
        val r = if (i % 2 == 0) 1.0 else 0.5
        Vec2((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
    }

    @Test
    fun aStarsPointsAreItsTipsAndItsNotchesItsValleys() {
        val points = star(5)
        val (tips, valleys) = FlatOutline.features(points)
        assertEquals(listOf(0, 2, 4, 6, 8), tips)
        assertEquals(listOf(1, 3, 5, 7, 9), valleys)
        assertEquals(FlatOutline.features(points.reversed()).first.size, 5, "the way round does not matter")
    }

    @Test
    fun aCircleHasNoTipsOrValleys() {
        val circle = List(48) { i -> Vec2(cos(2 * PI * i / 48).toFloat(), sin(2 * PI * i / 48).toFloat()) }
        val (tips, valleys) = FlatOutline.features(circle)
        assertTrue(tips.isEmpty() && valleys.isEmpty())
        val flat = FlatOutline(circle, tips, valleys)
        assertTrue(flat.contains(Vec2(0.2f, 0.1f)))
        assertTrue(!flat.contains(Vec2(1.2f, 0f)))
        assertEquals(2f * PI.toFloat(), flat.perimeter, 0.01f)
    }

    @Test
    fun theCheapestPairingTakesTheLeastTotal() {
        val cost = arrayOf(doubleArrayOf(4.0, 1.0, 3.0), doubleArrayOf(2.0, 0.0, 5.0), doubleArrayOf(3.0, 2.0, 2.0))
        assertContentEquals(intArrayOf(1, 0, 2), cheapestPairing(cost))
        val wide = arrayOf(doubleArrayOf(9.0, 1.0, 9.0, 9.0), doubleArrayOf(1.0, 9.0, 9.0, 9.0))
        assertContentEquals(intArrayOf(1, 0), cheapestPairing(wide))
        val tall = arrayOf(doubleArrayOf(5.0), doubleArrayOf(1.0), doubleArrayOf(3.0))
        assertContentEquals(intArrayOf(-1, 0, -1), cheapestPairing(tall), "rows left over pair with nothing")
    }

    @Test
    fun aCompassSearchFindsTheBowlsFloorWithinItsBounds() {
        val bowl = { x: FloatArray -> (x[0] - 0.3f) * (x[0] - 0.3f) + 2f * (x[1] + 0.6f) * (x[1] + 0.6f) }
        val (found, cost) = patternSearch(floatArrayOf(1f, 1f), floatArrayOf(0.5f, 0.5f), floatArrayOf(-2f, -2f), floatArrayOf(2f, 2f), 400, bowl)
        assertEquals(0.3f, found[0], 0.02f)
        assertEquals(-0.6f, found[1], 0.02f)
        assertTrue(cost < 1e-3f)
        val (bounded, _) = patternSearch(floatArrayOf(1f, 1f), floatArrayOf(0.5f, 0.5f), floatArrayOf(-2f, 0f), floatArrayOf(2f, 2f), 400, bowl)
        assertEquals(0f, bounded[1], "a value stays within its bounds")
        assertContentEquals(found, patternSearch(floatArrayOf(1f, 1f), floatArrayOf(0.5f, 0.5f), floatArrayOf(-2f, -2f), floatArrayOf(2f, 2f), 400, bowl).first)
    }

    @Test
    fun aTraceOutlinesTheBiggestPieceInTheKeyedColour() {
        val hair = 0x6b4226
        val paper = 0xf4f0e8
        val image = TraceImage(80, 60) { x, y -> if (hypot(x - 40f, y - 30f) < 20f || (x in 70..74 && y in 5..9)) hair else paper }
        val points = assertNotNull(traceHairOutline(image, listOf(hair), 20f) { x, y -> Vec2(x, -y) })
        assertTrue(points.size >= 8)
        assertTrue(points.all { abs(hypot(it.x - 40f, -it.y - 30f) - 20f) < 2.5f }, "the outline follows the disc's edge, not the speck's")
        assertNull(traceHairOutline(image, listOf(0x00ff00), 20f) { x, y -> Vec2(x, y) }, "no colour like the key, no outline")
    }
}
