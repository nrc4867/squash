package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_garment_name
import dev.chieppa.squash.uidesign.generated.dress_outfit_name
import dev.chieppa.squash.uidesign.generated.dress_slot_name
import org.jetbrains.compose.resources.getString

/** What new garment, slot and outfit ids start with, before their number. */
const val NEW_GARMENT = "garment-"
const val NEW_SLOT = "slot-"
const val NEW_OUTFIT = "outfit-"

/** The first id from [prefix] and a number that [taken] does not hold, with its number. */
fun freshNumbered(prefix: String, taken: Set<String>): Pair<String, Int> =
    generateSequence(1) { it + 1 }.map { "$prefix$it" to it }.first { it.first !in taken }

/** A garment new to [wardrobe], named [name] with its number, in no slot and with nothing covered. */
inline fun newGarmentIn(wardrobe: Wardrobe, name: (Int) -> String): Garment {
    val (id, number) = freshNumbered(NEW_GARMENT, wardrobe.garments.map { it.id.value }.toSet())
    return Garment(GarmentId(id), name(number))
}

/** A slot new to [wardrobe], named [name] with its number. */
inline fun newSlotIn(wardrobe: Wardrobe, name: (Int) -> String): GarmentSlot {
    val (id, number) = freshNumbered(NEW_SLOT, wardrobe.slots.map { it.id.value }.toSet())
    return GarmentSlot(GarmentSlotId(id), name(number))
}

/** An outfit new to [wardrobe], named [name] with its number, wearing nothing. */
inline fun newOutfitIn(wardrobe: Wardrobe, name: (Int) -> String): Outfit {
    val (id, number) = freshNumbered(NEW_OUTFIT, wardrobe.outfits.map { it.id.value }.toSet())
    return Outfit(OutfitId(id), name(number))
}

/** Adds a new garment to the open character's wardrobe and picks it. */
suspend fun AppState.newGarment() {
    val open = session.value ?: return
    val garment = newGarmentIn(open.document.value.character.wardrobe) { getString(Res.string.dress_garment_name, it) }
    open.send(AddGarment(garment))
    dress.garment.value = garment.id
}

/** Adds a new slot to the open character's wardrobe. */
suspend fun AppState.newSlot() {
    val open = session.value ?: return
    open.send(AddGarmentSlot(newSlotIn(open.document.value.character.wardrobe) { getString(Res.string.dress_slot_name, it) }))
}

/** Adds a new outfit to the open character's wardrobe and shows it, in one step. */
suspend fun AppState.newOutfit() {
    val open = session.value ?: return
    val outfit = newOutfitIn(open.document.value.character.wardrobe) { getString(Res.string.dress_outfit_name, it) }
    open.send(Together(listOf(AddOutfit(outfit), ShowOutfit(outfit.id))))
}
