package dev.chieppa.squash.screens.surface.paint

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.SlotName

/** The grey a new palette slot starts at. */
const val NEW_SLOT_GREY = 0.6f

/** A slot's name not yet in [character]'s palette, from [base] with a number when it must. */
fun freshSlotName(character: Character, base: String): SlotName =
    generateSequence(1) { it + 1 }.map { SlotName(if (it == 1) base else "$base-$it") }.first { character.palette.color(it) == null }
