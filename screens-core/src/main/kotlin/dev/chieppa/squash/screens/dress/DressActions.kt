package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.LooseEndId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetLooseEnd
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
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.document.AddWornGarment
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SNUG_FIT
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_garment_name
import dev.chieppa.squash.uidesign.generated.dress_outfit_name
import dev.chieppa.squash.uidesign.generated.dress_slot_name
import org.jetbrains.compose.resources.getString

/** What new garment, slot and outfit ids start with, before their number. */
const val NEW_GARMENT = "garment-"
const val NEW_SLOT = "slot-"
const val NEW_OUTFIT = "outfit-"
const val NEW_LOOSE_END = "end-"

/** The first id from [prefix] and a number that [taken] does not hold, with its number. */
fun freshNumbered(prefix: String, taken: Set<String>): Pair<String, Int> =
    generateSequence(1) { it + 1 }.map { "$prefix$it" to it }.first { it.first !in taken }

/**
 * The command that hangs a new loose end of [bones] bones from [garment]'s branch over [part] at [v] round it, from its hem when
 * [fromHem]; null where the garment does not cover the part.
 */
fun Character.newLooseEnd(garment: Garment, part: PartId, v: Float, bones: Int, fromHem: Boolean): SetLooseEnd? {
    val instance = part(part) ?: return null
    val branch = covers(garment, instance) ?: return null
    val (id, _) = freshNumbered(NEW_LOOSE_END, garment.looseEnds.map { it.id.value }.toSet())
    return SetLooseEnd(garment.id, LooseEnd(LooseEndId(id), branch.part, v, bones, fromHem))
}

/** A garment new to [wardrobe], named [name] with its number, in no slot, with nothing covered and a snug fit. */
inline fun newGarmentIn(wardrobe: Wardrobe, name: (Int) -> String): Garment {
    val (id, number) = freshNumbered(NEW_GARMENT, wardrobe.garments.map { it.id.value }.toSet())
    return Garment(GarmentId(id), name(number), fit = SNUG_FIT)
}

/** The names the slot and the outfit made with a new garment take. */
class NewGarmentNames(val slot: String, val outfit: String)

/**
 * The commands that make [garment] in [slot], or in the first slot when none is given, worn at once, in a color of its own, as one
 * step: a character with no slot gains one, and one showing no outfit shows its first, or gains one when it has none.
 */
fun Character.newGarmentCommand(garment: Garment, slot: GarmentSlotId?, names: NewGarmentNames): DocumentCommand {
    val commands = ArrayList<DocumentCommand>()
    val into = slot ?: wardrobe.slots.firstOrNull()?.id ?: newSlotIn(wardrobe) { names.slot }.also { commands += AddGarmentSlot(it) }.id
    val color = SlotName(garment.id.value)
    commands += SetPaletteSlot(color, garmentColor(garment.id), label = garment.name)
    val outfit = wardrobe.shown ?: wardrobe.outfits.firstOrNull()?.id ?: newOutfitIn(wardrobe) { names.outfit }.also { commands += AddOutfit(it) }.id
    if (wardrobe.shown != outfit) commands += ShowOutfit(outfit)
    commands += AddWornGarment(garment.copy(slot = into, color = color), outfit)
    return Together(commands)
}

/** The names [newGarmentCommand] gives a slot and an outfit new to [wardrobe], read from the app's strings. */
suspend fun newGarmentNames(wardrobe: Wardrobe): NewGarmentNames = NewGarmentNames(
    newSlotIn(wardrobe) { getString(Res.string.dress_slot_name, it) }.name,
    newOutfitIn(wardrobe) { getString(Res.string.dress_outfit_name, it) }.name,
)

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

/**
 * Makes a new garment, worn at once in its own color (see [newGarmentCommand]), in [slot] or the picked garment's, picks it and arms
 * Edge; its id, or null with no character open.
 */
suspend fun AppState.newGarment(slot: GarmentSlotId? = null): GarmentId? {
    val open = session.value ?: return null
    val character = open.document.value.character
    val names = newGarmentNames(character.wardrobe)
    val garment = newGarmentIn(character.wardrobe) { getString(Res.string.dress_garment_name, it) }
    val into = slot ?: dress.garment.value?.let(character.wardrobe::garment)?.slot
    open.send(character.newGarmentCommand(garment, into, names))
    dress.garment.value = garment.id
    tool.value = BuildTool.EDGE
    return garment.id
}

/** Adds a new slot to the open character's wardrobe; its id, or null with no character open. */
suspend fun AppState.newSlot(): GarmentSlotId? {
    val open = session.value ?: return null
    val slot = newSlotIn(open.document.value.character.wardrobe) { getString(Res.string.dress_slot_name, it) }
    open.send(AddGarmentSlot(slot))
    return slot.id
}

/** Adds a new outfit to the open character's wardrobe and shows it, in one step; its id, or null with no character open. */
suspend fun AppState.newOutfit(): OutfitId? {
    val open = session.value ?: return null
    val outfit = newOutfitIn(open.document.value.character.wardrobe) { getString(Res.string.dress_outfit_name, it) }
    open.send(Together(listOf(AddOutfit(outfit), ShowOutfit(outfit.id))))
    return outfit.id
}
