package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** A chart shape read at a point: zero on its almond's rim and its oval's, inside below zero, outside above, and its almond turned. */
class ChartShapeTest {
    private val almond = ChartAlmond(width = 0.04f, height = 0.03f, lowerLid = 0.7f, frontNarrowing = 0.2f)

    @Test
    fun theAlmondsLevelIsZeroOnItsRimAndBelowInside() {
        for (i in 1 until 32) {
            if (i == 16) continue
            val rim = almond.rim(2.0 * PI * i / 32)
            assertTrue(abs(almond.level(rim.u, rim.v)) < 1e-5f, "rim point $i lies on the level's zero")
        }
        assertTrue(almond.level(0f, 0f) < 0f)
        assertTrue(almond.level(0f, 0.05f) > 0f)
        assertTrue(almond.level(0.05f, 0f) > 0f)
    }

    @Test
    fun anOvalKeepsAFillInsideBothItAndTheTurnedOpening() {
        val oval = ChartOval(0.01f, 0f, 0.02f, 0.02f)
        val shape = ChartShape(FloatArray(0), almond, oval, turn = 0f)
        assertTrue(shape.level(0.01f, 0f) < 0f, "the oval's middle is inside")
        assertTrue(shape.level(-0.025f, 0f) > 0f, "inside the opening but outside the oval")
        val turned = ChartShape(FloatArray(0), almond, null, turn = 30f)
        val corner = almond.rim(0.0)
        val angle = PI / 6
        val u = (corner.u * cos(angle) - corner.v * sin(angle)).toFloat()
        val v = (corner.u * sin(angle) + corner.v * cos(angle)).toFloat()
        assertTrue(abs(turned.level(u, v)) < 1e-5f, "the turned opening's corner lies where the almond's corner turns to")
    }

    @Test
    fun aTurnedOvalIsReadTurnedAboutTheChartsOrigin() {
        val oval = ChartOval(0.02f, 0f, 0.008f, 0.004f)
        val upright = ChartShape(FloatArray(0), ChartAlmond(1f, 1f, 1f, 0f), oval, turn = 0f)
        val turned = ChartShape(FloatArray(0), ChartAlmond(1f, 1f, 1f, 0f), oval, turn = 0f, ovalTurn = 90f)
        assertTrue(upright.level(0.02f, 0f) < 0f && upright.level(0f, 0.02f) > 0f)
        assertTrue(turned.level(0f, 0.02f) < 0f, "the oval's middle turns a quarter round the origin")
        assertTrue(turned.level(0f, 0.027f) < 0f && turned.level(0.005f, 0.02f) > 0f, "its long half turns with it")
    }
}
