package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A part's length and girth in body units, so a cell is a little over a hundredth of a unit along it. */
private const val ALONG = 1.5f
private const val AROUND = 1.2f

/** One row of cells, in u, and one column, in v. */
private const val ROW = 1f / CHART_ROWS
private const val COLUMN = 1f / CHART_COLUMNS

/** The painted cloth as a smooth field over a part's chart: its hem, its seams, and how fine a line it keeps. */
class ClothFieldTest {
    private fun cells(held: (row: Int, column: Int) -> Boolean) = BooleanArray(CHART_CELLS) { held(it / CHART_COLUMNS, it % CHART_COLUMNS) }

    private fun field(vararg areas: BooleanArray): ClothField = assertNotNull(ClothField.of(areas.toList()))

    /** The middle of column [c], in v. */
    private fun columnV(c: Int) = (c + 0.5f) * COLUMN

    /** Where, in rows, the field crosses its hem level going down column [c] from [from] to [to] rows, the field falling there. */
    private fun hemRow(cloth: ClothField, c: Int, from: Float, to: Float): Float {
        var low = from * ROW
        var high = to * ROW
        repeat(40) {
            val middle = (low + high) / 2f
            if (cloth.at(middle, columnV(c)) >= CLOTH_HEM_LEVEL) low = middle else high = middle
        }
        return (low + high) / 2f * CHART_ROWS
    }

    @Test
    fun aLevelHemLiesOnTheHalfLevelAtTheEdgeOfThePaintedRowsReadAsADistance() {
        val cloth = field(cells { row, _ -> row < 32 })
        for (c in 0 until CHART_COLUMNS) {
            assertEquals(CLOTH_HEM_LEVEL, cloth.at(0.5f, columnV(c)), 1e-5f, "the hem is on the half level between rows 31 and 32")
            assertEquals(0f, cloth.inside(0.5f, columnV(c), ALONG, AROUND), 1e-5f)
        }
        val off = ROW / 4f
        assertEquals(-off * ALONG, cloth.inside(0.5f + off, 0.3f, ALONG, AROUND), off * ALONG * 0.25f, "a quarter row past the hem is outside by its length in body units")
        assertEquals(off * ALONG, cloth.inside(0.5f - off, 0.3f, ALONG, AROUND), off * ALONG * 0.25f)
        assertTrue(cloth.inside(0.1f, 0.3f, ALONG, AROUND) > ROW * ALONG * 10f, "far inside reads far inside")
        assertTrue(cloth.inside(0.9f, 0.3f, ALONG, AROUND) < -ROW * ALONG * 10f, "far outside reads far outside")
        assertTrue(cloth.at(0f, 0.3f) > 0.99f, "rows held at the part's end keep it covered")
        for (rows in listOf(1f, 2f, 5f, 12f)) {
            assertEquals(rows * ROW * ALONG, cloth.inside(0.5f - rows * ROW, 0.3f, ALONG, AROUND), ROW * ALONG * 0.15f, "$rows rows inside is that far in body units")
            assertEquals(-rows * ROW * ALONG, cloth.inside(0.5f + rows * ROW, 0.3f, ALONG, AROUND), ROW * ALONG * 0.15f, "and outside")
        }
    }

    @Test
    fun aSlantedHemIsAStraightLineThroughTheStepsOfItsCells() {
        val cloth = field(cells { row, column -> row < 20 + column / 2 })
        val columns = 4..27
        val rows = columns.map { hemRow(cloth, it, 10f, 40f) }
        val meanC = columns.average().toFloat()
        val meanR = rows.average().toFloat()
        val slope = columns.zip(rows).sumOf { (c, r) -> ((c - meanC) * (r - meanR)).toDouble() }.toFloat() /
            columns.sumOf { ((it - meanC) * (it - meanC)).toDouble() }.toFloat()
        assertEquals(0.5f, slope, 0.03f, "the hem climbs half a row a column, as the cells do")
        columns.zip(rows).forEach { (c, r) ->
            val line = meanR + slope * (c - meanC)
            assertTrue(abs(r - line) < 0.1f, "column $c's hem at row $r lies off the line by more than a tenth of a row, where the cells step a quarter either side")
        }
        rows.zipWithNext().forEach { (a, b) -> assertTrue(b - a > 0.25f, "the hem rises every column, with no flat steps: $rows") }
    }

    @Test
    fun aPatchIsCoveredInsideAndHemmedRoundItsEdgeAndWrapsRoundThePart() {
        val cloth = field(cells { row, column -> row in 20..29 && column in 8..15 })
        val middleU = 25f * ROW
        assertTrue(cloth.inside(middleU, 12f * COLUMN, ALONG, AROUND) > 0f)
        assertEquals(CLOTH_HEM_LEVEL, cloth.at(middleU, 8f * COLUMN), 0.02f, "its edge round the part")
        assertEquals(CLOTH_HEM_LEVEL, cloth.at(20f * ROW, 12f * COLUMN), 0.02f, "and along it")
        assertTrue(cloth.inside(middleU, 0.75f, ALONG, AROUND) < 0f)
        assertTrue(cloth.inside(40f * ROW, 12f * COLUMN, ALONG, AROUND) < 0f)

        val across = field(cells { row, column -> row in 20..29 && (column >= 28 || column <= 3) })
        assertTrue(across.at(middleU, 0f) > 0.95f, "a patch across v = 0 is whole there")
        assertEquals(CLOTH_HEM_LEVEL, across.at(middleU, 4f * COLUMN), 0.02f)
        assertEquals(CLOTH_HEM_LEVEL, across.at(middleU, 28f * COLUMN), 0.02f)
    }

    @Test
    fun twoAreasMeetAlongASeamWithNoGapAndEachPlaceInOneArea() {
        val upper = cells { row, _ -> row < 32 }
        val lower = cells { row, _ -> row in 32..47 }
        val cloth = field(upper, lower)
        assertEquals(2, cloth.areaCount)
        for (c in 0 until CHART_COLUMNS) {
            val v = columnV(c)
            assertTrue(cloth.at(0.5f, v) > 0.99f, "no gap where the areas meet")
            assertEquals(0f, cloth.areaAt(0, 0.5f, v) - cloth.areaAt(1, 0.5f, v), 1e-5f, "the seam lies where their cells meet")
            var u = 0.02f
            while (u < 47f * ROW) {
                val seam = cloth.areaAt(0, u, v) - cloth.areaAt(1, u, v)
                if (u < 0.5f - ROW / 4f) assertTrue(seam > 0f, "u $u lies in the upper area alone")
                if (u > 0.5f + ROW / 4f) assertTrue(seam < 0f, "u $u lies in the lower area alone")
                assertTrue(cloth.at(u, v) >= CLOTH_HEM_LEVEL, "u $u is covered")
                u += ROW / 8f
            }
        }
        assertEquals(CLOTH_HEM_LEVEL, cloth.at(48f * ROW, 0.3f), 1e-5f, "the lower area's hem")
        val apart = assertNotNull(ClothField.union(listOf(field(upper), field(lower))))
        assertTrue(apart.at(0.5f, 0.3f) < cloth.at(0.5f, 0.3f) - 0.2f, "areas blurred apart and joined by their most would thin the cloth along the seam")
    }

    @Test
    fun aLineOneCellWideDropsOutAndOneTwoCellsWideStays() {
        val one = field(cells { row, _ -> row == 32 })
        val middle = 32.5f * ROW
        assertTrue(one.at(middle, 0.3f) < CLOTH_HEM_LEVEL, "blurred by ${CLOTH_BLUR_CELLS} cells, a one-cell line reads under the hem level")
        assertTrue(one.inside(middle, 0.3f, ALONG, AROUND) < 0f, "so it is no cloth")
        val two = field(cells { row, _ -> row in 32..33 })
        assertTrue(two.inside(33f * ROW, 0.3f, ALONG, AROUND) > 0f)
    }

    @Test
    fun fieldsAreEqualForTheSameCellsInTheSameAreasAndNoneWithNoCells() {
        val a = cells { row, _ -> row < 10 }
        val b = cells { row, _ -> row < 11 }
        assertEquals(field(a), field(a.copyOf()))
        assertEquals(field(a).hashCode(), field(a.copyOf()).hashCode())
        assertNotEquals(field(a), field(b))
        assertNotEquals(field(a, b), field(b, a), "the same cells in other areas meet along other seams")
        assertNull(ClothField.of(listOf(BooleanArray(CHART_CELLS))))
        assertEquals(0, field(a).areaCount, "one area has no seams")
    }

    @Test
    fun aUnionIsTheMostOfItsFields() {
        val top = field(cells { row, _ -> row < 20 })
        val band = field(cells { row, _ -> row in 30..39 })
        val both = assertNotNull(ClothField.union(listOf(top, band)))
        for (u in listOf(0.1f, 0.3f, 0.5f, 0.55f, 0.9f)) assertEquals(maxOf(top.at(u, 0.3f), band.at(u, 0.3f)), both.at(u, 0.3f), 1e-6f)
        assertEquals(top, ClothField.union(listOf(top)))
    }
}
