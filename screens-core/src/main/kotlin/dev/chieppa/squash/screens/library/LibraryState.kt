package dev.chieppa.squash.screens.library

import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.TemplateId
import kotlinx.coroutines.flow.MutableStateFlow

/** The Library drawer's two halves. */
enum class LibraryTab { PARTS, GARMENTS }

/** What the Library drawer's Garments half has open for typing a new name in place. */
sealed interface Renaming {
    data class OfGarment(val garment: GarmentId) : Renaming
    data class OfOutfit(val outfit: OutfitId) : Renaming
    data class OfSlot(val slot: GarmentSlotId) : Renaming
}

/**
 * What the Library drawer remembers: the half shown, the search, the keyboard cursors, the templates added last, the groups folded per
 * character and what is being renamed. Never saved or undone.
 */
class LibraryState {
    val tab = MutableStateFlow(LibraryTab.PARTS)
    val query = MutableStateFlow("")

    /** The template under the Parts grids' keyboard cursor. */
    val cursor = MutableStateFlow<TemplateId?>(null)

    /** The garment under the Garments grids' keyboard cursor. */
    val garmentCursor = MutableStateFlow<GarmentId?>(null)

    /** The templates added last, newest first. */
    val recent = MutableStateFlow<List<TemplateId>>(emptyList())

    /** The groups each character has folded. */
    val folds = MutableStateFlow<Map<CharacterId, Set<LibraryGroup>>>(emptyMap())

    /** Counts the requests to put the focus in the search field; the drawer answers each new count. */
    val searchRequests = MutableStateFlow(0)

    val renaming = MutableStateFlow<Renaming?>(null)

    fun folded(character: CharacterId): Set<LibraryGroup> = folds.value[character].orEmpty()

    fun toggleFold(character: CharacterId, group: LibraryGroup) {
        val now = folded(character)
        folds.value = folds.value + (character to if (group in now) now - group else now + group)
    }

    /** Puts [template] first among the recent ones. */
    fun added(template: TemplateId) {
        recent.value = (listOf(template) + (recent.value - template)).take(RECENT_COUNT)
    }

    fun requestSearch() {
        searchRequests.value += 1
    }

    /** Drops the search, the cursors and the renaming, as a newly opened character starts. */
    fun clear() {
        query.value = ""
        cursor.value = null
        garmentCursor.value = null
        renaming.value = null
    }

    companion object {
        /** How many recent templates the Recent row holds. */
        const val RECENT_COUNT = 8
    }
}
