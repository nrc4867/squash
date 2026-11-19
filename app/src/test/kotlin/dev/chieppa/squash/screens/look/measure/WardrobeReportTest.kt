package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.Wardrobe
import kotlin.test.Test
import kotlin.test.assertEquals

class WardrobeReportTest {
    @Test
    fun `lists slots, garments with layer, covers, changes and owned parts, and outfits`() {
        val top = GarmentSlotId("top")
        val shirt = Garment(
            GarmentId("shirt"), "Shirt", top, layer = 2, fit = 0.015f,
            branches = listOf(GarmentBranch(PartId("torso"), 0f, 0.8f), GarmentBranch(PartId("arm-l"), 0f, 0.36f, fromV = 0.1f, toV = 0.9f)),
            changes = mapOf(PartId("tail") to BodyPartChange.Through(cuff = false)),
        )
        val wardrobe = Wardrobe(listOf(shirt), listOf(GarmentSlot(top, "Top")), listOf(Outfit(OutfitId("dressed"), "Dressed", mapOf(top to shirt.id))), OutfitId("dressed"))
        val parts = listOf(
            PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 1)),
            PartInstance(PartId("button"), TemplateRef(TemplateId("blob"), 1), garment = shirt.id),
        )
        val lines = wardrobeLines(Character(CharacterId("test"), "Test", BodyPlanRef(BodyPlanId("biped"), 1), parts, wardrobe = wardrobe))
        assertEquals(
            listOf(
                "slots top",
                "garment shirt slot top layer 2 fit 0.015 covers torso u 0.00-0.80, arm-l u 0.00-0.36 v 0.10-0.90; changes tail through; owns button",
                "outfit dressed (shown): top=shirt",
            ),
            lines,
        )
    }

    @Test
    fun `a character with no wardrobe has no lines`() {
        assertEquals(emptyList(), wardrobeLines(Character(CharacterId("test"), "Test", BodyPlanRef(BodyPlanId("biped"), 1))))
    }
}
