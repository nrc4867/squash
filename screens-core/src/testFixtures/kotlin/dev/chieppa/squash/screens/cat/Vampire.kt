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
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val CAPE = GarmentId("cape")
val FANGS = GarmentId("fangs")
val CAPE_CLOTH = SlotName("cape")

/** The cape runs from past the rump, where it trails, to the base of the neck, where the head comes up through its collar. */
private const val CAPE_FROM = -0.41f
private const val CAPE_TO = 1f

/** Round the body it covers the back and both flanks, hanging well down them, open under the belly and down the chest. */
private const val CAPE_ROUND = 0.40f

/** Under its drawing, the cape stands off the body as heavy cloth over the shoulders does. */
private const val CAPE_EASE = 0.05f
private const val CAPE_LAYER = 2

/** The collar the head comes up through: a tall stiff band round the neck. */
private const val COLLAR = 0.35f

/**
 * The cape from the side, rump to shoulders, back up: high and close over the shoulders, its hem falling away toward the rump and
 * trailing past it to a long point behind, well clear of the hocks.
 */
private val CAPE_SIDE = loop(
    p(-0.40f, -0.27f), p(-0.30f, -0.08f), p(-0.14f, 0.12f), p(0.02f, 0.25f), p(0.20f, 0.31f), p(0.50f, 0.30f), p(0.74f, 0.35f),
    p(0.90f, 0.36f), p(0.95f, 0.20f), p(0.92f, -0.12f), p(0.70f, -0.25f), p(0.40f, -0.30f), p(0.10f, -0.33f), p(-0.15f, -0.34f),
    p(-0.34f, -0.35f),
)

/** The cape from above: snug across the shoulders and flaring wide over the hips and the trailing tail of cloth. */
private val CAPE_TOP = loop(
    p(-0.40f, 0f), p(-0.36f, 0.14f), p(-0.20f, 0.27f), p(0.00f, 0.31f), p(0.20f, 0.30f), p(0.50f, 0.27f), p(0.78f, 0.24f),
    p(0.95f, 0.16f), p(0.97f, 0f), p(0.95f, -0.16f), p(0.78f, -0.24f), p(0.50f, -0.27f), p(0.20f, -0.30f), p(0.00f, -0.31f),
    p(-0.20f, -0.27f), p(-0.36f, -0.14f),
)

/** The cape's section: a round crown over the back falling to flanks that flare out toward the hem, as cloth hangs. */
private val CAPE_FRONT = loop(
    p(0f, 1f), p(0.55f, 0.88f), p(0.80f, 0.55f), p(0.90f, 0f), p(1f, -0.60f), p(0.85f, -1f), p(0f, -0.92f), p(-0.85f, -1f), p(-1f, -0.60f),
    p(-0.90f, 0f), p(-0.80f, 0.55f), p(-0.55f, 0.88f),
)

/** A fang: a small inked point hanging from the mouth's curve on the side [v] reads, the left as drawn and the right mirrored. */
private fun fang(id: String, v: (Float) -> Float) = Mark(
    MarkId(id),
    listOf(Anchor(CAT_HEAD, MUZZLE, 0.968f, v(0.235f)), Anchor(CAT_HEAD, MUZZLE, 0.957f, v(0.31f)), Anchor(CAT_HEAD, MUZZLE, 0.946f, v(0.245f))),
    MarkWidth(listOf(WidthStop(0f, 0.7f), WidthStop(1f, 0.7f))),
)

/** The vampire's cape with its collar, the tail through a slit in it, and fangs inked under the mouth by a garment that redraws only the head's surface. */
fun vampire(): List<DocumentCommand> = listOf(
    SetPaletteSlot(CAPE_CLOTH, SlotColor(0.16f, 0.12f, 0.20f)),
    AddGarment(Garment(CAPE, "Cape", OUTER_SLOT, layer = CAPE_LAYER, fit = CAPE_EASE)),
    SetGarmentColor(CAPE, CAPE_CLOTH),
    CoverPart(
        CAPE,
        GarmentBranch(
            CAT_TORSO, CAPE_FROM, CAPE_TO,
            outlines = mapOf(View.SIDE to CAPE_SIDE, View.TOP to CAPE_TOP, View.FRONT to CAPE_FRONT),
            fromV = -CAPE_ROUND, toV = CAPE_ROUND,
        ),
    ),
    SetBodyPartChange(CAPE, CAT_HEAD, BodyPartChange.Through(cuffSize = COLLAR)),
    SetBodyPartChange(CAPE, CAT_TAIL, BodyPartChange.Through(cuff = false)),
    AddGarment(Garment(FANGS, "Fangs", FACE_SLOT)),
    SetBodyPartChange(
        FANGS, CAT_HEAD,
        BodyPartChange.Redraw(PartData(surface = SurfaceWork(marks = listOf(fang("fang-l") { it }, fang("fang-r") { 1f - it })))),
    ),
)
