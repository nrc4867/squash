package dev.chieppa.squash.document

/** These teeth with [edit] made to the change kept for slot [slot] of jaw [jaw], a new change when none is kept. */
fun MouthTeeth.changingSlot(jaw: JawName, slot: ShownSlot, edit: (ToothSlotChange) -> ToothSlotChange): MouthTeeth {
    val held = jaw(jaw)
    val change = held.changeAt(slot.row, slot.index) ?: ToothSlotChange(row = slot.row, slot = slot.index)
    return withJaw(jaw, held.withChange(edit(change)))
}

/** These teeth with slot [slot] of jaw [jaw] back as its preset, or its adding, has it. */
fun MouthTeeth.resettingSlot(jaw: JawName, slot: ShownSlot): MouthTeeth = withJaw(jaw, jaw(jaw).reset(slot.row, slot.index))

/** These teeth with slot [slot] of jaw [jaw] taken out. */
fun MouthTeeth.removingSlot(jaw: JawName, slot: ShownSlot): MouthTeeth = changingSlot(jaw, slot) { it.copy(removed = true) }

/** These teeth with a copy of [slot] added at the end of its row of jaw [jaw]. */
fun MouthTeeth.addingSlot(jaw: JawName, slot: ShownSlot): MouthTeeth = withJaw(jaw, jaw(jaw).add(slot.row, slot.slot.copy(at = null)))

/** These teeth with a row added behind jaw [jaw]'s [rows] rows, holding a copy of [slot]. */
fun MouthTeeth.addingRow(jaw: JawName, rows: Int, slot: ToothSlot): MouthTeeth = withJaw(jaw, jaw(jaw).addRow().add(rows, slot.copy(at = null)))

/** These teeth with jaw [jaw] at [level], or at its preset's [presetLevel] again, which keeps nothing. */
fun MouthTeeth.atLevel(jaw: JawName, level: TeethLevel, presetLevel: TeethLevel): MouthTeeth =
    withJaw(jaw, jaw(jaw).copy(level = level.takeUnless { it == presetLevel }))
