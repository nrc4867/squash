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

    /** A lid about [shaped] with its shoulders found as an eye's are. */
    private fun lidOf(weight: Float, flick: Float = 0f, flickLift: Float = 0f, lower: Float = 0f, lowerExtent: Float = 1f, clumps: Int = 0, clumpLength: Float = 0f) =
        ChartLid(
            weight, flick = flick, flickLift = flickLift, lower = lower, lowerExtent = lowerExtent, clumps = clumps, clumpLength = clumpLength,
            shoulder = ChartLid.shoulderOf(shaped, LID_ASPECT), lowerShoulder = ChartLid.shoulderOf(shaped, LID_ASPECT, lowerLid = true),
        )

    @Test
    fun theMassLiesOverTheUpperLidAndNotInsideTheOpening() {
        val lid = lidOf(weight = 0.3f, flick = 0.3f, flickLift = 0.2f)
        val h = shaped.height
        assertTrue(lid.level(shaped, 0f, h * (shaped.upper(0f) + 0.12f)) < 0f, "just over the top lid's middle is ink")
        assertTrue(lid.level(shaped, 0f, 0f) > 0f, "the opening's middle is clear")
        assertTrue(lid.level(shaped, 0f, -h * 0.75f) > 0f, "the lower lid is open with no lower weight")
        val shoulder = lid.shoulder
        val rootY = shaped.topEdge(shoulder) + (lid.thickness(shaped, shoulder) - LID_INSET) / 2f
        assertTrue(lid.level(shaped, -shaped.width * 1.1f, h * (rootY + 0.03f)) < 0f, "the flick runs on from the shoulder past the back corner")
        assertTrue(lid.level(shaped, -shaped.width * 1.5f, 0f) > 0f, "nothing lies past the flick's tip")
    }

    @Test
    fun theMassThinsTowardTheFrontCorner() {
        val lid = lidOf(weight = 0.3f)
        assertTrue(lid.thickness(shaped, -0.5f) > lid.thickness(shaped, 0.6f), "heavier toward the back than the front")
        assertEquals(0f, lid.thickness(shaped, 1f), "it comes to a point at the front corner")
        assertTrue(lid.thickness(shaped, -1f) < lid.thickness(shaped, -0.5f), "and thins where it drops to the back corner")
    }

    @Test
    fun onATallOpeningTheMassEndsAtTheShoulderAndLeavesTheSideBare() {
        val tall = ChartAlmond(width = 0.04f, height = 0.11f, lowerLid = 0.9f, frontNarrowing = 0f, topFlat = 0.35f, outerCorner = 0.8f)
        val aspect = 0.6f
        val shoulder = ChartLid.shoulderOf(tall, aspect)
        assertTrue(shoulder > -0.9f, "a tall opening's upper lid turns down well in from its back corner, not at $shoulder")
        val lid = ChartLid(0.25f, aspect = aspect, shoulder = shoulder)
        val top = lid.thickness(tall, 0f)
        assertTrue(lid.thickness(tall, -0.97f) < 0.05f * top, "down the steep side it thins away")
        assertTrue(lid.thickness(tall, -0.3f) > 0.7f * top, "and it stays even over the top")
    }

    @Test
    fun aLowerBandDoesNotTrailDownTheSide() {
        val tall = ChartAlmond(width = 0.04f, height = 0.11f, lowerLid = 0.9f, frontNarrowing = 0f, outerCorner = 0.8f)
        val aspect = 0.6f
        val lid = ChartLid(0.25f, lower = 0.6f, lowerExtent = 0.4f, aspect = aspect, lowerShoulder = ChartLid.shoulderOf(tall, aspect, lowerLid = true))
        val h = tall.height
        val side = -0.95f
        assertTrue(lid.level(tall, side * tall.width, tall.openBottom(side) * h - 0.03f * h) > 0f, "no band low on the steep side")
    }

    @Test
    fun aLowerWeightDrawsTheLowerLidFromTheBackCornerOverItsExtent() {
        val lid = lidOf(weight = 0.3f, lower = 0.6f, lowerExtent = 0.4f)
        val h = shaped.height
        val nearBack = -0.75f
        val bottom = -shaped.lowerLid * shaped.lower(nearBack) * h
        assertTrue(lid.level(shaped, nearBack * shaped.width, bottom - h * 0.04f) < 0f, "the lower lid is inked near the back corner")
        val front = 0.5f
        val frontBottom = -shaped.lowerLid * shaped.lower(front) * h
        assertTrue(lid.level(shaped, front * shaped.width, frontBottom - h * 0.04f) > 0f, "and open past its extent")
    }

    @Test
    fun lashesLeaveTheLidTowardTheBackCornerAndCurveUpToAPoint() {
        val bare = lidOf(weight = 0.2f)
        val lashed = bare.copy(clumps = 3, clumpLength = 0.6f)
        val k = 0.5f / lashed.clumps
        val root = lashed.shoulder + LASH_START + 2f * lashed.clumpSpread * k
        val rootY = shaped.topEdge(root) + 0.5f * lashed.thickness(shaped, root)
        val angle = (LASH_ANGLE_OUTER + (LASH_ANGLE_INNER - LASH_ANGLE_OUTER) * k) * PI.toFloat() / 180f
        val length = 0.6f * LASH_REACH
        val reachX = -length * kotlin.math.cos(angle) / LID_ASPECT
        val reachY = length * kotlin.math.sin(angle)
        val t = 0.6f
        val u = (root + t * reachX) * shaped.width
        val v = (rootY + (t + (t * t - t) * LASH_BEND) * reachY) * shaped.height
        assertTrue(bare.level(shaped, u, v) > 0f, "the bare lid does not reach there")
        assertTrue(lashed.level(shaped, u, v) < 0f, "the outermost lash runs there, out toward the back corner and up off the lid")
        val tip = (root + reachX) * shaped.width to (rootY + reachY) * shaped.height
        assertTrue(lashed.level(shaped, tip.first, tip.second + 0.03f * shaped.height) > 0f, "it ends in a point")
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
