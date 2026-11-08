package dev.chieppa.squash.screens.dragon

import dev.chieppa.squash.character.block.DigitTips
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.drawn
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.fennec.pair
import dev.chieppa.squash.screens.fennec.ref

val TORSO = PartId("torso")
val HEAD = PartId("head")
val TAIL = PartId("tail")
val EAR_L = PartId("ear-l")
val EAR_R = PartId("ear-r")
val FRONT_LEG_L = PartId("front-leg-l")
val FRONT_LEG_R = PartId("front-leg-r")
val BACK_LEG_L = PartId("back-leg-l")
val BACK_LEG_R = PartId("back-leg-r")
val FRONT_PAW_L = PartId("front-paw-l")
val FRONT_PAW_R = PartId("front-paw-r")
val BACK_PAW_L = PartId("back-paw-l")
val BACK_PAW_R = PartId("back-paw-r")
val WING_L = PartId("wing-l")
val WING_R = PartId("wing-r")
val HORN_L = PartId("horn-l")
val HORN_R = PartId("horn-r")

/** The hand-made socket on the torso's shoulders the wings sit on. */
const val WING_SOCKET = 1

private const val SHOULDER_U = 0.74f
private const val SHOULDER_V = 0.12f

/** The hand-made rows of spikes along the torso's back, from the shoulders to the hips, and along the tail's top. */
const val BACK_ROW = 2
const val TAIL_ROW = 1

/** Each row: where along its part it starts and ends, how many spikes it holds, the first spike's size and the last's as a share of it. */
private class SpikeRow(val part: PartId, val row: Int, val from: Float, val to: Float, val count: Int, val size: Float, val falloff: Float)

private val SPIKE_ROWS = listOf(
    SpikeRow(TORSO, BACK_ROW, 0.84f, 0.1f, 7, 1f, 0.6f),
    SpikeRow(TAIL, TAIL_ROW, 0.06f, 0.82f, 6, 0.6f, 0.35f),
)

/** The spikes of [row]: a hand-made row on its part's top, filled with spikes in one step, each at the size its socket gives, as Add part fills it. */
private fun spikes(row: SpikeRow): List<DocumentCommand> {
    val placed = HandSockets.addRow(row.part, row.row, row.from, row.to, 0f, 0f, false, row.count, row.size, row.falloff)
    val sockets = HandSockets.specs(placed.values.mapNotNull { (name, value) -> value?.let { name to it } }.toMap())
    return listOf(
        placed,
        AddParts(
            sockets.mapIndexed { i, socket ->
                PartInstance(spikeOf(row.part, i), ref("spike"), SocketRef(row.part, socket.name), own = PartData(params = mapOf(SIZE_PARAM to socket.scale)), role = Role.HORN)
            },
        ),
    )
}

/** The spike on socket [index] of [part]'s row. */
fun spikeOf(part: PartId, index: Int) = PartId("${part.value}-spike-$index")

/** How many toes each paw has. */
const val PAW_TOES = 3

/** The claw on the tip of toe [index] of [paw]. */
fun clawOf(paw: PartId, index: Int) = PartId("${paw.value}-claw-$index")

/** The claws of a pair of paws: one on every toe's tip socket of each side, placed in one step, as Add part fills the tips. */
private fun claws(id: String, left: PartId, right: PartId) = AddParts(
    (0 until PAW_TOES).flatMap { i ->
        val tip = DigitTips.name("toes:$i")
        listOf(
            PartInstance(clawOf(left, i), ref("claw"), SocketRef(left, tip), pair = PairId("$id-$i"), side = Side.LEFT, role = Role.HORN),
            PartInstance(clawOf(right, i), ref("claw"), SocketRef(right, tip), pair = PairId("$id-$i"), side = Side.RIGHT, role = Role.HORN),
        )
    },
)

/** The paired hand-made socket on the head's crown the horns sit on, behind and inside the ears. */
const val HORN_SOCKET = 1
private const val CROWN_U = 0.16f
private const val CROWN_V = 0.06f

/** How far the wings lift toward the body's up from their template's rest, out to the sides. */
private const val WINGS_RAISED = 24f

private const val WING_SIZE = 1.5f
private const val TAIL_LEAN = -45f
private const val EAR_OUTWARD = 30f

private val TORSO_SIDE = loop(
    p(0.00f, 0.02f), p(0.06f, 0.12f), p(0.22f, 0.16f), p(0.42f, 0.15f), p(0.62f, 0.17f), p(0.78f, 0.22f), p(0.90f, 0.24f),
    p(0.96f, 0.16f), p(0.95f, 0.02f), p(0.84f, -0.12f), p(0.64f, -0.16f), p(0.42f, -0.14f), p(0.22f, -0.15f), p(0.06f, -0.10f),
)
private val TORSO_TOP = loop(
    p(0.00f, 0.00f), p(0.07f, 0.11f), p(0.24f, 0.15f), p(0.46f, 0.13f), p(0.70f, 0.16f), p(0.88f, 0.12f), p(0.96f, 0.00f),
    p(0.88f, -0.12f), p(0.70f, -0.16f), p(0.46f, -0.13f), p(0.24f, -0.15f), p(0.07f, -0.11f),
)

private val HEAD_SIDE = loop(
    p(0.00f, 0.02f), p(0.06f, 0.15f), p(0.20f, 0.20f), p(0.34f, 0.17f), p(0.48f, 0.10f), p(0.66f, 0.07f), p(0.80f, 0.05f),
    p(0.83f, -0.01f), p(0.76f, -0.06f), p(0.56f, -0.08f), p(0.36f, -0.11f), p(0.18f, -0.15f), p(0.05f, -0.11f),
)
private val HEAD_TOP = loop(
    p(0.00f, 0.00f), p(0.06f, 0.15f), p(0.22f, 0.21f), p(0.38f, 0.16f), p(0.54f, 0.09f), p(0.74f, 0.07f), p(0.83f, 0.00f),
    p(0.74f, -0.07f), p(0.54f, -0.09f), p(0.38f, -0.16f), p(0.22f, -0.21f), p(0.06f, -0.15f),
)

private val EAR_SIDE = drawn(p(0f, 0.025f), p(0.10f, 0.025f), p(0.18f, 0.012f), p(0.24f, 0f), p(0.18f, -0.012f), p(0.10f, -0.025f), p(0f, -0.025f))
private val EAR_TOP = drawn(p(0f, 0.06f), p(0.08f, 0.06f), p(0.18f, 0.03f), p(0.24f, 0f), p(0.18f, -0.03f), p(0.08f, -0.06f), p(0f, -0.06f))

private val TAIL_SIDE = drawn(
    p(0f, 0.11f), p(0.15f, 0.10f), p(0.40f, 0.075f), p(0.70f, 0.05f), p(1.00f, 0.03f), p(1.25f, 0.012f), p(1.34f, 0f),
    p(1.25f, -0.012f), p(1.00f, -0.03f), p(0.70f, -0.05f), p(0.40f, -0.075f), p(0.15f, -0.10f), p(0f, -0.11f),
)
private val TAIL_TOP = drawn(
    p(0f, 0.10f), p(0.15f, 0.09f), p(0.40f, 0.07f), p(0.70f, 0.05f), p(1.00f, 0.03f), p(1.25f, 0.012f), p(1.34f, 0f),
    p(1.25f, -0.012f), p(1.00f, -0.03f), p(0.70f, -0.05f), p(0.40f, -0.07f), p(0.15f, -0.09f), p(0f, -0.10f),
)

/**
 * The dragon's parts on the canine plan's sockets: a long low torso, a snouted head with small swept-back ears and a long tapering
 * tail, drawn from outlines, its legs and paws as their templates build them with three toes, and a pair of membrane wings on a
 * paired hand-made socket on the shoulders, raised a little from their rest out to the sides, and rows of spikes down its back and
 * tail, each smaller than the last, a pair of horns leaning back from its crown, and a claw on every toe.
 */
fun dragonBody(): List<DocumentCommand> = listOf(
    AddParts(listOf(PartInstance(TORSO, ref("torso"), role = Role.BODY))),
    AddParts(listOf(PartInstance(HEAD, ref("head"), SocketRef(TORSO, "neck"), role = Role.HEAD), PartInstance(TAIL, ref("tail"), SocketRef(TORSO, "tail"), role = Role.TAIL))),
    pair("ears", "ear", Role.EAR, EAR_L, HEAD, EAR_R, HEAD, "ear"),
    pair("front-legs", "leg", Role.ARM, FRONT_LEG_L, TORSO, FRONT_LEG_R, TORSO, "front-leg"),
    pair("back-legs", "leg", Role.LEG, BACK_LEG_L, TORSO, BACK_LEG_R, TORSO, "back-leg"),
    pair("front-paws", "paw", Role.HAND, FRONT_PAW_L, FRONT_LEG_L, FRONT_PAW_R, FRONT_LEG_R, "paw"),
    pair("back-paws", "paw", Role.FOOT, BACK_PAW_L, BACK_LEG_L, BACK_PAW_R, BACK_LEG_R, "paw"),
    SetOutlines(TORSO, mapOf(View.SIDE to TORSO_SIDE, View.TOP to TORSO_TOP)),
    SetOutlines(HEAD, mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP)),
    SetOutlines(EAR_L, mapOf(View.SIDE to EAR_SIDE, View.TOP to EAR_TOP)),
    SetParameter(EAR_L, PlacementParams.OUTWARD, EAR_OUTWARD),
    SetOutlines(TAIL, mapOf(View.SIDE to TAIL_SIDE, View.TOP to TAIL_TOP)),
    SetParameter(TAIL, PlacementParams.LEAN, TAIL_LEAN),
    SetParameter(FRONT_PAW_L, "toes", PAW_TOES.toFloat()),
    SetParameter(BACK_PAW_L, "toes", PAW_TOES.toFloat()),
    HandSockets.add(TORSO, WING_SOCKET, SHOULDER_U, SHOULDER_V, 0f, paired = true),
    pair("wings", "wing-membrane", Role.WING, WING_L, TORSO, WING_R, TORSO, HandSockets.name(WING_SOCKET)),
    SetParameter(WING_L, "size", WING_SIZE),
    SetParameter(WING_L, PlacementParams.LEAN, WINGS_RAISED),
) + SPIKE_ROWS.flatMap(::spikes) + listOf(
    HandSockets.add(HEAD, HORN_SOCKET, CROWN_U, CROWN_V, 0f, paired = true),
    pair("horns", "horn", Role.HORN, HORN_L, HEAD, HORN_R, HEAD, HandSockets.name(HORN_SOCKET)),
    claws("front-claws", FRONT_PAW_L, FRONT_PAW_R),
    claws("back-claws", BACK_PAW_L, BACK_PAW_R),
)
