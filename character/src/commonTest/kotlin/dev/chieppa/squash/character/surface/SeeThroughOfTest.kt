package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.ClothNet
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.document.SlotName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeeThroughOfTest {
    private val nylon = SlotName("nylon")
    private val lace = SlotName("lace")
    private val palette = Palette(listOf(PaletteSlot(nylon, SlotColor(0.1f, 0.1f, 0.1f), material = SlotMaterial(MaterialPreset.CLOTH, seeThrough = 0.5f)), PaletteSlot(lace, SlotColor(0f, 0f, 0f))))

    private fun garment(vararg areas: GarmentArea) = Garment(GarmentId("sock"), "Sock", color = lace, areas = areas.toList())

    @Test
    fun wholeClothIsNotSeeThrough() {
        assertTrue(seeThroughOf(garment(GarmentArea(GarmentAreaId("a"), "")), palette).isEmpty())
    }

    @Test
    fun anAreasOpacityIsItsOwnTimesItsSlots() {
        val looks = seeThroughOf(garment(GarmentArea(GarmentAreaId("a"), "", color = nylon, opacity = 0.4f), GarmentArea(GarmentAreaId("b"), ""), GarmentArea(GarmentAreaId("c"), "", net = ClothNet())), palette)
        assertEquals(0.2f, looks[0]?.opacity)
        assertNull(looks[1], "an area in a whole slot with no opacity hides all")
        assertEquals(1f, looks[2]?.opacity)
        assertEquals(ClothNet(), looks[2]?.net)
    }

    @Test
    fun aSetsCountRoundIsWholeAndRunsRoundWhenLevel() {
        assertEquals(10f, countRound(girth = 0.3f, slant = 0f, hole = 0.03f))
        assertEquals(0f, countRound(girth = 0.3f, slant = 90f, hole = 0.03f), "rings cross no seam")
        assertEquals(-7f, countRound(girth = 0.3f, slant = 135f, hole = 0.03f))
        assertEquals(1f, countRound(girth = 0.01f, slant = 0f, hole = 0.03f), "a thin run still has one")
    }
}
