package dev.chieppa.squash.screens.edit

import dev.chieppa.squash.document.SlotName
import kotlin.test.Test
import kotlin.test.assertEquals

class SlotShelfTest {
    private fun slots(vararg names: String) = names.map(::SlotName)
    private val palette = slots("a", "b", "c", "d", "e")

    @Test
    fun whatFitsShowsInPaletteOrder() {
        assertEquals(slots("a", "b", "c"), SlotShelf().shown(palette, SlotName("a"), 3))
        assertEquals(palette, SlotShelf().shown(palette, SlotName("a"), 5))
    }

    @Test
    fun pickingOneThatShowsMovesNothing() {
        val shelf = SlotShelf()
        shelf.shown(palette, SlotName("a"), 3)
        assertEquals(slots("a", "b", "c"), shelf.shown(palette, SlotName("c"), 3))
    }

    @Test
    fun aHiddenOneTakesThePlaceOfTheOneUsedLongestAgo() {
        val shelf = SlotShelf()
        shelf.shown(palette, SlotName("a"), 3)
        shelf.shown(palette, SlotName("b"), 3)
        shelf.shown(palette, SlotName("a"), 3)
        assertEquals(slots("a", "b", "e"), shelf.shown(palette, SlotName("e"), 3), "c was never used")
        assertEquals(slots("a", "d", "e"), shelf.shown(palette, SlotName("d"), 3), "then b is the oldest")
    }

    @Test
    fun aDeletedOneLeavesAndTheMostRecentHiddenFillsIn() {
        val shelf = SlotShelf()
        shelf.shown(palette, SlotName("d"), 2)
        shelf.shown(palette, SlotName("a"), 2)
        shelf.shown(palette, SlotName("b"), 2)
        assertEquals(slots("b", "a"), shelf.shown(palette, SlotName("b"), 2), "b took the place of d")
        assertEquals(slots("b", "d"), shelf.shown(palette - SlotName("a"), SlotName("b"), 2))
    }

    @Test
    fun askingAgainChangesNothing() {
        val shelf = SlotShelf()
        val first = shelf.shown(palette, SlotName("e"), 3)
        assertEquals(first, shelf.shown(palette, SlotName("e"), 3))
    }
}
