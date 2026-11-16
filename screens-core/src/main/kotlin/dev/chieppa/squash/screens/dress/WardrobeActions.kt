package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.DuplicateOutfit
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.RemoveGarment
import dev.chieppa.squash.document.RemoveGarmentSlot
import dev.chieppa.squash.document.RemoveOutfit
import dev.chieppa.squash.document.RenameGarment
import dev.chieppa.squash.document.RenameGarmentSlot
import dev.chieppa.squash.document.RenameOutfit
import dev.chieppa.squash.document.SetGarmentSlot
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.library.Renaming
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_outfit_name
import org.jetbrains.compose.resources.getString

/** Whether the shown outfit wears [garment]. */
fun AppState.isWorn(garment: GarmentId): Boolean {
    val wardrobe = session.value?.document?.value?.character?.wardrobe ?: return false
    return wardrobe.shown?.let(wardrobe::outfit)?.picks?.containsValue(garment) == true
}

/**
 * Wears [garment] in its slot for the shown outfit, in place of what that slot wore, or takes it off when the outfit wears it already,
 * and picks it; a garment in no slot, or with no outfit shown, is only picked.
 */
fun AppState.wear(garment: GarmentId) {
    val open = session.value ?: return
    val wardrobe = open.document.value.character.wardrobe
    val held = wardrobe.garment(garment) ?: return
    dress.garment.value = garment
    val outfit = wardrobe.shown?.let(wardrobe::outfit) ?: return
    val slot = held.slot ?: return
    open.send(PickGarment(outfit.id, slot, if (outfit.picks[slot] == garment) null else garment))
}

/** Shows [outfit], or the bare body when null. */
fun AppState.showOutfit(outfit: OutfitId?) {
    val open = session.value ?: return
    if (open.document.value.character.wardrobe.shown != outfit) open.send(ShowOutfit(outfit))
}

/** Makes a garment in [slot], worn at once in a color of its own, picks it, opens its name for typing and arms Edge. */
suspend fun AppState.newGarmentIn(slot: GarmentSlotId) {
    val garment = newGarment(slot) ?: return
    library.garmentCursor.value = garment
    library.renaming.value = Renaming.OfGarment(garment, new = true)
}

/** Adds a copy of [outfit] with its picks after it and shows the copy, one step. */
suspend fun AppState.duplicateOutfit(outfit: OutfitId) {
    val open = session.value ?: return
    val wardrobe = open.document.value.character.wardrobe
    if (wardrobe.outfit(outfit) == null) return
    val copy = newOutfitIn(wardrobe) { getString(Res.string.dress_outfit_name, it) }
    open.send(DuplicateOutfit(outfit, copy.id, copy.name))
}

fun AppState.removeOutfit(outfit: OutfitId) {
    session.value?.send(RemoveOutfit(outfit))
}

fun AppState.renameOutfit(outfit: OutfitId, name: String) {
    val open = session.value ?: return
    val now = open.document.value.character.wardrobe.outfit(outfit) ?: return
    if (name.isNotBlank() && name != now.name) open.send(RenameOutfit(outfit, name.trim()))
}

fun AppState.renameGarment(garment: GarmentId, name: String) {
    val open = session.value ?: return
    val now = open.document.value.character.wardrobe.garment(garment) ?: return
    if (name.isNotBlank() && name != now.name) open.send(RenameGarment(garment, name.trim()))
}

fun AppState.renameSlot(slot: GarmentSlotId, name: String) {
    val open = session.value ?: return
    val now = open.document.value.character.wardrobe.slot(slot) ?: return
    if (name.isNotBlank() && name != now.name) open.send(RenameGarmentSlot(slot, name.trim()))
}

/** Puts [garment] in [slot], or in none; the outfits that wore it stop wearing it. */
fun AppState.moveGarment(garment: GarmentId, slot: GarmentSlotId?) {
    val open = session.value ?: return
    if (open.document.value.character.wardrobe.garment(garment)?.slot != slot) open.send(SetGarmentSlot(garment, slot))
}

/** Removes [garment] from the wardrobe and every outfit, with the parts it owns, one step. */
fun AppState.removeGarment(garment: GarmentId) {
    session.value?.send(RemoveGarment(garment))
}

fun AppState.removeSlot(slot: GarmentSlotId) {
    session.value?.send(RemoveGarmentSlot(slot))
}
