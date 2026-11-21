package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An almond's flat top and sharp back corner, and a lid's mass about it: heavy over the top, light under, past the back corner, wedges. */
class ChartLidTest {
    private val plain = ChartAlmond(width = 0.04f, height = 0.03f, lowerLid = 0.8f, frontNarrowing = 0.15f)
    private val shaped = plain.copy(topFlat = 0.6f, outerCorner = 0.8f)

    @Test
    fun anAlmondWithNoFlatnessOrCornerReadsAsTheEllipseItWas() {
        for (i in 1 until 32) {
            val a = -1f + 2f * i / 32
            val open = kotlin.math.sqrt(1f - a * a) * (1f + plain.frontNarrowing * a)
            assertTrue(abs(plain.upper(a) - open) < 1e-5f, "the top at $a is the ellipse's")
            assertTrue(abs(plain.lower(a) - open) < 1e-5f, "the bottom at $a is the ellipse's")
        }
    }

    @Test
    fun aFlatTopRunsHigherNearItsCornersAndItsRimStaysOnItsLevel() {
        assertTrue(shaped.upper(0.6f) > plain.upper(0.6f), "a flat top stays up further toward the front corner")
        for (i in 1 until 32) {
            if (i == 16) continue
            val rim = shaped.rim(2.0 * PI * i / 32)
            assertTrue(abs(shaped.level(rim.u, rim.v)) < 1e-5f, "rim point $i lies on the level's zero")
        }
    }

    @Test
    fun theMassLiesOverTheUpperLidAndNotInsideTheOpening() {
        val lid = ChartLid(weight = 0.3f, flick = 0.3f, flickLift = 0.2f)
        val h = shaped.height
        assertTrue(lid.level(shaped, 0f, h * (shaped.upper(0f) + 0.12f)) < 0f, "just over the top lid's middle is ink")
        assertTrue(lid.level(shaped, 0f, 0f) > 0f, "the opening's middle is clear")
        assertTrue(lid.level(shaped, 0f, -h * 0.75f) > 0f, "the lower lid is open with no lower weight")
        assertTrue(lid.level(shaped, -shaped.width * 1.1f, h * 0.05f) < 0f, "the flick runs on past the back corner")
        assertTrue(lid.level(shaped, -shaped.width * 1.5f, 0f) > 0f, "nothing lies past the flick's tip")
    }

    @Test
    fun theMassThinsTowardTheFrontCorner() {
        val lid = ChartLid(weight = 0.3f)
        assertTrue(lid.thickness(shaped, -0.5f) > lid.thickness(shaped, 0.6f), "heavier toward the back than the front")
        assertEquals(0f, lid.thickness(shaped, 1f), "it comes to a point at the front corner")
        assertTrue(lid.thickness(shaped, -1f) < lid.thickness(shaped, -0.5f), "and thins where it drops to the back corner")
    }

    @Test
    fun aLowerWeightDrawsTheLowerLidFromTheBackCornerOverItsExtent() {
        val lid = ChartLid(weight = 0.3f, lower = 0.6f, lowerExtent = 0.4f)
        val h = shaped.height
        val nearBack = -0.75f
        val bottom = -shaped.lowerLid * shaped.lower(nearBack) * h
        assertTrue(lid.level(shaped, nearBack * shaped.width, bottom - h * 0.04f) < 0f, "the lower lid is inked near the back corner")
        val front = 0.5f
        val frontBottom = -shaped.lowerLid * shaped.lower(front) * h
        assertTrue(lid.level(shaped, front * shaped.width, frontBottom - h * 0.04f) > 0f, "and open past its extent")
    }

    @Test
    fun clumpsGrowOutOfTheLidNearTheBackCorner() {
        val bare = ChartLid(weight = 0.2f)
        val clumped = bare.copy(clumps = 3, clumpLength = 0.6f)
        val root = -1f + CLUMP_START + 2f * clumped.clumpSpread / (2f * clumped.clumps)
        val u = -0.727f * shaped.width
        val v = (shaped.upper(root) + 0.5f * clumped.thickness(shaped, root) + 0.2f) * shaped.height
        assertTrue(bare.level(shaped, u, v) > 0f, "the bare lid does not reach there")
        assertTrue(clumped.level(shaped, u, v) < 0f, "the outermost wedge stands there, above the lid near the back corner")
    }

    @Test
    fun aCutKeepsAFillInsideOrOutsideItsShape() {
        val iris = ChartOval(0f, 0f, 0.01f, 0.02f)
        val upper = ChartShape(FloatArray(0), plain, iris, 0f, cut = ChartCut(iris.copy(v = 0.02f)))
        assertTrue(upper.level(0f, 0.015f) < 0f, "the iris's top is kept by a cut moved up")
        assertTrue(upper.level(0f, -0.015f) > 0f, "its bottom is not")
        val crescent = ChartShape(FloatArray(0), plain, iris, 0f, cut = ChartCut(iris.copy(v = 0.01f), outside = true))
        assertTrue(crescent.level(0f, -0.018f) < 0f && crescent.level(0f, 0.01f) > 0f, "outside a cut keeps the bottom crescent")
    }
}
