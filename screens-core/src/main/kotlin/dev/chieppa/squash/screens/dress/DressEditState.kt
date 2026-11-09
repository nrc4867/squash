package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.GarmentLook
import dev.chieppa.squash.character.build.GarmentView
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.SquashDocument
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the Dress stage remembers: the garment picked, the slot whose popover is open, the garments left out of view, how the garments
 * show in Dress and in every other stage, and the Loose end tool's options. Never saved or undone.
 */
class DressEditState {
    /** The garment the inspector and the garment tools work on. */
    val garment = MutableStateFlow<GarmentId?>(null)

    /** The slot of the shown outfit whose popover is open. */
    val openSlot = MutableStateFlow<GarmentSlotId?>(null)

    /** The garments left out of view by the layers list's Show buttons. */
    val hidden = MutableStateFlow<Set<GarmentId>>(emptySet())

    /** How the garments show in the Dress stage. */
    val lookInDress = MutableStateFlow(GarmentLook.SHOWN)

    /** How the garments show in every stage but Dress. */
    val lookElsewhere = MutableStateFlow(GarmentLook.GHOSTED)

    /** How many bones the Loose end tool gives a new loose end. */
    val looseBones = MutableStateFlow(LooseEnd.DEFAULT_LOOSE_BONES)

    /** Whether the Loose end tool starts a new loose end at the hem. */
    val looseFromHem = MutableStateFlow(true)

    /** The setting the garments show by in Dress when [dressing], else everywhere else. */
    fun look(dressing: Boolean): MutableStateFlow<GarmentLook> = if (dressing) lookInDress else lookElsewhere

    /** What the session is asked to show of the garments, in Dress when [dressing]. */
    fun view(dressing: Boolean): GarmentView = GarmentView(look(dressing).value, hidden.value)

    /** Shows or leaves out [id]. */
    fun toggleShown(id: GarmentId) {
        hidden.value = if (id in hidden.value) hidden.value - id else hidden.value + id
    }

    /** Keeps the picked garment and the open slot to what [document] holds, picking the first worn garment when none is. */
    fun follow(document: SquashDocument) {
        val wardrobe = document.character.wardrobe
        if (garment.value?.let(wardrobe::garment) == null) garment.value = wardrobe.worn.lastOrNull()?.id ?: wardrobe.garments.firstOrNull()?.id
        if (openSlot.value?.let(wardrobe::slot) == null) openSlot.value = null
        hidden.value = hidden.value.filterTo(mutableSetOf()) { wardrobe.garment(it) != null }
    }

    fun clear() {
        garment.value = null
        openSlot.value = null
        hidden.value = emptySet()
    }
}
