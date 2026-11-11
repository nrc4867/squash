package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument

/** Where the saved dressed woman lies among the test fixtures' resources. */
const val WOMAN_DRESSED_RESOURCE = "woman-dressed.json"

val BEACH = OutfitId("beach")
val OFFICE = OutfitId("office")
val CASUAL = OutfitId("casual")
val DRESS = OutfitId("dress")
val WINTER = OutfitId("winter")
val GOTH = OutfitId("goth")
val BARE = OutfitId("bare")

/** Each outfit's name and what it picks per slot; the shorts, pumps, boots and dark top are shared between outfits. */
val OUTFITS: List<Triple<OutfitId, String, Map<GarmentSlotId, GarmentId>>> = listOf(
    Triple(BEACH, "Beach", mapOf(TOP_SLOT to BIKINI_TOP, BOTTOM_SLOT to SHORTS, FEET_SLOT to SANDALS, EYES_SLOT to SHADES)),
    Triple(OFFICE, "Office", mapOf(TOP_SLOT to BLOUSE, OUTER_SLOT to BLAZER, BOTTOM_SLOT to TROUSERS, FEET_SLOT to PUMPS)),
    Triple(CASUAL, "Casual", mapOf(TOP_SLOT to TEE, BOTTOM_SLOT to SHORTS, FEET_SLOT to SNEAKERS)),
    Triple(DRESS, "Dress", mapOf(TOP_SLOT to TANK_TOP, BOTTOM_SLOT to SKIRT, FEET_SLOT to PUMPS)),
    Triple(WINTER, "Winter", mapOf(TOP_SLOT to DARK_TOP, OUTER_SLOT to COAT, BOTTOM_SLOT to JEANS, FEET_SLOT to BOOTS, HEAD_SLOT to BEANIE, NECK_SLOT to SCARF, HANDS_SLOT to MITTENS)),
    Triple(GOTH, "Goth", mapOf(TOP_SLOT to DARK_TOP, BOTTOM_SLOT to BLACK_JEANS, FEET_SLOT to BOOTS, NECK_SLOT to CHOKER, FACE_SLOT to MAKEUP, WAIST_SLOT to BELT)),
)

private fun outfits(): List<DocumentCommand> = OUTFITS.flatMap { (id, name, picks) ->
    listOf(AddOutfit(Outfit(id, name))) + picks.map { (slot, garment) -> PickGarment(id, slot, garment) }
} + AddOutfit(Outfit(BARE, "Bare"))

/** The commands that dress her: the slots, every garment, the six outfits and Bare, Casual shown. */
fun womanDressing(): List<DocumentCommand> = wardrobeSlots() + tops() + bottoms() + dress() + shoes() + pumps() + accessories() + shades() + suitParts() + belt() + outfits() + ShowOutfit(CASUAL)

/** Every command that makes the dressed woman: the woman, then her clothes. */
fun womanDressedCommands(): List<DocumentCommand> = womanCommands() + womanDressing()

/** The dressed woman as those commands leave it. */
fun womanDressedDocument(): SquashDocument = womanDressedCommands().fold(EMPTY_WOMAN) { document, command -> command.apply(document).document }

/** The dressed woman from the example file. */
fun savedWomanDressed(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(WOMAN_DRESSED_RESOURCE)) { "No $WOMAN_DRESSED_RESOURCE" }.readText())
