package dev.chieppa.squash.screens.edit

import dev.chieppa.squash.document.SlotName

/**
 * Which palette slots show in the drawer when not all of them fit. A slot that shows keeps its place; the slot in use always shows, taking the place of
 * the one used longest ago, and a place left free goes to the slot used most recently, then to the palette's next. Never saved or undone.
 */
class SlotShelf {
    private var shelf: List<SlotName> = emptyList()
    private var recent: List<SlotName> = emptyList()

    /** What shows of [palette] with room for [capacity] slots while [current] is in use. Asking again with the same answers changes nothing. */
    fun shown(palette: List<SlotName>, current: SlotName?, capacity: Int): List<SlotName> {
        recent = (listOfNotNull(current) + recent.filter { it != current }).filter { it in palette }
        fun usedAgo(slot: SlotName) = recent.indexOf(slot).let { if (it < 0) Int.MAX_VALUE else it }
        val next = shelf.filter { it in palette }.toMutableList()
        while (next.size > capacity) next.remove(next.maxBy(::usedAgo))
        if (current != null && current !in next) {
            if (next.size < capacity) next += current else next[next.indexOf(next.maxBy(::usedAgo))] = current
        }
        val waiting = palette.filter { it !in next }.sortedBy(::usedAgo)
        next += waiting.take((capacity - next.size).coerceAtLeast(0))
        shelf = next
        return next
    }

    fun clear() {
        shelf = emptyList()
        recent = emptyList()
    }
}
