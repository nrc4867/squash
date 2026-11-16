package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.Paint
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import kotlin.math.PI
import kotlin.math.sin

val SAUSAGE = GarmentId("sausage")
val BUN = GarmentId("bun")
val SAUSAGE_SKIN = SlotName("sausage")
val BUN_CRUST = SlotName("bun")
val MUSTARD = SlotName("mustard")

/** The sausage wraps the whole body from a round end at the rump, inside the bun's, up to the base of the neck. */
private const val SAUSAGE_FROM = -0.06f
private const val SAUSAGE_TO = 0.98f
private const val SAUSAGE_EASE = 0.04f

/**
 * The bun cradles the sausage from under the belly up both flanks, open over the back, from behind the rump to the chest, where it
 * ends open, soft and thick: its halves' rims run along the sausage's flanks, well below its top, so the sausage shows all along it.
 */
private const val BUN_FROM = -0.09f
private const val BUN_TO = 1.02f
private const val BUN_ROUND_FROM = 0.22f
private const val BUN_ROUND_TO = 0.78f
private const val BUN_EASE = 0.06f
private const val BUN_LAYER = 2

/** The sausage from the side: a plump log with round ends. */
private val SAUSAGE_SIDE = loop(
    p(-0.06f, 0.04f), p(-0.03f, 0.18f), p(0.06f, 0.28f), p(0.20f, 0.31f), p(0.50f, 0.31f), p(0.76f, 0.32f), p(0.86f, 0.28f),
    p(0.87f, -0.10f), p(0.76f, -0.22f), p(0.50f, -0.25f), p(0.14f, -0.23f), p(0.02f, -0.17f), p(-0.04f, -0.08f),
)

/** The sausage from above: as round across as it is tall, its back end round. */
private val SAUSAGE_TOP = loop(
    p(-0.06f, 0f), p(-0.04f, 0.13f), p(0.04f, 0.22f), p(0.20f, 0.25f), p(0.50f, 0.24f), p(0.76f, 0.24f), p(0.88f, 0.15f), p(0.90f, 0f),
    p(0.88f, -0.15f), p(0.76f, -0.24f), p(0.50f, -0.24f), p(0.20f, -0.25f), p(0.04f, -0.22f), p(-0.04f, -0.13f),
)

/** A round section, so the sausage is a log, not the body's egg. */
private val ROUND = loop(
    p(0f, 1f), p(0.71f, 0.71f), p(1f, 0f), p(0.71f, -0.71f), p(0f, -1f), p(-0.71f, -0.71f), p(-1f, 0f), p(-0.71f, 0.71f),
)

/** The bun from the side: a long soft loaf, its back end round over the sausage's, deep under the belly. */
private val BUN_SIDE = loop(
    p(-0.08f, -0.04f), p(-0.04f, 0.12f), p(0.10f, 0.22f), p(0.45f, 0.24f), p(0.80f, 0.26f), p(1.00f, 0.24f),
    p(1.00f, -0.27f), p(0.80f, -0.31f), p(0.40f, -0.32f), p(0.10f, -0.30f), p(-0.05f, -0.22f),
)

/** The bun from above: a little wider than the sausage, closing in toward the chest so its halves hug the flanks there, round at both ends. */
private val BUN_TOP = loop(
    p(-0.08f, 0f), p(-0.05f, 0.18f), p(0.08f, 0.29f), p(0.30f, 0.32f), p(0.60f, 0.31f), p(0.80f, 0.27f), p(1.00f, 0.25f),
    p(1.00f, -0.25f), p(0.80f, -0.27f), p(0.60f, -0.31f), p(0.30f, -0.32f), p(0.08f, -0.29f), p(-0.05f, -0.18f),
)

/** The bun's section: full and round, a little flattened underneath, so each half reads as a puffy roll. */
private val BUN_FRONT = loop(
    p(0f, 1f), p(0.75f, 0.80f), p(1f, 0.10f), p(0.85f, -0.62f), p(0.45f, -0.95f), p(0f, -1f), p(-0.45f, -0.95f), p(-0.85f, -0.62f),
    p(-1f, 0.10f), p(-0.75f, 0.80f),
)

/** The mustard: a zigzag painted along the top of the sausage from the rump to the shoulders. */
private const val MUSTARD_FROM = 0.02f
private const val MUSTARD_TO = 0.86f
private const val MUSTARD_TURNS = 9
private const val MUSTARD_REACH = 0.075f
private const val MUSTARD_RADIUS = 0.022f
private const val MUSTARD_POINTS_PER_TURN = 6

private fun onSausage(u: Float, v: Float) = Anchor(CAT_TORSO, BODY_PATCH, u, v)

/** The squiggle's stroke: a smooth zigzag either side of the spine, [MUSTARD_TURNS] swings from rump to shoulders. */
private fun mustard(): BrushStroke {
    val count = MUSTARD_TURNS * MUSTARD_POINTS_PER_TURN + 1
    val points = List(count) { i ->
        val t = i.toFloat() / (count - 1)
        val swing = sin(t * MUSTARD_TURNS * PI.toFloat())
        onSausage(MUSTARD_FROM + (MUSTARD_TO - MUSTARD_FROM) * t, MUSTARD_REACH * swing)
    }
    return BrushStroke(BrushStrokeId("mustard"), points, MUSTARD, MUSTARD_RADIUS)
}

/** The hotdog costume: a sausage over the body with a squiggle of mustard painted along its top, and a puffy bun round it open over the back. */
fun hotdog(): List<DocumentCommand> = listOf(
    SetPaletteSlot(SAUSAGE_SKIN, SlotColor(0.80f, 0.32f, 0.22f)),
    SetPaletteSlot(BUN_CRUST, SlotColor(0.90f, 0.66f, 0.36f)),
    SetPaletteSlot(MUSTARD, SlotColor(0.98f, 0.82f, 0.12f)),
    AddGarment(Garment(SAUSAGE, "Sausage", BODY_SLOT)),
    SetGarmentColor(SAUSAGE, SAUSAGE_SKIN),
    CoverPart(
        SAUSAGE,
        GarmentBranch(
            CAT_TORSO, SAUSAGE_FROM, SAUSAGE_TO, ease = SAUSAGE_EASE,
            outlines = mapOf(View.SIDE to SAUSAGE_SIDE, View.TOP to SAUSAGE_TOP, View.FRONT to ROUND),
            surface = SurfaceWork(paint = Paint(listOf(mustard()))),
        ),
    ),
    SetBodyPartChange(SAUSAGE, CAT_TAIL, BodyPartChange.Through(cuff = false)),
    AddGarment(Garment(BUN, "Bun", OUTER_SLOT, layer = BUN_LAYER)),
    SetGarmentColor(BUN, BUN_CRUST),
    CoverPart(
        BUN,
        GarmentBranch(
            CAT_TORSO, BUN_FROM, BUN_TO, ease = BUN_EASE,
            outlines = mapOf(View.SIDE to BUN_SIDE, View.TOP to BUN_TOP, View.FRONT to BUN_FRONT),
            fromV = BUN_ROUND_FROM, toV = BUN_ROUND_TO,
        ),
    ),
)
