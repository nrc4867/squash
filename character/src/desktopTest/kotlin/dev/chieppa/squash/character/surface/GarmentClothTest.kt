package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.runsOfCells
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.PartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A garment's painted areas read as each covered part's cloth. */
class GarmentClothTest {
    private val left = PartId("leg-l")
    private val right = PartId("leg-r")
    private val neck = PartId("neck")
    private val twins = mapOf(left to right, right to left)

    private fun rows(range: IntRange): List<Int> = runsOfCells(BooleanArray(CHART_CELLS) { it / CHART_COLUMNS in range })

    private fun garment(vararg areas: Map<PartId, List<Int>>, branches: List<GarmentBranch>) =
        Garment(GarmentId("sock"), "Sock", GarmentSlotId("feet"), branches = branches, areas = areas.mapIndexed { i, cells -> GarmentArea(GarmentAreaId("a$i"), "Area $i", cells) })

    private fun clothOf(garment: Garment): Map<PartId, ClothField> {
        val branches = garment.branches.associateBy { it.part } + garment.branches.filter { it.part == left }.map { right to it }
        return clothOf(garment, branches, twins)
    }

    @Test
    fun aPairsOtherSideReadsItsLeadersCells() {
        val sock = garment(mapOf(left to rows(32..63)), branches = listOf(GarmentBranch(left, 0.5f, 1f)))
        val cloth = clothOf(sock)
        assertSame(cloth[left], cloth[right], "both sides read the leader's cells")
        assertEquals(CLOTH_HEM_LEVEL, assertNotNull(cloth[right]).at(0.5f, 0.2f), 1e-5f)
    }

    @Test
    fun aPartNoAreaPaintsAndAGarmentWithNoAreasHaveNoCloth() {
        val sock = garment(mapOf(left to rows(32..63)), branches = listOf(GarmentBranch(left, 0.5f, 1f), GarmentBranch(neck, 0f, 0.1f)))
        assertNull(clothOf(sock)[neck], "a branch over a part painted nowhere covers nothing")
        val bare = Garment(GarmentId("box"), "Box", GarmentSlotId("feet"), branches = listOf(GarmentBranch(left, 0.5f, 1f)))
        assertTrue(clothOf(bare).isEmpty())
    }

    @Test
    fun theCellsAreTheClothWhateverTheBranchSays() {
        val sock = garment(mapOf(left to rows(16..63)), branches = listOf(GarmentBranch(left, 0.5f, 1f)))
        val cloth = assertNotNull(clothOf(sock)[left])
        assertEquals(CLOTH_HEM_LEVEL, cloth.at(0.25f, 0.2f), 1e-5f, "the hem is the cells' edge")
        assertTrue(cloth.at(0.1f, 0.2f) < 0.01f)
    }

    @Test
    fun aBoxPaintedReadsItsHemAtItsCellsEdge() {
        val sock = Garment(GarmentId("box"), "Box", GarmentSlotId("feet"), branches = listOf(GarmentBranch(left, 0.4f, 1.3f))).withBoxesPainted()
        val cloth = assertNotNull(clothOf(sock)[left])
        assertEquals(CLOTH_HEM_LEVEL, cloth.at(26f / CHART_ROWS, 0.2f), 1e-5f, "the edge of the first row whose middle lies past 0.4")
        val along = alongOf(cloth.reach, checkNotNull(sock.branch(left)))
        assertEquals(26f / CHART_ROWS, along.start)
        assertEquals(1.3f, along.endInclusive, "running on past the end it reaches")
        val band = cuffCloth(GarmentBranch(neck, -1f, 0.1f))
        assertEquals(CLOTH_HEM_LEVEL, band.at(6f / CHART_ROWS, 0.7f), 1e-5f, "a cuff's band holds the rows from its root under its size")
    }

    @Test
    fun eachAreaOfAGarmentOfSeveralKeepsItsOwnFieldOnEveryPart() {
        val sock = garment(mapOf(left to rows(32..47)), mapOf(left to rows(48..63)), branches = listOf(GarmentBranch(left, 0.5f, 1f)))
        val cloth = assertNotNull(clothOf(sock)[left])
        assertEquals(2, cloth.areaCount)
        assertTrue(cloth.paints(0) && cloth.paints(1))
        assertEquals(0f, cloth.areaAt(0, 48f / CHART_ROWS, 0.2f) - cloth.areaAt(1, 48f / CHART_ROWS, 0.2f), 1e-5f)
        val one = garment(mapOf(left to rows(32..47)), mapOf(neck to rows(0..10)), branches = listOf(GarmentBranch(left, 0.5f, 1f), GarmentBranch(neck, 0f, 0.2f)))
        val leg = assertNotNull(clothOf(one)[left])
        assertEquals(2, leg.areaCount, "a part one area paints still holds a field for each")
        assertTrue(leg.paints(0) && !leg.paints(1))
    }
}
