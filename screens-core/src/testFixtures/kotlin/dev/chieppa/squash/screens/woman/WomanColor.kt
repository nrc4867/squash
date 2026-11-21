package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName

val SKIN = SlotName("skin")
val HAIR_BROWN = SlotName("hair")
val LIPS = SlotName("lips")

/** The inside of her mouth, which shows as her lips part. */
val MOUTH_INSIDE = SlotName("mouth")
val BLUSH = SlotName("blush")
val HAZEL = SlotName("hazel")
val EYE_WHITE = SlotName("eye-white")
val BROW = SlotName("brow")
val INK = SlotName("ink")

/** The slot the hand's nails show, as its template names it. */
val NAILS = SlotName("nails")

/** The parts her skin covers, by their left side where they pair. */
private val SKIN_PARTS = listOf(TORSO, NECK, HEAD, ARM_L, HAND_L, LEG_L, FOOT_L)
private val HAIR_PARTS = listOf(HAIR, HAIR_FALL, FALL_INNER_L, FALL_OUTER_L, LOCK_L)

/** Her palette: fair skin, warm brown hair, hazel eyes, soft rose lips and cheeks; and a base slot for every part. */
fun womanColor(): List<DocumentCommand> = listOf(
    SetPaletteSlot(SKIN, SlotColor(0.98f, 0.86f, 0.78f)),
    SetPaletteSlot(HAIR_BROWN, SlotColor(0.45f, 0.27f, 0.15f)),
    SetPaletteSlot(BROW, SlotColor(0.30f, 0.18f, 0.10f)),
    SetPaletteSlot(LIPS, SlotColor(0.83f, 0.47f, 0.48f)),
    SetPaletteSlot(MOUTH_INSIDE, SlotColor(0.45f, 0.17f, 0.20f)),
    SetPaletteSlot(BLUSH, SlotColor(0.97f, 0.74f, 0.70f)),
    SetPaletteSlot(HAZEL, SlotColor(0.52f, 0.47f, 0.22f)),
    SetPaletteSlot(EYE_WHITE, SlotColor(0.99f, 0.97f, 0.95f)),
    SetPaletteSlot(NAILS, SlotColor(0.88f, 0.56f, 0.55f)),
) + SKIN_PARTS.map { SetBaseSlot(it, SKIN) } + HAIR_PARTS.map { SetBaseSlot(it, HAIR_BROWN) }
