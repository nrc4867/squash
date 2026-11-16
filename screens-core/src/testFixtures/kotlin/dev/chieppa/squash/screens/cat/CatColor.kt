package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName

val GINGER = SlotName("ginger")
val STRIPE = SlotName("stripe")
/** The white of the chest, the muzzle and the paws. */
val WHITE = SlotName("white")
val PAD_PINK = SlotName("pad")
val CLAW_SLOT = SlotName("claw")
val NOSE_PINK = SlotName("nose")
val EAR_PINK = SlotName("inner-ear")
val CAT_INK = SlotName("ink")
val GREEN = SlotName("iris")

/** The head's muzzle patch, which the head template names for the muzzle's own coordinates. */
const val MUZZLE = "muzzle"

private val GINGER_PARTS = listOf(CAT_TORSO, CAT_HEAD, CAT_TAIL, CAT_EAR_L, CAT_FRONT_LEG_L, CAT_BACK_LEG_L)
private val WHITE_PARTS = listOf(CAT_FRONT_PAW_L, CAT_BACK_PAW_L)

private fun region(part: PartId, id: String, slot: SlotName, patch: String = BODY_PATCH, uv: List<Pair<Float, Float>>) =
    SetRegion(part, RegionId(id), Region(RegionId(id), uv.map { (u, v) -> Anchor(part, patch, u, v) }, slot))

/**
 * A stripe across the back at [u], [width] wide where it crosses the spine, tapering to a point [reach] down each flank: two halves, one
 * either side of the spine's v = 0.
 */
private fun backStripe(part: PartId, id: String, u: Float, width: Float, reach: Float, lean: Float = 0f): List<DocumentCommand> {
    val half = width / 2f
    fun side(name: String, toward: (Float) -> Float) = region(
        part, "$id-$name", STRIPE,
        uv = listOf(
            u - half to toward(0f), u - half * 0.8f + lean * 0.5f to toward(reach * 0.5f), u + lean to toward(reach),
            u + half * 0.8f + lean * 0.5f to toward(reach * 0.5f), u + half to toward(0f),
        ),
    )
    return listOf(side("l") { it }, side("r") { 1f - it })
}

/** A ring round the tail at [u], [width] long. */
private fun tailRing(index: Int, u: Float, width: Float) = region(
    CAT_TAIL, "ring-$index", STRIPE,
    uv = List(16) { i -> (if (i < 8) u - width / 2f else u + width / 2f) to (if (i < 8) i / 7f else (15 - i) / 7f) },
)

/** The palette, ginger over the body and white paws, a white chest and belly, muzzle and chin, darker ginger stripes and tail rings. */
fun catColor(): List<DocumentCommand> = listOf(
    SetPaletteSlot(GINGER, SlotColor(0.94f, 0.60f, 0.28f)),
    SetPaletteSlot(STRIPE, SlotColor(0.80f, 0.40f, 0.15f)),
    SetPaletteSlot(WHITE, SlotColor(0.99f, 0.96f, 0.89f)),
    SetPaletteSlot(PAD_PINK, SlotColor(0.93f, 0.60f, 0.64f)),
    SetPaletteSlot(CLAW_SLOT, SlotColor(0.85f, 0.80f, 0.74f)),
    SetPaletteSlot(NOSE_PINK, SlotColor(0.90f, 0.50f, 0.56f)),
    SetPaletteSlot(EAR_PINK, SlotColor(0.97f, 0.73f, 0.72f)),
    SetPaletteSlot(CAT_INK, SlotColor(0.13f, 0.09f, 0.10f)),
    SetPaletteSlot(GREEN, SlotColor(0.46f, 0.70f, 0.22f)),
) + GINGER_PARTS.map { SetBaseSlot(it, GINGER) } + WHITE_PARTS.map { SetBaseSlot(it, WHITE) } + listOf(
    region(
        CAT_TORSO, "chest", WHITE, uv = listOf(
            0.99f to 0.16f, 0.88f to 0.24f, 0.70f to 0.34f, 0.50f to 0.38f, 0.30f to 0.40f, 0.20f to 0.44f, 0.18f to 0.50f, 0.20f to 0.56f,
            0.30f to 0.60f, 0.50f to 0.62f, 0.70f to 0.66f, 0.88f to 0.76f, 0.99f to 0.84f,
        ),
    ),
    region(
        CAT_HEAD, "muzzle", WHITE, MUZZLE, uv = listOf(
            0.45f to 0.36f, 0.65f to 0.22f, 0.85f to 0.10f, 1.00f to 0.06f, 1.00f to 0.94f, 0.85f to 0.90f, 0.65f to 0.78f, 0.45f to 0.64f,
        ),
    ),
    region(
        CAT_HEAD, "chin", WHITE, uv = listOf(
            0.70f to 0.30f, 0.50f to 0.36f, 0.30f to 0.42f, 0.22f to 0.50f, 0.30f to 0.58f, 0.50f to 0.64f, 0.70f to 0.70f,
        ),
    ),
    region(
        CAT_EAR_L, "inner", EAR_PINK, uv = listOf(
            0.08f to 0.80f, 0.40f to 0.83f, 0.70f to 0.92f, 0.86f to 0.00f, 0.70f to 0.08f, 0.40f to 0.17f, 0.08f to 0.20f,
        ),
    ),
) + backStripe(CAT_TORSO, "stripe-1", 0.20f, 0.07f, 0.24f, lean = -0.04f) +
    backStripe(CAT_TORSO, "stripe-2", 0.35f, 0.07f, 0.26f, lean = -0.03f) +
    backStripe(CAT_TORSO, "stripe-3", 0.50f, 0.07f, 0.25f) +
    backStripe(CAT_TORSO, "stripe-4", 0.65f, 0.06f, 0.20f, lean = 0.03f) +
    backStripe(CAT_HEAD, "brow-1", 0.30f, 0.05f, 0.05f, lean = 0.06f) +
    backStripe(CAT_HEAD, "brow-2", 0.18f, 0.05f, 0.08f, lean = 0.04f) +
    listOf(tailRing(1, 0.30f, 0.06f), tailRing(2, 0.50f, 0.06f), tailRing(3, 0.70f, 0.06f), tailRing(4, 0.88f, 0.10f))
