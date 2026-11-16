package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.WidthStop

val CAPE = GarmentId("cape")
val FANGS = GarmentId("fangs")
val CAPE_CLOTH = SlotName("cape")

/** The cape runs the torso's whole length, from the rump to the base of the neck, where the head comes up through its collar. */
private const val CAPE_FROM = 0f
private const val CAPE_TO = 1f

/** Round the body it covers the back and both flanks down to the legs' roots, open under the belly and down the chest. */
private const val CAPE_ROUND = 0.40f

/** The cape stands well off the body, as heavy cloth over the shoulders does. */
private const val CAPE_EASE = 0.07f
private const val CAPE_LAYER = 2

/** The collar the head comes up through: a tall stiff band round the neck. */
private const val COLLAR = 0.35f

/** A fang: a small inked point hanging from the mouth's curve on the side [v] reads, the left as drawn and the right mirrored. */
private fun fang(id: String, v: (Float) -> Float) = Mark(
    MarkId(id),
    listOf(Anchor(CAT_HEAD, MUZZLE, 0.968f, v(0.235f)), Anchor(CAT_HEAD, MUZZLE, 0.957f, v(0.31f)), Anchor(CAT_HEAD, MUZZLE, 0.946f, v(0.245f))),
    MarkWidth(listOf(WidthStop(0f, 0.7f), WidthStop(1f, 0.7f))),
)

/** The vampire's cape with its collar, and fangs inked under the mouth by a garment that redraws only the head's surface. */
fun vampire(): List<DocumentCommand> = listOf(
    SetPaletteSlot(CAPE_CLOTH, SlotColor(0.16f, 0.12f, 0.20f)),
    AddGarment(Garment(CAPE, "Cape", OUTER_SLOT, layer = CAPE_LAYER)),
    SetGarmentColor(CAPE, CAPE_CLOTH),
    CoverPart(
        CAPE,
        GarmentBranch(CAT_TORSO, CAPE_FROM, CAPE_TO, ease = CAPE_EASE, fromV = -CAPE_ROUND, toV = CAPE_ROUND),
    ),
    SetBodyPartChange(CAPE, CAT_HEAD, BodyPartChange.Through(cuffSize = COLLAR)),
    AddGarment(Garment(FANGS, "Fangs", FACE_SLOT)),
    SetBodyPartChange(
        FANGS, CAT_HEAD,
        BodyPartChange.Redraw(PartData(surface = SurfaceWork(marks = listOf(fang("fang-l") { it }, fang("fang-r") { 1f - it })))),
    ),
)
