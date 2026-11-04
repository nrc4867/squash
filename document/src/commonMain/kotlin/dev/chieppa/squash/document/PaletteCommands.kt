package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Adds, recolors or removes the palette slot [name]. A slot keeps its place and its shadow tone when recolored; [index] places a
 * new one, last if null, with [shadow]. Removing a slot leaves what names it naming it.
 */
@Serializable
@SerialName("setPaletteSlot")
data class SetPaletteSlot(val name: SlotName, val color: SlotColor?, val index: Int? = null, val shadow: SlotColor? = null) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val slots = character.palette.slots
        val old = slots.locate { it.name == name }
        val next = slots.withItem(color?.let { PaletteSlot(name, it, shadow ?: old.value?.shadow) }, index) { it.name == name }
        return Applied(
            document.copy(character = character.copy(palette = Palette(next))),
            SetPaletteSlot(name, old.value?.color, old.restoreIndex, old.value?.shadow),
        )
    }
}

/** Gives the palette slot [name] its own shadow tone, or with null returns it to the look's warm shift of its color. */
@Serializable
@SerialName("setSlotShadow")
data class SetSlotShadow(val name: SlotName, val shadow: SlotColor?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val slots = character.palette.slots
        val old = requireNotNull(slots.firstOrNull { it.name == name }) { "No palette slot ${name.value}" }
        val next = slots.map { if (it.name == name) it.copy(shadow = shadow) else it }
        return Applied(document.copy(character = character.copy(palette = Palette(next))), SetSlotShadow(name, old.shadow))
    }
}
