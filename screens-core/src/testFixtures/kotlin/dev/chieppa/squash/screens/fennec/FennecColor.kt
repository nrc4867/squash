package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName

val FUR = SlotName("fur")
val CREAM = SlotName("cream")
val PAD = SlotName("pad")
val NOSE = SlotName("nose")
val INNER_EAR = SlotName("inner-ear")
val INK = SlotName("ink")
val IRIS = SlotName("iris")

const val MUZZLE_PATCH = "muzzle"

private val FUR_PARTS = listOf(TORSO, HEAD, TAIL, EAR_L, FRONT_LEG_L, BACK_LEG_L, FRONT_PAW_L, BACK_PAW_L)

private fun region(part: PartId, id: String, slot: SlotName, patch: String = "body", vararg uv: Pair<Float, Float>) =
    SetRegion(part, RegionId(id), Region(RegionId(id), uv.map { (u, v) -> Anchor(part, patch, u, v) }, slot))

/** The palette, a base slot per part, and the areas in cream, inner ear and ink, each a region drawn on the surface. */
fun fennecColor(): List<DocumentCommand> = listOf(
    SetPaletteSlot(FUR, SlotColor(0.90f, 0.70f, 0.43f)),
    SetPaletteSlot(CREAM, SlotColor(0.99f, 0.94f, 0.82f)),
    SetPaletteSlot(PAD, SlotColor(0.36f, 0.24f, 0.24f)),
    SetPaletteSlot(NOSE, SlotColor(0.16f, 0.11f, 0.12f)),
    SetPaletteSlot(INNER_EAR, SlotColor(0.96f, 0.76f, 0.68f)),
    SetPaletteSlot(INK, SlotColor(0.13f, 0.09f, 0.10f)),
    SetPaletteSlot(IRIS, SlotColor(0.42f, 0.25f, 0.12f)),
) + FUR_PARTS.map { SetBaseSlot(it, FUR) } + listOf(
    region(
        TORSO, "chest", CREAM, uv = arrayOf(
            0.97f to 0.20f, 0.86f to 0.24f, 0.74f to 0.33f, 0.68f to 0.42f, 0.66f to 0.50f, 0.68f to 0.58f, 0.74f to 0.67f, 0.86f to 0.76f, 0.97f to 0.80f,
        ),
    ),
    region(
        HEAD, "muzzle", CREAM, MUZZLE_PATCH, uv = arrayOf(
            0.62f to 0.31f, 0.74f to 0.235f, 0.86f to 0.20f, 0.97f to 0.24f, 0.99f to 0.50f, 0.97f to 0.76f, 0.86f to 0.80f, 0.74f to 0.765f, 0.62f to 0.69f,
        ),
    ),
    region(
        HEAD, "cheeks", CREAM, uv = arrayOf(
            0.70f to 0.27f, 0.54f to 0.31f, 0.36f to 0.36f, 0.24f to 0.44f, 0.22f to 0.50f, 0.24f to 0.56f, 0.36f to 0.64f, 0.54f to 0.69f, 0.70f to 0.73f,
        ),
    ),
    region(
        EAR_L, "inner", INNER_EAR, uv = arrayOf(
            0.10f to 0.82f, 0.45f to 0.84f, 0.74f to 0.91f, 0.88f to 0.00f, 0.74f to 0.09f, 0.45f to 0.16f, 0.10f to 0.18f,
        ),
    ),
    region(TAIL, "tip", INK, uv = Array(16) { i -> 0.80f + (if (i % 2 == 0) 0.015f else -0.015f) to i / 16f }),
)
