package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.RemoveParts
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.Wardrobe

/** A tile of Library's Garments half that shows a thumbnail: an outfit, or a garment. */
sealed interface WardrobeShot {
    data class OfOutfit(val outfit: OutfitId) : WardrobeShot
    data class OfGarment(val garment: GarmentId) : WardrobeShot
}

/**
 * What a wardrobe thumbnail draws: [document]'s character wearing what its one outfit picks, in its display pose, seen whole, or with
 * [around] close round that garment. [document] holds only what the thumbnail shows: no names, no working pose, and of the wardrobe only
 * the garments worn and their slots, so renaming, posing or changing another garment keeps a thumbnail.
 */
data class WardrobeScene(val document: SquashDocument, val around: GarmentId?)

/** The outfit a [WardrobeScene]'s document shows, the only one it holds. */
private val SCENE_OUTFIT = OutfitId("thumbnail")

/** Every tile of [wardrobe] that can show a thumbnail, in the order the drawer lists them: outfits, then garments in a slot by slot. */
fun wardrobeShots(wardrobe: Wardrobe): List<WardrobeShot> =
    wardrobe.outfits.map { WardrobeShot.OfOutfit(it.id) } +
        wardrobe.slots.flatMap { slot -> wardrobe.garments.filter { it.slot == slot.id }.map { WardrobeShot.OfGarment(it.id) } }

/**
 * What [shot]'s thumbnail draws from [document]: an outfit worn whole, or a garment alone on the bare body. Null for an outfit or garment
 * the wardrobe lacks, or a garment in no slot, which no outfit can wear.
 */
fun wardrobeScene(document: SquashDocument, shot: WardrobeShot): WardrobeScene? {
    val wardrobe = document.character.wardrobe
    return when (shot) {
        is WardrobeShot.OfOutfit -> wardrobe.outfit(shot.outfit)?.let { WardrobeScene(document.wearing(it.picks), null) }
        is WardrobeShot.OfGarment -> {
            val slot = wardrobe.garment(shot.garment)?.slot ?: return null
            WardrobeScene(document.wearing(mapOf(slot to shot.garment)), shot.garment)
        }
    }
}

/** This document's character alone, wearing [picks] and holding no other garment or the parts they own, unnamed and in its display pose. */
private fun SquashDocument.wearing(picks: Map<GarmentSlotId, GarmentId>): SquashDocument {
    val wardrobe = character.wardrobe
    val worn = picks.values.toSet()
    val kept = Wardrobe(
        garments = wardrobe.garments.filter { it.id in worn }.map { it.copy(name = "") },
        slots = wardrobe.slots.filter { it.id in picks.keys }.map { it.copy(name = "") },
        outfits = listOf(Outfit(SCENE_OUTFIT, "", picks)),
        shown = SCENE_OUTFIT,
    )
    val unworn = character.parts.filter { it.garment != null && it.garment !in worn }.map { it.id }
    val body = if (unworn.isEmpty()) character else RemoveParts(unworn).apply(this).document.character
    return SquashDocument(body.copy(name = "", pose = Pose(), wardrobe = kept))
}
