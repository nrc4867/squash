package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName

val SKIN = SlotName("skin")
val HAIR_BROWN = SlotName("hair")
val LIPS = SlotName("lips")
val BLUSH = SlotName("blush")
val LID = SlotName("lid")
val IRIS = SlotName("iris")
val EYE_WHITE = SlotName("eye-white")
val BROW = SlotName("brow")
val PUPIL = SlotName("pupil")
val INK = SlotName("ink")

/** The slot the hand's nails show, as its template names it. */
val NAILS = SlotName("nails")

/** The reference's colours as 24-bit sRGB numbers. */
internal const val SKIN_HEX = 0xF7D5C1
private const val HAIR_HEX = 0x7B4A2B
private const val BROW_HEX = 0x5B3420
private const val IRIS_HEX = 0x8B8A3E
private const val WHITE_HEX = 0xFBF8F3
private const val PUPIL_HEX = 0x1C120E
internal const val BLUSH_HEX = 0xF0A49A

/** The reference's beach nail colour, worn bare. */
private const val NAIL_HEX = 0xF08A7A

/** The natural look's lips, eyeshadow and its strength, and the blush's strength, which the beach outfit and the bare twin wear. */
internal val NATURAL = FaceLook(lips = 0xD9837C, shadow = 0xD9A08E, shadowAmount = 0.35f, blushAmount = 0.45f)

/** A face look: the lip colour, the eyeshadow and how much of it shows over the skin, and how much blush. */
internal data class FaceLook(val lips: Int, val shadow: Int, val shadowAmount: Float, val blushAmount: Float) {
    val lidColor: SlotColor get() = mix(SKIN_HEX, shadow, shadowAmount)
    val blushColor: SlotColor get() = mix(SKIN_HEX, BLUSH_HEX, blushAmount)
    val lipColor: SlotColor get() = color(lips)
}

/** The colour [hex], a 24-bit sRGB number. */
internal fun color(hex: Int) = SlotColor(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f)

/** [from] carried [amount] of the way to [to]. */
internal fun mix(from: Int, to: Int, amount: Float): SlotColor {
    val a = color(from)
    val b = color(to)
    return SlotColor(a.red + (b.red - a.red) * amount, a.green + (b.green - a.green) * amount, a.blue + (b.blue - a.blue) * amount)
}

private val SKIN_PARTS = listOf(TORSO, NECK, HEAD, ARM_L, HAND_L, LEG_L, FOOT_L)
private val HAIR_PARTS = listOf(HAIR, HAIR_FALL, LOCK_L)

/** Her palette in the reference's colours, the face in its natural look; and a base slot for every part. */
fun twinColor(): List<DocumentCommand> = listOf(
    SetPaletteSlot(SKIN, color(SKIN_HEX)),
    SetPaletteSlot(HAIR_BROWN, color(HAIR_HEX)),
    SetPaletteSlot(BROW, color(BROW_HEX)),
    SetPaletteSlot(LIPS, NATURAL.lipColor),
    SetPaletteSlot(BLUSH, NATURAL.blushColor),
    SetPaletteSlot(LID, NATURAL.lidColor),
    SetPaletteSlot(IRIS, color(IRIS_HEX)),
    SetPaletteSlot(EYE_WHITE, color(WHITE_HEX)),
    SetPaletteSlot(PUPIL, color(PUPIL_HEX)),
    SetPaletteSlot(NAILS, color(NAIL_HEX)),
) + SKIN_PARTS.map { SetBaseSlot(it, SKIN) } + HAIR_PARTS.map { SetBaseSlot(it, HAIR_BROWN) }
