package dev.chieppa.squash.document

import dev.chieppa.squash.document.WinterFennec.BARE
import dev.chieppa.squash.document.WinterFennec.BOOTS
import dev.chieppa.squash.document.WinterFennec.EAR_L
import dev.chieppa.squash.document.WinterFennec.EAR_R
import dev.chieppa.squash.document.WinterFennec.FEET_SLOT
import dev.chieppa.squash.document.WinterFennec.HAT
import dev.chieppa.squash.document.WinterFennec.HEAD
import dev.chieppa.squash.document.WinterFennec.HEAD_SLOT
import dev.chieppa.squash.document.WinterFennec.PAW_L
import dev.chieppa.squash.document.WinterFennec.PAW_R
import dev.chieppa.squash.document.WinterFennec.SANDALS
import dev.chieppa.squash.document.WinterFennec.TORSO
import dev.chieppa.squash.document.WinterFennec.WINTER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun SquashDocument.then(vararg commands: DocumentCommand): SquashDocument =
    commands.fold(this) { document, command -> command.apply(document).document }

class WornPartTest {
    @Test
    fun aBarePartIsItsResolvedSelf() {
        val body = WinterFennec.body().then(SetParameter(PAW_L, "size", 1.2f))
        val worn = checkNotNull(body.character.wornPart(PAW_L))
        assertEquals(WornPart(WinterFennec.ref("paw", 4), body.character.resolved(PAW_L)!!), worn)
    }

    @Test
    fun aSwapHoldsOnBothSidesOfThePair() {
        val character = WinterFennec.dressed().character
        assertEquals(WinterFennec.ref("foot"), character.wornPart(PAW_L)?.template)
        assertEquals(WinterFennec.ref("foot"), character.wornPart(PAW_R)?.template)
        assertEquals(BOOTS, character.wornPart(PAW_R)?.changedBy)
    }

    @Test
    fun throughHoldsForBothEars() {
        val character = WinterFennec.dressed().character
        assertEquals(BodyPartChange.Through(), character.wornPart(EAR_L)?.through)
        assertEquals(BodyPartChange.Through(), character.wornPart(EAR_R)?.through)
        assertEquals(false, character.wornPart(EAR_R)?.hidden)
    }

    @Test
    fun aRedrawLaysTheGarmentsValuesOverThePartsOwn() {
        val dressed = WinterFennec.dressed().then(SetParameter(PAW_L, "size", 1.2f), PickGarment(WINTER, FEET_SLOT, SANDALS))
        val worn = checkNotNull(dressed.character.wornPart(PAW_L))
        assertEquals(WinterFennec.ref("paw", 4), worn.template)
        assertEquals(mapOf("size" to 1.2f, "toes" to 0f), worn.data.params)
    }

    @Test
    fun takingTheGarmentOffGivesThePartBackExactly() {
        val bare = WinterFennec.body().character
        val undressed = WinterFennec.dressed().then(ShowOutfit(BARE)).character
        listOf(PAW_L, PAW_R, EAR_L, HEAD, TORSO).forEach { part ->
            assertEquals(bare.wornPart(part), undressed.wornPart(part), part.value)
        }
    }

    @Test
    fun theOutermostGarmentChangingAPartWins() {
        val both = WinterFennec.dressed().then(
            SetGarmentSlot(SANDALS, HEAD_SLOT),
            PickGarment(WINTER, HEAD_SLOT, SANDALS),
            SetLayers(listOf(BOOTS, SANDALS)),
        )
        assertEquals(SANDALS, both.character.wornPart(PAW_L)?.changedBy)
        assertEquals(WinterFennec.ref("paw", 4), both.character.wornPart(PAW_L)?.template)
        val bootsOuter = both.then(SetLayers(listOf(SANDALS, BOOTS)))
        assertEquals(BOOTS, bootsOuter.character.wornPart(PAW_L)?.changedBy)
        assertEquals(WinterFennec.ref("foot"), bootsOuter.character.wornPart(PAW_L)?.template)
    }

    @Test
    fun hideHidesAndAnOuterThroughBringsThePartBack() {
        val hidden = WinterFennec.dressed().then(SetBodyPartChange(HAT, EAR_L, BodyPartChange.Hide))
        assertTrue(checkNotNull(hidden.character.wornPart(EAR_R)).hidden)
        assertNull(hidden.character.wornPart(EAR_R)?.through)
        val outer = hidden.then(SetLayers(listOf(HAT, BOOTS)), SetBodyPartChange(BOOTS, EAR_R, BodyPartChange.Through(cuff = false)))
        assertEquals(false, outer.character.wornPart(EAR_L)?.hidden)
        assertEquals(BodyPartChange.Through(cuff = false), outer.character.wornPart(EAR_L)?.through)
    }
}
