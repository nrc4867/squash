package dev.chieppa.squash.screens.edit

import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.screens.AppState

/**
 * The slot the paint tools lay down: the one picked, or, while none is, the first of the palette the selected part's base does not already
 * show, so a first stroke on it shows.
 */
fun AppState.paintSlot(): SlotName? {
    val open = session.value ?: return null
    val character = open.document.value.character
    val base = selectedPart.value?.let { id ->
        character.part(id)?.let { character.resolved(it).surface.baseSlot } ?: open.heldColor(id).output.value?.baseSlot
    }
    return paint.slotIn(character.palette, base)
}
