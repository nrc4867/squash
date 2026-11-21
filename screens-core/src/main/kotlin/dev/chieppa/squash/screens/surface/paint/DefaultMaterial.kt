package dev.chieppa.squash.screens.surface.paint

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.SlotName

/** The preset [slot] shows with no material of its own: skin for the root body part's base, cloth for a garment's colour, matte otherwise. */
fun defaultPreset(character: Character, slot: SlotName): MaterialPreset {
    val root = character.parts.firstOrNull { it.socket == null && it.garment == null }
    return when {
        root != null && character.resolved(root).surface.baseSlot == slot -> MaterialPreset.SKIN
        character.wardrobe.garments.any { garment -> garment.color == slot || garment.areas.any { it.color == slot } } -> MaterialPreset.CLOTH
        else -> MaterialPreset.MATTE
    }
}
