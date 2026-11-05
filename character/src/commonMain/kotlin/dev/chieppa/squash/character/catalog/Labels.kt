package dev.chieppa.squash.character.catalog

import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.SlotName

private val WORD_BREAK = Regex("(?<=[a-z0-9])(?=[A-Z])|[-_]")

/** What a palette slot is called in the app: its label, else its name's reading. */
val PaletteSlot.shownName: String get() = label ?: displayName(name.value)

/** What the slot [name] of this palette is called in the app. */
fun Palette.shownName(name: SlotName): String = slots.firstOrNull { it.name == name }?.shownName ?: displayName(name.value)

/** A name from a template or plan file as people read it: `front-leg` and `frontLeg` are both "Front leg". */
fun displayName(name: String): String =
    name.split(WORD_BREAK).joinToString(" ") { it.lowercase() }.replaceFirstChar { it.uppercase() }
