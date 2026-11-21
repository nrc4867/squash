package dev.chieppa.squash.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A mouth's teeth over its preset: only what it changes is kept, and the preset's own later changes still reach it. */
class DentitionTest {
    private val fang = ToothSlot("canine")
    private val preset = Jaw(TeethLevel.BLOCKS, listOf(ToothRow(listOf(ToothSlot("incisor"), ToothSlot("incisor"), fang))))

    private fun shown(teeth: MouthTeeth, jaw: Jaw = preset) = teeth.upper.shown(jaw).flatten()

    @Test
    fun noChangesShowThePresetAsItIs() {
        assertEquals(preset, JawTeeth().on(preset))
    }

    @Test
    fun aSlotChangeKeepsOnlyTheValueItSetsAndFollowsThePresetForTheRest() {
        val teeth = MouthTeeth().changingSlot(JawName.UPPER, shown(MouthTeeth())[2]) { it.copy(length = 0.3f, over = true) }
        assertEquals(listOf(ToothSlotChange(row = 0, slot = 2, length = 0.3f, over = true)), teeth.upper.slots)
        val wider = preset.copy(rows = listOf(ToothRow(preset.rows[0].slots.dropLast(1) + fang.copy(width = 0.2f))))
        val slot = teeth.upper.on(wider).rows[0].slots[2]
        assertEquals(0.3f, slot.length)
        assertEquals(0.2f, slot.width, "a later change to the preset still reaches the slot")
        assertTrue(slot.over)
    }

    @Test
    fun resetForgetsTheSlotsChange() {
        val changed = MouthTeeth().changingSlot(JawName.UPPER, shown(MouthTeeth())[0]) { it.copy(curve = 0.5f) }
        assertEquals(MouthTeeth(), changed.resettingSlot(JawName.UPPER, shown(changed)[0]))
    }

    @Test
    fun aRemovedSlotLeavesTheOthersAtTheirIndices() {
        val removed = MouthTeeth().removingSlot(JawName.UPPER, shown(MouthTeeth())[1])
        val left = shown(removed)
        assertEquals(listOf(0, 2), left.map { it.index })
        assertEquals(2, removed.upper.on(preset).rows[0].slots.size)
    }

    @Test
    fun addedSlotsAndRowsFollowThePresetsOwn() {
        val last = shown(MouthTeeth()).last()
        val added = MouthTeeth().addingSlot(JawName.UPPER, last).addingRow(JawName.UPPER, rows = 1, slot = last.slot)
        val jaw = added.upper.on(preset)
        assertEquals(4, jaw.rows[0].slots.size)
        assertEquals(listOf(fang), jaw.rows[1].slots)
        val second = added.upper.shown(preset)[1].single()
        val longer = added.changingSlot(JawName.UPPER, second) { it.copy(length = 0.4f) }
        assertEquals(0.4f, longer.upper.on(preset).rows[1].slots.single().length)
    }

    @Test
    fun choosingThePresetsOwnLevelKeepsNothing() {
        val bar = MouthTeeth().atLevel(JawName.LOWER, TeethLevel.BAR, presetLevel = TeethLevel.NONE)
        assertEquals(TeethLevel.BAR, bar.lower.level)
        assertNull(bar.atLevel(JawName.LOWER, TeethLevel.NONE, presetLevel = TeethLevel.NONE).lower.level)
    }
}
