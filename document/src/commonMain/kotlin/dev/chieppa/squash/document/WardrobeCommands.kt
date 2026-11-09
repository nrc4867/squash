package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Makes [wardrobe] the character's wardrobe whole: the undo of every change to its garments, slots and outfits. */
@Serializable
@SerialName("setWardrobe")
data class SetWardrobe(val wardrobe: Wardrobe) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(wardrobe = wardrobe)), SetWardrobe(document.character.wardrobe))
}

/** Adds [garment] to the wardrobe, at [index] or last. It is worn by no outfit until one picks it. */
@Serializable
@SerialName("addGarment")
data class AddGarment(val garment: Garment, val index: Int? = null) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.garment(garment.id) == null) { "Garment ${garment.id.value} already exists" }
        val at = index ?: wardrobe.garments.size
        wardrobe.copy(garments = wardrobe.garments.take(at) + garment + wardrobe.garments.drop(at))
    }
}

/** Removes [garment] from the wardrobe and from every outfit that picks it. */
@Serializable
@SerialName("removeGarment")
data class RemoveGarment(val garment: GarmentId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.garment(garment) != null) { "No garment ${garment.value}" }
        wardrobe.picksKept { _, id -> id != garment }.copy(garments = wardrobe.garments.filter { it.id != garment })
    }
}

/** Puts [garment] in [slot], or in none, and drops every outfit's pick of it. */
@Serializable
@SerialName("setGarmentSlot")
data class SetGarmentSlot(val garment: GarmentId, val slot: GarmentSlotId?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        wardrobe.picksKept { _, id -> id != garment }.withGarment(garment) { it.copy(slot = slot) }
    }
}

/** Lays the garments out in [order], innermost first, each on its own layer from the first; garments it leaves out keep theirs. */
@Serializable
@SerialName("setLayers")
data class SetLayers(val order: List<GarmentId>) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        order.foldIndexed(wardrobe) { i, held, id -> held.withGarment(id) { it.copy(layer = FIRST_LAYER + i) } }
    }
}

/** Adds [slot] to the wardrobe, at [index] or last. */
@Serializable
@SerialName("addGarmentSlot")
data class AddGarmentSlot(val slot: GarmentSlot, val index: Int? = null) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.slot(slot.id) == null) { "Slot ${slot.id.value} already exists" }
        val at = index ?: wardrobe.slots.size
        wardrobe.copy(slots = wardrobe.slots.take(at) + slot + wardrobe.slots.drop(at))
    }
}

/** Removes [slot]: its garments stay in the wardrobe in no slot, and no outfit picks for it. */
@Serializable
@SerialName("removeGarmentSlot")
data class RemoveGarmentSlot(val slot: GarmentSlotId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.slot(slot) != null) { "No slot ${slot.value}" }
        val emptied = wardrobe.picksKept { at, _ -> at != slot }
        emptied.copy(
            slots = emptied.slots.filter { it.id != slot },
            garments = emptied.garments.map { if (it.slot == slot) it.copy(slot = null) else it },
        )
    }
}

/** Names [slot] [name]. */
@Serializable
@SerialName("renameGarmentSlot")
data class RenameGarmentSlot(val slot: GarmentSlotId, val name: String) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.slot(slot) != null) { "No slot ${slot.value}" }
        wardrobe.copy(slots = wardrobe.slots.map { if (it.id == slot) it.copy(name = name) else it })
    }
}

/** Adds [outfit], at [index] or last. */
@Serializable
@SerialName("addOutfit")
data class AddOutfit(val outfit: Outfit, val index: Int? = null) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.outfit(outfit.id) == null) { "Outfit ${outfit.id.value} already exists" }
        val at = index ?: wardrobe.outfits.size
        wardrobe.copy(outfits = wardrobe.outfits.take(at) + outfit + wardrobe.outfits.drop(at))
    }
}

/** Removes [outfit]; while it was shown, the bare body shows. */
@Serializable
@SerialName("removeOutfit")
data class RemoveOutfit(val outfit: OutfitId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.outfit(outfit) != null) { "No outfit ${outfit.value}" }
        wardrobe.copy(outfits = wardrobe.outfits.filter { it.id != outfit }, shown = wardrobe.shown.takeIf { it != outfit })
    }
}

/** Names [outfit] [name]. */
@Serializable
@SerialName("renameOutfit")
data class RenameOutfit(val outfit: OutfitId, val name: String) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.outfit(outfit) != null) { "No outfit ${outfit.value}" }
        wardrobe.copy(outfits = wardrobe.outfits.map { if (it.id == outfit) it.copy(name = name) else it })
    }
}

/** Has [outfit] wear [garment] in [slot], or nothing there when null. The garment must sit in that slot. */
@Serializable
@SerialName("pickGarment")
data class PickGarment(val outfit: OutfitId, val slot: GarmentSlotId, val garment: GarmentId?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { wardrobe ->
        require(wardrobe.outfit(outfit) != null) { "No outfit ${outfit.value}" }
        wardrobe.copy(
            outfits = wardrobe.outfits.map { held ->
                if (held.id != outfit) held else held.copy(picks = if (garment == null) held.picks - slot else held.picks + (slot to garment))
            },
        )
    }
}

/** Shows [outfit] on the character in Build, or the bare body when null. */
@Serializable
@SerialName("showOutfit")
data class ShowOutfit(val outfit: OutfitId?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.wardrobeChanged { it.copy(shown = outfit) }
}

/** This document with its wardrobe changed by [change], undone by setting the wardrobe back whole. */
internal fun SquashDocument.wardrobeChanged(change: (Wardrobe) -> Wardrobe): Applied<SquashDocument> =
    SetWardrobe(change(character.wardrobe)).apply(this)
