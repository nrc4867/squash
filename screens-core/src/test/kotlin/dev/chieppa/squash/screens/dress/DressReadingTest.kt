package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.screens.fennec.BACK_PAW_L
import dev.chieppa.squash.screens.fennec.BOOTS
import dev.chieppa.squash.screens.fennec.CAP
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.EAR_R
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_R
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.SCARF
import dev.chieppa.squash.screens.fennec.SWEATER
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DressReadingTest {
    private val character: Character = fennecDressedDocument().character
    private fun garment(id: dev.chieppa.squash.document.GarmentId) = assertNotNull(character.wardrobe.garment(id))

    @Test
    fun theCapCoversTheHeadAndTheEarsComeThroughItAsOnePair() {
        val under = character.partsUnder(garment(CAP), null)
        val head = under.single { it.part.id == HEAD }
        assertTrue(head.covered)
        val ears = under.single { it.part.id == EAR_L }
        assertTrue(ears.through, "the ears sit on the covered head")
        assertTrue(under.none { it.part.id == EAR_R }, "a pair has one row")
        assertEquals(2, character.holeCount(garment(CAP), null), "a pair is two holes")
    }

    @Test
    fun theBootsChangeThePawsTheyCover() {
        val paw = character.partsUnder(garment(BOOTS), null).single { it.part.id == BACK_PAW_L }
        assertTrue(paw.covered)
        assertTrue(paw.change is BodyPartChange.Redraw)
    }

    @Test
    fun theSweatersSleevesJoinItsBody() {
        val joins = character.joinsOf(garment(SWEATER))
        assertEquals(listOf(FRONT_LEG_L to TORSO), joins.map { it.first.part to it.second })
        assertEquals(1, character.joinCount(garment(SWEATER)))
    }

    @Test
    fun theScarfWinsOverTheSweaterOnTheTorso() {
        val clash = character.layerClashes().single { it.part.id == TORSO }
        assertEquals(SCARF, clash.outer)
        assertEquals(listOf(SWEATER), clash.under)
    }

    @Test
    fun thePartsRowsSayWhatEachPartIsUnder() {
        assertEquals(DressNote.Under(listOf("Scarf", "Sweater")), character.dressNote(assertNotNull(character.part(TORSO)), null))
        assertEquals(DressNote.Under(listOf("Boots", "Sweater")), character.dressNote(assertNotNull(character.part(FRONT_LEG_R)), null), "the right side reads its pair's branches")
        assertEquals(DressNote.Through, character.dressNote(assertNotNull(character.part(EAR_L)), null))
        val bare = ShowOutfit(null).apply(fennecDressedDocument()).document.character
        assertEquals(DressNote.Bare, bare.dressNote(assertNotNull(bare.part(TAIL)), null))
    }

    @Test
    fun newGarmentsSlotsAndOutfitsTakeTheFirstFreeNumber() {
        val wardrobe = Wardrobe()
        val first = newGarmentIn(wardrobe) { "Garment $it" }
        assertEquals("garment-1", first.id.value)
        assertEquals("Garment 1", first.name)
        val second = newGarmentIn(wardrobe.copy(garments = listOf(first))) { "Garment $it" }
        assertEquals("garment-2", second.id.value)
        assertEquals("slot-1", newSlotIn(wardrobe) { "Slot $it" }.id.value)
        assertEquals("outfit-1", newOutfitIn(wardrobe) { "Outfit $it" }.id.value)
    }
}
