package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.LooseEndId
import dev.chieppa.squash.screens.fennec.CAP
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_R
import dev.chieppa.squash.screens.fennec.SCARF
import dev.chieppa.squash.screens.fennec.SWEATER
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NewLooseEndTest {
    private val character: Character = fennecDressedDocument().character
    private fun garment(id: dev.chieppa.squash.document.GarmentId) = assertNotNull(character.wardrobe.garment(id))

    @Test
    fun aNewEndTakesTheNextFreeIdAndTheToolsSettings() {
        val set = assertNotNull(character.newLooseEnd(garment(SCARF), TORSO, 0.25f, bones = 3, fromHem = false))
        assertEquals(SCARF, set.garment)
        assertEquals(LooseEndId("end-1"), set.end.id)
        assertEquals(TORSO, set.end.branch)
        assertEquals(0.25f, set.end.v)
        assertEquals(3, set.end.bones)
        assertEquals(false, set.end.fromHem)
    }

    @Test
    fun anEndOnAPairsOtherSideHangsFromTheBranchOnItsLeader() {
        val set = assertNotNull(character.newLooseEnd(garment(SWEATER), FRONT_LEG_R, 0.5f, bones = 5, fromHem = true))
        assertEquals(FRONT_LEG_L, set.end.branch)
    }

    @Test
    fun aGarmentHangsNoEndFromAPartItDoesNotCover() {
        assertNull(character.newLooseEnd(garment(CAP), TORSO, 0.5f, bones = 5, fromHem = true))
    }
}
