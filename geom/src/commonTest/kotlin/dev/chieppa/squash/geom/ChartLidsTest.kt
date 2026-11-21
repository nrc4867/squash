package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Lids closing an almond toward their closed line, the lid's mass riding them, and the lid's own paint moving with the upper lid. */
class ChartLidsTest {
    private val open = ChartAlmond(width = 0.04f, height = 0.03f, lowerLid = 0.8f, frontNarrowing = 0.15f, topFlat = 0.4f, outerCorner = 0.6f)
    private fun standing(close: Float, rise: Float = 0f, bend: Float = 0f) = open.copy(lids = ChartLids(close, rise, closedBend = bend))
    private val h = open.height

    @Test
    fun openLidsLeaveTheAlmondAsItIs() {
        val lidded = open.copy(lids = ChartLids(closedAt = 0.3f, closedBend = 0.5f))
        for (i in 0..20) for (j in 0..10) {
            val u = open.width * (-1.2f + 2.4f * i / 20)
            val v = h * (-1.2f + 2.4f * j / 10)
            assertEquals(open.level(u, v), lidded.level(u, v), "at $u, $v")
        }
        assertEquals(open.openTop(0.3f), lidded.topEdge(0.3f))
    }

    @Test
    fun aHalfClosedUpperLidHidesTheTopOfTheOpeningAndNotItsBottom() {
        val half = standing(0.5f)
        assertTrue(open.level(0f, h * 0.8f) < 0f && half.level(0f, h * 0.8f) > 0f, "the top of the opening is under the lid")
        assertTrue(half.level(0f, -h * 0.5f) < 0f, "its bottom still shows")
        assertTrue(half.topEdge(0f) < open.openTop(0f) && half.topEdge(0f) > half.closedLine(0f), "the edge stands between open and the closed line")
    }

    @Test
    fun shutLidsLeaveNothingOfTheOpeningAndMeetOnTheClosedLine() {
        val shut = standing(1f, 1f)
        for (i in 0..20) for (j in 0..20) {
            val u = open.width * (-1f + 2f * i / 20)
            val v = h * (-1f + 2f * j / 20)
            assertTrue(shut.level(u, v) > -1e-6f, "nothing shows at $u, $v")
        }
        for (a in listOf(-0.6f, 0f, 0.6f)) {
            assertEquals(shut.topEdge(a), shut.bottomEdge(a), 1e-6f, "the lids meet at $a")
            assertTrue(shut.closedLine(a) <= open.openTop(a) && shut.closedLine(a) >= open.openBottom(a), "the closed line lies in the opening at $a")
        }
    }

    @Test
    fun aBentClosedLineArchesUpInItsMiddle() {
        val happy = standing(1f, 1f, bend = 0.5f)
        assertTrue(happy.closedLine(0f) - happy.closedLine(-0.6f) > 0.15f, "a happy closed eye's line stands higher in its middle")
        val sad = standing(1f, 1f, bend = -0.5f)
        assertTrue(sad.closedLine(0f) < sad.closedLine(-0.6f), "a sagging one lower")
    }

    @Test
    fun theMassRidesTheMovingEdge() {
        val lid = ChartLid(weight = 0.25f, shoulder = ChartLid.shoulderOf(open, LID_ASPECT))
        val half = standing(0.6f)
        val overEdge = h * (half.topEdge(0f) + 0.1f)
        assertTrue(lid.level(half, 0f, overEdge) < 0f, "the band lies just over the lowered edge")
        assertTrue(lid.level(open, 0f, overEdge) > 0f, "and not there with the lid open")
        assertTrue(lid.level(half, 0f, h * (open.openTop(0f) + 0.1f)) > 0f, "nor over the open lid's place")
    }

    @Test
    fun theLidsColourCoversWhatTheLidCoversAndFadesUpFromTheLashLine() {
        val skin = ChartLidPaint(fade = 1f)
        val half = standing(0.5f)
        val under = h * (half.topEdge(0f) + open.openTop(0f)) / 2f
        assertTrue(skin.level(half, 0f, under) < 0f, "the lid's colour shows where the lid has come down")
        assertTrue(skin.level(open, 0f, under) > 0f, "and with the lid open, not above it")
        assertTrue(skin.level(half, 0f, h * (half.topEdge(0f) - 0.1f)) > 0f, "nor over the open part of the eye")
        assertTrue(skin.share(half, 0f, h * (half.topEdge(0f) + 0.05f)) > skin.share(half, 0f, h * (half.topEdge(0f) + 0.4f)), "it fades going up")
        val above = ChartLidPaint(above = 0.5f)
        assertTrue(above.level(open, 0f, h * (open.openTop(0f) + 0.3f)) < 0f, "with above it shows over the open lid too")
    }

    @Test
    fun theCoveredSkinIsWhatTheLidsHaveClosedOverAndNothingOpen() {
        val cover = ChartLidPaint(swept = true)
        for (i in 0..20) for (j in 0..10) {
            val u = open.width * (-1.2f + 2.4f * i / 20)
            val v = h * (-1.2f + 2.4f * j / 10)
            assertTrue(cover.level(open, u, v) >= 0f, "nothing is covered with the lids open, at $u, $v")
        }
        val shut = standing(1f, 1f)
        assertTrue(cover.level(shut, 0f, h * 0.5f) < 0f && cover.level(shut, 0f, -h * 0.5f) < 0f, "shut, both lids cover the opening")
        val half = standing(0.5f, 0.5f)
        assertTrue(cover.level(half, 0f, h * (half.closedLine(0f) + half.topEdge(0f)) / 2f) > 0f, "half shut, the part still open is not")
        assertTrue(cover.level(half, 0f, h * (half.topEdge(0f) + open.openTop(0f)) / 2f) < 0f, "the part the upper lid came over is")
        assertTrue(cover.level(shut, 0f, h * (open.openTop(0f) + 0.1f)) > 0f, "nor anything past the open opening")
    }

    @Test
    fun aClosedLineStraightAcrossTheMiddleStaysStraightUntilTheCorners() {
        val shut = standing(1f, 1f)
        assertEquals(ChartLids.DEFAULT_CLOSED_AT, shut.closedLine(0f), 1e-6f)
        assertEquals(ChartLids.DEFAULT_CLOSED_AT, shut.closedLine(-0.5f), 1e-6f)
        assertTrue(abs(shut.closedLine(-0.999f)) < abs(ChartLids.DEFAULT_CLOSED_AT) * 0.5f, "at the corner it is drawn in to where the lids meet")
    }

    @Test
    fun aMarkOnTheLidMovesDownWithIt() {
        val wing = ChartLidPaint(mark = ChartLidMark(along = -0.2f, above = 0.3f, reachX = 0f, reachY = 0f, halfWidth = 0.1f, bend = 0f))
        val u = -0.2f * open.width
        val openPlace = h * (open.openTop(-0.2f) + 0.3f)
        val half = standing(0.5f)
        val halfPlace = h * (half.topEdge(-0.2f) + 0.3f)
        assertTrue(wing.level(open, u, openPlace) < 0f && wing.level(half, u, openPlace) > 0f, "it leaves its open place")
        assertTrue(wing.level(half, u, halfPlace) < 0f, "and stands as far over the lowered edge")
    }
}
