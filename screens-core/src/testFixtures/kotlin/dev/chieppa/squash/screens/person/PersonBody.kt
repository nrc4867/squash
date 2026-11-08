package dev.chieppa.squash.screens.person

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.drawn
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val TORSO = PartId("torso")
val HEAD = PartId("head")
val HAIR = PartId("hair")
val ARM_L = PartId("arm-l")
val ARM_R = PartId("arm-r")
val HAND_L = PartId("hand-l")
val HAND_R = PartId("hand-r")
val LEG_L = PartId("leg-l")
val LEG_R = PartId("leg-r")
val FOOT_L = PartId("foot-l")
val FOOT_R = PartId("foot-r")

/** How far the arms lean out from the body, in degrees, so the hands hang clear of the thighs. */
private const val ARM_OUTWARD = 12f

/** The hand-made socket on the crown that takes the hair. */
const val HAIR_SOCKET = 1

/** The template versions the example takes past the first. */
private val NEWER = mapOf("hand" to 2)

private fun ref(id: String) = TemplateRef(TemplateId(id), NEWER[id] ?: 1)

/** A pair as Add part places it on a paired socket of the biped plan, each side on its own parent. */
private fun pair(id: String, template: String, role: Role, left: PartId, leftOn: PartId, right: PartId, rightOn: PartId, socket: String) = AddParts(
    listOf(
        PartInstance(left, ref(template), SocketRef(leftOn, socket), pair = PairId(id), side = Side.LEFT, role = role),
        PartInstance(right, ref(template), SocketRef(rightOn, socket), pair = PairId(id), side = Side.RIGHT, role = role),
    ),
)

/** The torso in Side, hips at the left and shoulders at the right, the belly forward and up: round in front, flatter behind. */
private val TORSO_SIDE = loop(
    p(0.00f, 0.02f), p(0.04f, 0.12f), p(0.17f, 0.18f), p(0.31f, 0.185f), p(0.45f, 0.16f), p(0.56f, 0.12f), p(0.62f, 0.04f),
    p(0.61f, -0.06f), p(0.54f, -0.12f), p(0.38f, -0.135f), p(0.20f, -0.14f), p(0.06f, -0.12f),
)

/** The torso seen from in front: broad at the shoulders and the hips, barely in at the waist. */
private val TORSO_FRONT = loop(
    p(0.00f, 0.00f), p(0.04f, 0.17f), p(0.18f, 0.215f), p(0.34f, 0.205f), p(0.50f, 0.225f), p(0.59f, 0.18f), p(0.63f, 0.00f),
    p(0.59f, -0.18f), p(0.50f, -0.225f), p(0.34f, -0.205f), p(0.18f, -0.215f), p(0.04f, -0.17f),
)

/**
 * The round head in Side, the back of the skull at the left and the face at the right, with a bit of a chin under the face, drawn
 * to the template's length so its mount, read from the template, lands under its middle.
 */
private val HEAD_SIDE = loop(
    p(0.000f, 0.018f), p(0.052f, 0.150f), p(0.182f, 0.234f), p(0.338f, 0.222f), p(0.455f, 0.138f), p(0.501f, 0.018f), p(0.488f, -0.090f),
    p(0.442f, -0.186f), p(0.351f, -0.234f), p(0.208f, -0.204f), p(0.078f, -0.138f),
)
private val HEAD_TOP = loop(
    p(0.000f, 0.000f), p(0.052f, 0.144f), p(0.182f, 0.216f), p(0.338f, 0.210f), p(0.455f, 0.132f), p(0.501f, 0.000f),
    p(0.455f, -0.132f), p(0.338f, -0.210f), p(0.182f, -0.216f), p(0.052f, -0.144f),
)

/** The hair: a blob lying along the crown, as wide as the head and deep enough to show above it. */
private val HAIR_SIDE = loop(p(0.000f, 0.024f), p(0.054f, 0.084f), p(0.189f, 0.102f), p(0.324f, 0.072f), p(0.392f, 0.000f), p(0.324f, -0.060f), p(0.162f, -0.072f), p(0.041f, -0.048f))
private val HAIR_TOP = loop(p(0.000f, 0.000f), p(0.054f, 0.156f), p(0.189f, 0.228f), p(0.324f, 0.180f), p(0.392f, 0.000f), p(0.324f, -0.180f), p(0.189f, -0.228f), p(0.054f, -0.156f))

/** A leg, thick at the thigh and narrowing to the ankle, in Side and from in front. */
private val LEG_SIDE = drawn(p(0f, 0.085f), p(0.12f, 0.08f), p(0.22f, 0.065f), p(0.34f, 0.058f), p(0.40f, 0f), p(0.34f, -0.058f), p(0.22f, -0.065f), p(0.10f, -0.08f), p(0f, -0.09f))
private val LEG_FRONT = drawn(p(0f, 0.09f), p(0.18f, 0.075f), p(0.34f, 0.06f), p(0.40f, 0f), p(0.34f, -0.06f), p(0.18f, -0.075f), p(0f, -0.09f))

/** An arm, round at the shoulder and narrowing to the wrist. */
private val ARM_SIDE = drawn(p(0f, 0.065f), p(0.14f, 0.055f), p(0.28f, 0.045f), p(0.36f, 0f), p(0.28f, -0.045f), p(0.14f, -0.055f), p(0f, -0.065f))

/** A shoe in Side and Top: a rounded heel behind the ankle, a long flat sole and a round toe cap. */
private val FOOT_SIDE = loop(
    p(-0.03f, 0.00f), p(-0.015f, 0.05f), p(0.05f, 0.07f), p(0.13f, 0.05f), p(0.21f, 0.035f), p(0.25f, 0.00f), p(0.23f, -0.035f),
    p(0.10f, -0.045f), p(0.00f, -0.045f),
)
private val FOOT_TOP = loop(
    p(-0.03f, 0.00f), p(-0.01f, 0.045f), p(0.08f, 0.055f), p(0.18f, 0.06f), p(0.24f, 0.035f), p(0.255f, 0.00f),
    p(0.24f, -0.035f), p(0.18f, -0.06f), p(0.08f, -0.055f), p(-0.01f, -0.045f),
)

/**
 * The person's shape on the biped plan: a stocky torso drawn in Side and from in front, a round head with a chin, arms and legs drawn,
 * arms leaning out a little, hands at the template's own shape, shoes for feet with no toes, and hair, a blob on a socket placed by hand on the crown. Every
 * part takes the role Add part gives it there; the hair's guess is chosen as other.
 */
fun personBody(): List<DocumentCommand> = listOf(
    AddParts(listOf(PartInstance(TORSO, ref("torso-upright"), role = Role.BODY))),
    AddParts(listOf(PartInstance(HEAD, ref("head-round"), SocketRef(TORSO, "neck"), role = Role.HEAD))),
    pair("arms", "arm", Role.ARM, ARM_L, TORSO, ARM_R, TORSO, "shoulder"),
    pair("legs", "leg", Role.LEG, LEG_L, TORSO, LEG_R, TORSO, "hip"),
    pair("hands", "hand", Role.HAND, HAND_L, ARM_L, HAND_R, ARM_R, "hand"),
    pair("feet", "foot", Role.FOOT, FOOT_L, LEG_L, FOOT_R, LEG_R, "paw"),
    SetOutlines(TORSO, mapOf(View.SIDE to TORSO_SIDE, View.TOP to TORSO_FRONT)),
    SetOutlines(HEAD, mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP)),
    SetOutlines(ARM_L, mapOf(View.SIDE to ARM_SIDE, View.TOP to ARM_SIDE)),
    SetParameter(ARM_L, PlacementParams.OUTWARD, ARM_OUTWARD),
    SetOutlines(LEG_L, mapOf(View.SIDE to LEG_SIDE, View.TOP to LEG_FRONT)),
    SetOutlines(FOOT_L, mapOf(View.SIDE to FOOT_SIDE, View.TOP to FOOT_TOP)),
    SetParameter(FOOT_L, "toes", 0f),
    SetParameter(HAND_L, "size", 0.9f),
    HandSockets.add(HEAD, HAIR_SOCKET, 0.12f, 0.02f, 0f, paired = false),
    AddParts(listOf(PartInstance(HAIR, ref("blob"), SocketRef(HEAD, HandSockets.name(HAIR_SOCKET)), role = Role.EAR, roleGuessed = true))),
    SetRole(HAIR, Role.OTHER),
    SetOutlines(HAIR, mapOf(View.SIDE to HAIR_SIDE, View.TOP to HAIR_TOP)),
)
