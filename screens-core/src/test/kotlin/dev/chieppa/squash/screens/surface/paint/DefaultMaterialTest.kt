package dev.chieppa.squash.screens.surface.paint

import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.SetSlotMaterial
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.screens.fennec.BOOT_LEATHER
import dev.chieppa.squash.screens.fennec.CREAM
import dev.chieppa.squash.screens.fennec.FUR
import dev.chieppa.squash.screens.fennec.SWEATER_WOOL
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/** The preset a slot with no material of its own shows. */
class DefaultMaterialTest {
    private val document = fennecDressedDocument()
    private val character = document.character

    @Test
    fun theRootPartsBaseIsSkinAGarmentsColorClothAndTheRestMatte() {
        assertEquals(MaterialPreset.SKIN, defaultPreset(character, FUR))
        assertEquals(MaterialPreset.CLOTH, defaultPreset(character, SWEATER_WOOL))
        assertEquals(MaterialPreset.CLOTH, defaultPreset(character, BOOT_LEATHER))
        assertEquals(MaterialPreset.MATTE, defaultPreset(character, CREAM))
    }

    @Test
    fun aSlotsOwnMaterialIsShownOverTheDefault() {
        assertEquals(SlotMaterial(MaterialPreset.SKIN), shownMaterial(character, FUR))
        val metal = SlotMaterial(MaterialPreset.METAL, steps = 2)
        val changed = SetSlotMaterial(FUR, metal).apply(document).document.character
        assertEquals(metal, shownMaterial(changed, FUR))
    }
}
