package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClothReachTest {
    private fun reach(rows: IntRange, columns: Set<Int> = (0 until CHART_COLUMNS).toSet()): ClothReach =
        assertNotNull(ClothReach.of(BooleanArray(CHART_CELLS) { it / CHART_COLUMNS in rows && it % CHART_COLUMNS in columns }))

    @Test
    fun theGrownRangeIsThePaintedRowsAndAMarginRoundedOutToSteps() {
        val band = reach(36..44)
        assertEquals(32f / CHART_ROWS, band.from)
        assertEquals(1f, band.to)
        assertTrue(band.partial)
        assertEquals(10f / CHART_ROWS, band.along)
        val low = reach(10..20)
        assertEquals(0f, low.from)
        assertEquals(32f / CHART_ROWS, low.to)
    }

    @Test
    fun aSmallChangeToThePaintedRowsKeepsTheRange() {
        val one = reach(36..44)
        val other = reach(35..48)
        assertEquals(one.from, other.from)
        assertEquals(one.to, other.to)
    }

    @Test
    fun paintNearAnEndGrowsToThatEnd() {
        val low = reach(1..10)
        assertEquals(0f, low.from)
        val high = reach(50..62)
        assertEquals(1f, high.to)
        val whole = reach(0 until CHART_ROWS)
        assertEquals(0f, whole.from)
        assertEquals(1f, whole.to)
        assertFalse(whole.partial)
    }

    @Test
    fun theColumnsRoundAreTheShortestSpanHoldingThemAcrossVZero() {
        val across = reach(10..12, setOf(30, 31, 0, 1, 2))
        assertEquals(5, across.columns)
        assertFalse(across.allRound)
        assertEquals(6f / CHART_COLUMNS, across.around)
        assertEquals(-2f / CHART_COLUMNS, across.fromV, "the span round starts at its first column's edge, below 0 across v 0")
        assertEquals(3f / CHART_COLUMNS, across.toV, "and ends at its last column's edge")
        assertTrue(reach(10..12).allRound)
        assertEquals(0f, reach(10..12).fromV)
        assertEquals(1f, reach(10..12).toV)
    }

    @Test
    fun noCellsHaveNoReach() {
        assertNull(ClothReach.of(BooleanArray(CHART_CELLS)))
    }
}
