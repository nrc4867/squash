package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.PartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverUnionTest {
    private val leg = PartId("leg")
    private val torso = PartId("torso")

    private fun worn(id: String, vararg branches: GarmentBranch, open: Map<PartId, OpenEnds> = emptyMap()): WornCover {
        val garment = Garment(GarmentId(id), id, GarmentSlotId("slot"))
        val map = branches.associateBy { it.part }
        return WornCover(GarmentCover(garment, map), map, emptySet(), emptySet(), emptyMap(), openEnds = open)
    }

    @Test
    fun rangesThatMeetJoinAtTheMostEaseAndPartsAreKeptApart() {
        val sleeve = worn("sweater", GarmentBranch(torso, 0.4f, 0.97f, 0.03f), GarmentBranch(leg, 0f, 0.78f, 0.03f))
        val boot = worn("boots", GarmentBranch(leg, 0.72f, 1f, 0.05f))
        val union = checkNotNull(coverUnion(listOf(sleeve, boot)))
        assertEquals(GarmentBranch(leg, 0f, 1f, 0.05f), union.branchOf(leg))
        assertEquals(GarmentBranch(torso, 0.4f, 0.97f, 0.03f), union.branchOf(torso))
    }

    @Test
    fun rangesApartKeepTheWidestAndOneGarmentHasNoUnion() {
        val a = worn("a", GarmentBranch(leg, 0f, 0.2f))
        val b = worn("b", GarmentBranch(leg, 0.5f, 1f))
        assertEquals(0.5f, checkNotNull(coverUnion(listOf(a, b))).branchOf(leg)?.from)
        assertNull(coverUnion(listOf(a)))
    }

    @Test
    fun anEndIsOpenInTheUnionOnlyWhereEveryGarmentReachingItLeavesItOpen() {
        val top = worn("top", GarmentBranch(torso, 0.2f, 1f), open = mapOf(torso to OpenEnds(low = false, high = true)))
        val coat = worn("coat", GarmentBranch(torso, 0f, 1f), open = mapOf(torso to OpenEnds(low = false, high = true)))
        assertEquals(OpenEnds(low = false, high = true), checkNotNull(coverUnion(listOf(top, coat))).openEnds[torso])
        val scarf = worn("scarf", GarmentBranch(torso, 0.8f, 1f))
        assertEquals(OpenEnds(low = false, high = false), checkNotNull(coverUnion(listOf(top, scarf))).openEnds[torso])
    }
}
