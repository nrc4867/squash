package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** An sRGB color, each channel 0 to 1. */
@Serializable
data class SlotColor(val red: Float, val green: Float, val blue: Float) {
    init {
        require(red in 0f..1f && green in 0f..1f && blue in 0f..1f) { "A color's channels run 0 to 1, not ($red, $green, $blue)" }
    }
}

@Serializable
data class PaletteSlot(val name: SlotName, val color: SlotColor)

/** A character's named colors, in the order they are listed. */
@Serializable
data class Palette(val slots: List<PaletteSlot> = emptyList()) {
    init {
        require(slots.size == slots.distinctBy { it.name }.size) { "A palette names a slot twice: ${slots.map { it.name.value }}" }
    }

    /** The color of slot [name], or null if no slot holds that name. */
    fun color(name: SlotName): SlotColor? = slots.firstOrNull { it.name == name }?.color
}
