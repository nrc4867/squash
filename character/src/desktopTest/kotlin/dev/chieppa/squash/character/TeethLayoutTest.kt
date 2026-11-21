package dev.chieppa.squash.character

import dev.chieppa.squash.character.mouth.MouthDentition
import dev.chieppa.squash.character.mouth.MouthDrawing
import dev.chieppa.squash.character.mouth.MouthShaper
import dev.chieppa.squash.character.mouth.TeethSeat
import dev.chieppa.squash.character.mouth.layTeeth
import dev.chieppa.squash.document.Jaw
import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.SlotSide
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.ToothRow
import dev.chieppa.squash.document.ToothSlot
import dev.chieppa.squash.document.ToothTip
import dev.chieppa.squash.document.ToothType
import dev.chieppa.squash.geom.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DEPTH = 0.45f
private const val SLACK = 1e-4f

/** Teeth laid from a dentition on a seat, and shaped with the lips: levels, sides, over slots and what a shut mouth shows. */
class TeethLayoutTest {
    private val types = mapOf("front" to ToothType(width = 0.1f, length = 0.1f), "fang" to ToothType(width = 0.06f, length = 0.2f, tip = ToothTip.POINT))
    private val row = ToothRow(listOf(ToothSlot("front"), ToothSlot("front"), ToothSlot("fang", over = true)))

    private fun dentition(level: TeethLevel, lower: Jaw = Jaw()) = MouthDentition(types, Jaw(level, listOf(row)), lower)

    /** A level seat two units wide, its jaws on the line y = 0. */
    private class FlatSeat(private val scale: Float = 1f) : TeethSeat {
        override val middle = 0f
        override val width = 2f
        override fun reach(sign: Int) = 1f
        override fun upperLine(x: Float) = 0f
        override fun lowerLine(x: Float) = 0f
        override fun heightScale(x: Float) = scale
    }

    @Test
    fun blocksLayEachSlotOnBothSidesAndNoneLaysNothing() {
        assertEquals(6, layTeeth(dentition(TeethLevel.BLOCKS), FlatSeat()).size)
        assertTrue(layTeeth(dentition(TeethLevel.NONE), FlatSeat()).isEmpty())
    }

    @Test
    fun aBarIsOneBandAndItsOverSlotsStayTeethOfTheirOwn() {
        val bar = layTeeth(dentition(TeethLevel.BAR), FlatSeat())
        assertEquals(3, bar.size)
        assertEquals(1, bar.count { !it.over })
        assertEquals(2, bar.count { it.over })
        val band = bar.single { !it.over }.loop
        assertEquals(-0.4f, band.minOf { it.x }, SLACK)
        assertEquals(0.4f, band.maxOf { it.x }, SLACK)
    }

    @Test
    fun slotsFollowEachOtherFromTheMiddleOutAndASidedSlotFillsOneSide() {
        val middles = dentition(TeethLevel.BLOCKS).middles(row.slots)
        assertEquals(listOf(0.05f, 0.15f, 0.23f), middles.map { (it * 100).toInt() / 100f })
        val sided = MouthDentition(types, Jaw(TeethLevel.BLOCKS, listOf(ToothRow(listOf(ToothSlot("front", side = SlotSide.LEFT))))), Jaw())
        assertEquals(1, layTeeth(sided, FlatSeat()).size)
    }

    @Test
    fun anUpperToothHangsDownTheLowerStandsUpAndTheSeatsHeightScaleStretchesThem() {
        val lower = Jaw(TeethLevel.BLOCKS, listOf(ToothRow(listOf(ToothSlot("front")))))
        val teeth = layTeeth(MouthDentition(types, Jaw(TeethLevel.BLOCKS, listOf(ToothRow(listOf(ToothSlot("front"))))), lower), FlatSeat())
        assertEquals(0.2f, teeth.filter { it.jaw == JawName.UPPER }.maxOf { tooth -> tooth.loop.maxOf { it.y } }, SLACK)
        assertEquals(-0.2f, teeth.filter { it.jaw == JawName.LOWER }.minOf { tooth -> tooth.loop.minOf { it.y } }, SLACK)
        val stretched = layTeeth(MouthDentition(types, Jaw(TeethLevel.BLOCKS, listOf(ToothRow(listOf(ToothSlot("front"))))), Jaw()), FlatSeat(scale = 2f))
        assertEquals(0.4f, stretched.maxOf { tooth -> tooth.loop.maxOf { it.y } }, SLACK)
    }

    @Test
    fun aShutMouthShowsOnlyOverTeethAndTheyBreakTheLipInk() {
        val lip = List(41) { Vec2(-1f + it / 20f, 0f) }
        val shut = MouthShaper.shape(MouthDrawing(lip, dentition = dentition(TeethLevel.BLOCKS)), MarkControls(), DEPTH)
        assertEquals(2, shut.teeth.size)
        assertTrue(shut.teeth.all { it.over })
        assertTrue(shut.upper.size > 1, "the lip's ink breaks under each fang")
        val open = MouthShaper.shape(MouthDrawing(lip, dentition = dentition(TeethLevel.BAR)), MarkControls(open = 1f), DEPTH)
        assertTrue(open.teeth.any { !it.over }, "the bar shows inside the open mouth")
        assertTrue(open.teeth.filterNot { it.over }.all { piece -> piece.loop.all { it.y >= -SLACK } }, "kept below the upper lip")
    }

    @Test
    fun aMouthWithNoTeethRestsAsDrawn() {
        val lip = listOf(Vec2(-1f, 0f), Vec2(1f, 0f))
        val bar = MouthDentition(types, Jaw(TeethLevel.BAR, listOf(ToothRow(listOf(ToothSlot("front"))))), Jaw())
        assertTrue(MouthShaper.shape(MouthDrawing(lip, dentition = bar), MarkControls(), DEPTH).restsAsDrawn)
    }
}
