package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.fennec.ref

val SAUSAGE = GarmentId("sausage")
val BUN = GarmentId("bun")
val SAUSAGE_SKIN = SlotName("sausage")
val BUN_CRUST = SlotName("bun")
val MUSTARD = SlotName("mustard")

/** The sausage wraps the whole body, rump to the base of the neck. */
private const val SAUSAGE_FROM = 0f
private const val SAUSAGE_TO = 0.98f
private const val SAUSAGE_EASE = 0.04f

/** The bun cradles the sausage from under the belly up both flanks, open over the back, from the rump to the chest, thick and soft. */
private const val BUN_FROM = 0.04f
private const val BUN_TO = 0.92f
private const val BUN_ROUND_FROM = 0.19f
private const val BUN_ROUND_TO = 0.81f
private const val BUN_EASE = 0.10f
private const val BUN_LAYER = 2

/** The sausage from the side: a plump round-ended log over the body. */
private val SAUSAGE_SIDE = loop(
    p(-0.07f, 0.04f), p(-0.02f, 0.20f), p(0.14f, 0.28f), p(0.50f, 0.29f), p(0.76f, 0.31f), p(0.86f, 0.27f),
    p(0.86f, -0.10f), p(0.76f, -0.21f), p(0.50f, -0.24f), p(0.14f, -0.22f), p(-0.02f, -0.12f),
)

/** The torso's sockets for the mustard's pieces, one a piece, numbered from [MUSTARD_SOCKET] along the back. */
private const val MUSTARD_SOCKET = 1
private const val MUSTARD_PIECES = 7
private const val MUSTARD_FROM = 0.16f
private const val MUSTARD_STEP = 0.105f
private const val SPINE = 0f

/** How far each piece turns off the spine, alternately one way and the other, so the pieces run as a zigzag. */
private const val MUSTARD_TURN = 38f

/** A piece of the squiggle in Side, front up: a short round bead standing on top of the sausage, whose drawn top lies about 0.08 over the back. */
private val MUSTARD_SIDE = loop(p(0f, 0.088f), p(0.03f, 0.104f), p(0.10f, 0.104f), p(0.13f, 0.088f), p(0.10f, 0.074f), p(0.03f, 0.074f))

/** A piece across: an even ribbon with round ends. */
private val MUSTARD_TOP = loop(p(0f, 0f), p(0.015f, 0.026f), p(0.115f, 0.026f), p(0.13f, 0f), p(0.115f, -0.026f), p(0.015f, -0.026f))

private const val MUSTARD_RINGS = 12
private const val MUSTARD_SEGMENTS = 10
private const val UNSCALED = 1f

/** The mustard's pieces, each a part the sausage owns, riding the back. */
val MUSTARD_PARTS: List<PartId> = List(MUSTARD_PIECES) { PartId("mustard-$it") }

/** The mustard: a zigzag of short beads along the top of the sausage from the rump to the shoulders, each turned off the spine the other way from the last. */
private fun mustard(): List<DocumentCommand> = MUSTARD_PARTS.flatMapIndexed { i, piece ->
    val socket = MUSTARD_SOCKET + i
    listOf(
        HandSockets.add(CAT_TORSO, socket, MUSTARD_FROM + MUSTARD_STEP * i, SPINE, 0f, paired = false, sink = 0f),
        owned(SAUSAGE, piece, SocketRef(CAT_TORSO, HandSockets.name(socket))),
        RestDirection.restIn(piece, RestDirection.FORWARD),
        SetParameter(piece, PlacementParams.OUTWARD, if (i % 2 == 0) MUSTARD_TURN else -MUSTARD_TURN),
        SetOutlines(piece, mapOf(View.SIDE to MUSTARD_SIDE, View.TOP to MUSTARD_TOP)),
        SetParameter(piece, "size", UNSCALED),
        GenericParams.rings(piece, MUSTARD_RINGS),
        GenericParams.segments(piece, MUSTARD_SEGMENTS),
        SetBaseSlot(piece, MUSTARD),
    )
}

/** A part [garment] owns, a generic blob on [socket]: built only while the garment is worn, riding the bone it sits on. */
private fun owned(garment: GarmentId, id: PartId, socket: SocketRef): DocumentCommand =
    AddParts(listOf(PartInstance(id, ref("blob"), socket, role = Role.OTHER, garment = garment)))

/** The hotdog costume: a sausage over the body with a zigzag of mustard along its top, and a bun round it open over the back. */
fun hotdog(): List<DocumentCommand> = listOf(
    SetPaletteSlot(SAUSAGE_SKIN, SlotColor(0.80f, 0.32f, 0.22f)),
    SetPaletteSlot(BUN_CRUST, SlotColor(0.90f, 0.66f, 0.36f)),
    SetPaletteSlot(MUSTARD, SlotColor(0.98f, 0.82f, 0.12f)),
    AddGarment(Garment(SAUSAGE, "Sausage", BODY_SLOT)),
    SetGarmentColor(SAUSAGE, SAUSAGE_SKIN),
    CoverPart(
        SAUSAGE,
        GarmentBranch(
            CAT_TORSO, SAUSAGE_FROM, SAUSAGE_TO, ease = SAUSAGE_EASE, outlines = mapOf(View.SIDE to SAUSAGE_SIDE),
        ),
    ),
) + mustard() + listOf(
    AddGarment(Garment(BUN, "Bun", OUTER_SLOT, layer = BUN_LAYER)),
    SetGarmentColor(BUN, BUN_CRUST),
    CoverPart(
        BUN,
        GarmentBranch(CAT_TORSO, BUN_FROM, BUN_TO, ease = BUN_EASE, fromV = BUN_ROUND_FROM, toV = BUN_ROUND_TO),
    ),
)
