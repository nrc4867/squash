package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.SlotColor

/** The colors new garments take in turn, each clear of skin and fur and of the one before. */
val GARMENT_COLORS: List<SlotColor> = listOf(
    SlotColor(0.24f, 0.52f, 0.36f),
    SlotColor(0.22f, 0.38f, 0.64f),
    SlotColor(0.70f, 0.26f, 0.24f),
    SlotColor(0.84f, 0.62f, 0.20f),
    SlotColor(0.46f, 0.30f, 0.58f),
    SlotColor(0.18f, 0.56f, 0.60f),
    SlotColor(0.36f, 0.24f, 0.18f),
)

/** The color a new garment of [id] takes: its number's turn through [GARMENT_COLORS]. */
fun garmentColor(id: GarmentId): SlotColor {
    val number = id.value.substringAfterLast('-').toIntOrNull() ?: 1
    return GARMENT_COLORS[(number - 1).mod(GARMENT_COLORS.size)]
}
