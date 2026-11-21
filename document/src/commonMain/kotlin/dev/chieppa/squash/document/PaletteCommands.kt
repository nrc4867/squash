package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Adds, recolors or removes the palette slot [name]. A slot keeps its place and its material when recolored; [index] places a
 * new one, last if null, with [label] and [material]. Removing a slot leaves what names it naming it.
 */
@Serializable
@SerialName("setPaletteSlot")
data class SetPaletteSlot(
    val name: SlotName,
    val color: SlotColor?,
    val index: Int? = null,
    val label: String? = null,
    val material: SlotMaterial? = null,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val slots = character.palette.slots
        val old = slots.locate { it.name == name }
        val next = slots.withItem(color?.let { PaletteSlot(name, it, label ?: old.value?.label, material ?: old.value?.material) }, index) { it.name == name }
        return Applied(
            document.copy(character = character.copy(palette = Palette(next))),
            SetPaletteSlot(name, old.value?.color, old.restoreIndex, old.value?.label, old.value?.material),
        )
    }
}

/** Calls the palette slot [name] [label], or with null by its name's own reading. What refers to the slot still does. */
@Serializable
@SerialName("setSlotLabel")
data class SetSlotLabel(val name: SlotName, val label: String?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val slots = character.palette.slots
        val old = requireNotNull(slots.firstOrNull { it.name == name }) { "No palette slot ${name.value}" }
        val next = slots.map { if (it.name == name) it.copy(label = label) else it }
        return Applied(document.copy(character = character.copy(palette = Palette(next))), SetSlotLabel(name, old.label))
    }
}

/**
 * Gives the palette slot [name] [material], its toon finish, or with null returns it to matte. Everything painted in the slot takes it at
 * its next draw.
 */
@Serializable
@SerialName("setSlotMaterial")
data class SetSlotMaterial(val name: SlotName, val material: SlotMaterial?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val slots = character.palette.slots
        val old = requireNotNull(slots.firstOrNull { it.name == name }) { "No palette slot ${name.value}" }
        val next = slots.map { if (it.name == name) it.copy(material = material) else it }
        return Applied(document.copy(character = character.copy(palette = Palette(next))), SetSlotMaterial(name, old.material))
    }
}
