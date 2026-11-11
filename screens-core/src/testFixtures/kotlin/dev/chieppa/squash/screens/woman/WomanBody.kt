package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Limits
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
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.drawn
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.fennec.ref

val TORSO = PartId("torso")
val NECK = PartId("neck")
val ARM_L = PartId("arm-l")
val ARM_R = PartId("arm-r")
val HAND_L = PartId("hand-l")
val HAND_R = PartId("hand-r")
val LEG_L = PartId("leg-l")
val LEG_R = PartId("leg-r")
val FOOT_L = PartId("foot-l")
val FOOT_R = PartId("foot-r")

/** The torso's hand-made sockets: the neck on its top, the shoulders and the hips. */
const val NECK_SOCKET = 1
const val SHOULDER_SOCKET = 2
const val HIP_SOCKET = 3

/** The hand-made socket at the end of a limb or the neck that takes what hangs from it. */
const val END_SOCKET = 1

/** How long her arms and legs are drawn, which a garment's branches over them are measured by. */
internal val LIMB_LENGTHS = mapOf(ARM_L to 0.62f, LEG_L to 0.94f, NECK to 0.13f)

/** Where along the torso the shoulders and the hips sit. */
internal const val SHOULDER_U = 0.83f
internal const val HIP_U = 0.045f

/** Where round an upright part its left side lies, as hand-made sockets read v. */
private const val LEFT = 0.75f

/** The torso's joints: the small of the back, the ribs and the chest. */
internal val SPINE = listOf(0.32f, 0.55f, 0.8f)
private val SPINE_BEND = Limits(-25f, 25f)

/** A shoulder that lifts the arm forward, negative, as far as overhead, a little below the tube's own base, which only turns 60 degrees. */
private const val SHOULDER_AT = 0.06f
private val SHOULDER_SWING = Limits(-170f, 60f)
private const val ELBOW_AT = 0.5f
private val ELBOW_BEND = Limits(-145f, 0f)
private const val KNEE_AT = 0.52f
private val KNEE_BEND = Limits(0f, 140f)

/** The finest a generic part may be lofted. */
private const val FINE_RINGS = 96
private const val FINE_SEGMENTS = 32
private const val LIMB_RINGS = 64
private const val LIMB_SEGMENTS = 24

/** She is built in A-pose: the arms 45 degrees out from the body, the legs a little apart under the wide hips. */
internal const val ARM_OUTWARD = 45f
private const val LEG_OUTWARD = 1f

private const val HAND_SIZE = 1.0f
internal const val FOOT_SIZE = 0.6f

/**
 * The torso in Side, hips at the left and the base of the neck at the right, front up: a full seat behind, the belly in to a narrow
 * waist, the bust forward under the chest and the shoulders sloping to the neck.
 */
internal val TORSO_SIDE_POINTS = listOf(
    p(0.000f, 0.010f), p(0.040f, 0.090f), p(0.120f, 0.115f), p(0.220f, 0.105f), p(0.320f, 0.088f), p(0.420f, 0.095f),
    p(0.475f, 0.125f), p(0.525f, 0.165f), p(0.575f, 0.150f), p(0.620f, 0.105f), p(0.655f, 0.065f), p(0.680f, 0.000f),
    p(0.655f, -0.060f), p(0.600f, -0.090f), p(0.480f, -0.095f), p(0.380f, -0.082f), p(0.300f, -0.090f), p(0.200f, -0.135f),
    p(0.110f, -0.150f), p(0.040f, -0.120f),
)

/** The torso from in front: wide at the hips, in at the waist, out again over the ribs to the shoulders. */
internal val TORSO_FRONT_POINTS = listOf(
    p(0.000f, 0.000f), p(0.040f, 0.165f), p(0.120f, 0.205f), p(0.220f, 0.190f), p(0.320f, 0.135f), p(0.420f, 0.145f),
    p(0.520f, 0.160f), p(0.600f, 0.175f), p(0.640f, 0.150f), p(0.668f, 0.085f), p(0.680f, 0.000f),
    p(0.668f, -0.085f), p(0.640f, -0.150f), p(0.600f, -0.175f), p(0.520f, -0.160f), p(0.420f, -0.145f), p(0.320f, -0.135f), p(0.220f, -0.190f),
    p(0.120f, -0.205f), p(0.040f, -0.165f),
)

private val TORSO_SIDE = loop(*TORSO_SIDE_POINTS.toTypedArray())
private val TORSO_FRONT = loop(*TORSO_FRONT_POINTS.toTypedArray())

/** The torso's drawn length, from the hips to the base of the neck. */
internal const val TORSO_LENGTH = 0.68f

private val NECK_SIDE = drawn(p(0f, 0.075f), p(0.04f, 0.06f), p(0.1f, 0.056f), p(0.13f, 0f), p(0.1f, -0.056f), p(0.04f, -0.064f), p(0f, -0.085f))
private val NECK_FRONT = drawn(p(0f, 0.105f), p(0.04f, 0.07f), p(0.1f, 0.062f), p(0.13f, 0f), p(0.1f, -0.062f), p(0.04f, -0.07f), p(0f, -0.105f))

/** An arm, round over the shoulder, slim above the elbow, a soft forearm and a narrow wrist, front up in Side. */
private val ARM_SIDE = drawn(
    p(0f, 0.05f), p(0.08f, 0.056f), p(0.2f, 0.046f), p(0.31f, 0.038f), p(0.4f, 0.041f), p(0.5f, 0.034f), p(0.59f, 0.024f),
    p(0.62f, 0f), p(0.59f, -0.025f), p(0.5f, -0.036f), p(0.4f, -0.042f), p(0.31f, -0.038f), p(0.2f, -0.048f), p(0.08f, -0.058f), p(0f, -0.052f),
)
private val ARM_FRONT = drawn(
    p(0f, 0.05f), p(0.1f, 0.058f), p(0.2f, 0.046f), p(0.31f, 0.04f), p(0.42f, 0.042f), p(0.52f, 0.036f), p(0.59f, 0.031f),
    p(0.62f, 0f), p(0.59f, -0.031f), p(0.52f, -0.036f), p(0.42f, -0.042f), p(0.31f, -0.04f), p(0.2f, -0.046f), p(0.1f, -0.058f), p(0f, -0.05f),
)

/** A leg, front up in Side: a full thigh, the knee, a calf swelling behind and a slim ankle. */
private val LEG_SIDE = drawn(
    p(0f, 0.08f), p(0.12f, 0.094f), p(0.3f, 0.08f), p(0.46f, 0.058f), p(0.52f, 0.056f), p(0.62f, 0.05f), p(0.78f, 0.038f), p(0.9f, 0.03f),
    p(0.94f, 0f), p(0.9f, -0.034f), p(0.78f, -0.045f), p(0.64f, -0.072f), p(0.54f, -0.06f), p(0.46f, -0.058f), p(0.3f, -0.085f), p(0.12f, -0.105f),
    p(0f, -0.09f),
)
private val LEG_FRONT = drawn(
    p(0f, 0.09f), p(0.12f, 0.098f), p(0.3f, 0.082f), p(0.47f, 0.058f), p(0.53f, 0.056f), p(0.63f, 0.06f), p(0.78f, 0.042f), p(0.9f, 0.032f),
    p(0.94f, 0f), p(0.9f, -0.032f), p(0.78f, -0.042f), p(0.63f, -0.058f), p(0.53f, -0.054f), p(0.47f, -0.056f), p(0.3f, -0.08f), p(0.12f, -0.096f),
    p(0f, -0.085f),
)

/** A hand in Top, from the wrist: narrow there, widening over the palm to the knuckles, the fingers' run tapering to their tips. */
private val HAND_TOP = loop(
    p(0.000f, 0.040f), p(0.050f, 0.058f), p(0.110f, 0.072f), p(0.150f, 0.075f), p(0.205f, 0.075f), p(0.232f, 0.060f), p(0.240f, 0.000f),
    p(0.232f, -0.060f), p(0.205f, -0.075f), p(0.150f, -0.075f), p(0.110f, -0.072f), p(0.050f, -0.058f), p(0.000f, -0.040f),
)

/** The hand in Side: as deep as the wrist where it leaves it, flattening over the palm, thin through the fingers. */
private val HAND_SIDE = loop(
    p(0.000f, 0.032f), p(0.060f, 0.030f), p(0.130f, 0.024f), p(0.200f, 0.016f), p(0.240f, 0.000f),
    p(0.200f, -0.016f), p(0.130f, -0.026f), p(0.060f, -0.032f), p(0.000f, -0.034f),
)

/** A bare foot in Side, from behind the ankle: a round heel, the instep rising to the ankle and falling to the toes, a flat sole. */
private val FOOT_SIDE = loop(
    p(-0.030f, 0.000f), p(-0.020f, 0.045f), p(0.030f, 0.075f), p(0.110f, 0.065f), p(0.220f, 0.040f), p(0.320f, 0.024f), p(0.405f, 0.020f),
    p(0.420f, -0.005f), p(0.405f, -0.035f), p(0.250f, -0.042f), p(0.080f, -0.045f), p(-0.010f, -0.040f),
)

/** The foot in Top: a narrow heel, the arch, widest across the ball and round over the toes. */
private val FOOT_TOP = loop(
    p(-0.030f, 0.000f), p(-0.020f, 0.032f), p(0.060f, 0.038f), p(0.180f, 0.046f), p(0.300f, 0.056f), p(0.385f, 0.056f), p(0.412f, 0.045f),
    p(0.420f, 0.000f), p(0.412f, -0.045f), p(0.385f, -0.056f), p(0.300f, -0.056f), p(0.180f, -0.046f), p(0.060f, -0.038f), p(-0.020f, -0.032f),
)

private fun on(part: PartId, socket: Int) = SocketRef(part, HandSockets.name(socket))

/** A pair as Add part places it on a paired hand-made socket, its role a guess until it is chosen. */
private fun pairOn(id: String, template: String, left: PartId, right: PartId, socket: SocketRef, role: Role) = AddParts(
    listOf(
        PartInstance(left, ref(template), socket, pair = PairId(id), side = Side.LEFT, role = role, roleGuessed = true),
        PartInstance(right, ref(template), socket, pair = PairId(id), side = Side.RIGHT, role = role, roleGuessed = true),
    ),
)

/** A pair as Add part places it on each side's own parent, a typed template giving its role. */
private fun pairEach(id: String, template: String, role: Role, left: Pair<PartId, PartId>, right: Pair<PartId, PartId>) = AddParts(
    listOf(
        PartInstance(left.first, ref(template), on(left.second, END_SOCKET), pair = PairId(id), side = Side.LEFT, role = role),
        PartInstance(right.first, ref(template), on(right.second, END_SOCKET), pair = PairId(id), side = Side.RIGHT, role = role),
    ),
)

/** The commands that make [part], a generic part, fine: the most rings and segments it may take. */
private fun fine(part: PartId, rings: Int, segments: Int) = listOf(GenericParams.rings(part, rings), GenericParams.segments(part, segments))

/**
 * The woman's shape on the Blank plan, from generic parts where a template's built shape would fight her figure: a tube torso standing
 * up, drawn in Side and from in front, with a spine of three joints; a tube neck; tube arms and legs on paired sockets with a
 * shoulder, an elbow and a knee placed by hand. The hands and feet are the shipped hand and foot, whose fingers, thumb, toes and webs
 * are blocks no generic part has.
 */
fun womanBody(): List<DocumentCommand> = buildList {
    add(AddParts(listOf(PartInstance(TORSO, ref("tube"), role = Role.BODY))))
    add(RestDirection.restIn(TORSO, RestDirection.UP))
    addAll(fine(TORSO, FINE_RINGS, FINE_SEGMENTS))
    add(SetOutlines(TORSO, mapOf(View.SIDE to TORSO_SIDE, View.TOP to TORSO_FRONT)))
    SPINE.forEachIndexed { i, u -> add(HandJoints.add(TORSO, i + 1, u, Hinge.X, SPINE_BEND, stretch = false)) }
    add(HandSockets.add(TORSO, NECK_SOCKET, 1f, 0f, 0f, paired = false))
    add(HandSockets.add(TORSO, SHOULDER_SOCKET, SHOULDER_U, LEFT, 0f, paired = true))
    add(HandSockets.add(TORSO, HIP_SOCKET, HIP_U, LEFT, 0f, paired = true))

    add(AddParts(listOf(PartInstance(NECK, ref("tube"), on(TORSO, NECK_SOCKET), role = Role.OTHER, roleGuessed = true))))
    add(RestDirection.restIn(NECK, RestDirection.UP))
    add(SetRole(NECK, Role.NECK))
    add(SetOutlines(NECK, mapOf(View.SIDE to NECK_SIDE, View.TOP to NECK_FRONT)))
    add(HandSockets.add(NECK, END_SOCKET, 1f, 0f, 0f, paired = false))

    add(pairOn("arms", "tube", ARM_L, ARM_R, on(TORSO, SHOULDER_SOCKET), Role.OTHER))
    add(RestDirection.restIn(ARM_L, RestDirection.DOWN))
    add(SetRole(ARM_L, Role.ARM))
    addAll(fine(ARM_L, LIMB_RINGS, LIMB_SEGMENTS))
    add(SetOutlines(ARM_L, mapOf(View.SIDE to ARM_SIDE, View.TOP to ARM_FRONT)))
    add(SetParameter(ARM_L, PlacementParams.OUTWARD, ARM_OUTWARD))
    add(HandJoints.add(ARM_L, 1, SHOULDER_AT, Hinge.X, SHOULDER_SWING, stretch = false))
    add(HandJoints.add(ARM_L, 2, ELBOW_AT, Hinge.X, ELBOW_BEND, stretch = true))
    add(HandSockets.add(ARM_L, END_SOCKET, 1f, 0.5f, 0f, paired = false))

    add(pairOn("legs", "tube", LEG_L, LEG_R, on(TORSO, HIP_SOCKET), Role.OTHER))
    add(RestDirection.restIn(LEG_L, RestDirection.DOWN))
    add(SetRole(LEG_L, Role.LEG))
    addAll(fine(LEG_L, LIMB_RINGS, LIMB_SEGMENTS))
    add(SetOutlines(LEG_L, mapOf(View.SIDE to LEG_SIDE, View.TOP to LEG_FRONT)))
    add(SetParameter(LEG_L, PlacementParams.OUTWARD, LEG_OUTWARD))
    add(HandJoints.add(LEG_L, 1, KNEE_AT, Hinge.X, KNEE_BEND, stretch = true))
    add(HandSockets.add(LEG_L, END_SOCKET, 1f, 0.5f, 0f, paired = false))

    add(pairEach("hands", "hand", Role.HAND, HAND_L to ARM_L, HAND_R to ARM_R))
    add(SetParameter(HAND_L, "size", HAND_SIZE))
    add(SetOutlines(HAND_L, mapOf(View.SIDE to HAND_SIDE, View.TOP to HAND_TOP)))
    add(pairEach("feet", "foot", Role.FOOT, FOOT_L to LEG_L, FOOT_R to LEG_R))
    add(SetParameter(FOOT_L, "size", FOOT_SIZE))
    add(SetOutlines(FOOT_L, mapOf(View.SIDE to FOOT_SIDE, View.TOP to FOOT_TOP)))
}
