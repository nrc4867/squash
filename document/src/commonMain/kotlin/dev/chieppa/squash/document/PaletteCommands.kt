package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Adds, recolors or removes the palette slot [name]. A slot keeps its place when recolored; [index] places a new one, last if
 * null. Removing a slot leaves what names it naming it.
 */
@Serializable
@SerialName("setPaletteSlot")
data class SetPaletteSlot(val name: SlotName, val color: SlotColor?, val index: Int? = null) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val slots = character.palette.slots
        val old = slots.locate { it.name == name }
        val next = slots.withItem(color?.let { PaletteSlot(name, it) }, index) { it.name == name }
        return Applied(
            document.copy(character = character.copy(palette = Palette(next))),
            SetPaletteSlot(name, old.value?.color, old.restoreIndex),
        )
    }
}
