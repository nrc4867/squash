package dev.chieppa.squash.screens.twin

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

/** How long her arms, legs and neck are drawn, which a garment's branches over them are measured by. */
internal val LIMB_LENGTHS = mapOf(ARM_L to ARM_LENGTH, LEG_L to LEG_LENGTH, NECK to NECK_LENGTH)

/** Up the torso, as a share of its length, where the shoulder joints and the hip joints stand. */
internal fun torsoU(height: Float) = (height - PELVIS_HEIGHT) / TORSO_LENGTH
private const val SHOULDER_HEIGHT = 1.318f
private const val HIP_HEIGHT = 0.746f

/** A socket seated on its part's surface rather than sunk into it, so the joints stand where the reference's do. */
private const val ON_SURFACE = 0f

/** Where round an upright part its left side lies, as hand-made sockets read v. */
internal const val LEFT = 0.75f

/** The torso's joints: the hips into the spine, the spine into the chest and the chest into the neck, at the reference's bone heights. */
private val SPINE = listOf(0.99f, 1.13f, 1.335f)
private val SPINE_BEND = Limits(-25f, 25f)

/** The shoulder, elbow and knee, placed at the reference's joints along each limb. */
private const val SHOULDER_AT = 0.04f
private val SHOULDER_SWING = Limits(-170f, 60f)
private const val ELBOW_AT = 0.2606f / ARM_LENGTH
private val ELBOW_BEND = Limits(-145f, 0f)
private const val KNEE_AT = KNEE_ALONG / LEG_LENGTH
private val KNEE_BEND = Limits(0f, 140f)

private const val FINE_RINGS = 96
private const val FINE_SEGMENTS = 32
private const val LIMB_RINGS = 64
private const val LIMB_SEGMENTS = 24

/** Built in A-pose: the arms 45 degrees out from the body, the legs straight down. */
internal const val ARM_OUTWARD = 45f
private const val LEG_OUTWARD = -1f

private const val HAND_SIZE = 1.0f

private fun on(part: PartId, socket: Int) = SocketRef(part, HandSockets.name(socket))

private fun pairOn(id: String, template: String, left: PartId, right: PartId, socket: SocketRef, role: Role) = AddParts(
    listOf(
        PartInstance(left, ref(template), socket, pair = PairId(id), side = Side.LEFT, role = role, roleGuessed = true),
        PartInstance(right, ref(template), socket, pair = PairId(id), side = Side.RIGHT, role = role, roleGuessed = true),
    ),
)

private fun pairEach(id: String, template: String, role: Role, left: Pair<PartId, PartId>, right: Pair<PartId, PartId>) = AddParts(
    listOf(
        PartInstance(left.first, ref(template), on(left.second, END_SOCKET), pair = PairId(id), side = Side.LEFT, role = role),
        PartInstance(right.first, ref(template), on(right.second, END_SOCKET), pair = PairId(id), side = Side.RIGHT, role = role),
    ),
)

private fun fine(part: PartId, rings: Int, segments: Int) = listOf(GenericParams.rings(part, rings), GenericParams.segments(part, segments))

/**
 * The twin's shape on the Blank plan, in metres: a tube torso standing up from the bottom of the pelvis to the base of the neck, with joints
 * where the reference's hips, spine and chest bones meet; a tube neck up to the head's origin; tube arms and legs on paired sockets with a
 * shoulder, an elbow and a knee at the reference's joints; the shipped hand and foot.
 */
fun twinBody(): List<DocumentCommand> = buildList {
    add(AddParts(listOf(PartInstance(TORSO, ref("tube"), role = Role.BODY))))
    add(RestDirection.restIn(TORSO, RestDirection.UP))
    addAll(fine(TORSO, FINE_RINGS, FINE_SEGMENTS))
    add(SetOutlines(TORSO, mapOf(View.SIDE to TORSO_SIDE, View.TOP to TORSO_TOP)))
    SPINE.forEachIndexed { i, height -> add(HandJoints.add(TORSO, i + 1, torsoU(height), Hinge.X, SPINE_BEND, stretch = false)) }
    add(HandSockets.add(TORSO, NECK_SOCKET, 1f, 0f, 0f, paired = false, sink = ON_SURFACE))
    add(HandSockets.add(TORSO, SHOULDER_SOCKET, torsoU(SHOULDER_HEIGHT), LEFT, 0f, paired = true, sink = ON_SURFACE))
    add(HandSockets.add(TORSO, HIP_SOCKET, torsoU(HIP_HEIGHT), LEFT, 0f, paired = true, sink = ON_SURFACE))

    add(AddParts(listOf(PartInstance(NECK, ref("tube"), on(TORSO, NECK_SOCKET), role = Role.OTHER, roleGuessed = true))))
    add(RestDirection.restIn(NECK, RestDirection.UP))
    add(SetRole(NECK, Role.NECK))
    add(SetOutlines(NECK, mapOf(View.SIDE to NECK_SIDE, View.TOP to NECK_TOP)))
    add(HandSockets.add(NECK, END_SOCKET, 1f, 0f, 0f, paired = false, sink = 0f))

    add(pairOn("arms", "tube", ARM_L, ARM_R, on(TORSO, SHOULDER_SOCKET), Role.OTHER))
    add(RestDirection.restIn(ARM_L, RestDirection.DOWN))
    add(SetRole(ARM_L, Role.ARM))
    addAll(fine(ARM_L, LIMB_RINGS, LIMB_SEGMENTS))
    add(SetOutlines(ARM_L, mapOf(View.SIDE to ARM_SIDE, View.TOP to ARM_TOP)))
    add(SetParameter(ARM_L, PlacementParams.OUTWARD, ARM_OUTWARD))
    add(HandJoints.add(ARM_L, 1, SHOULDER_AT, Hinge.X, SHOULDER_SWING, stretch = false))
    add(HandJoints.add(ARM_L, 2, ELBOW_AT, Hinge.X, ELBOW_BEND, stretch = true))
    add(HandSockets.add(ARM_L, END_SOCKET, 1f, 0.5f, 0f, paired = false))

    add(pairOn("legs", "tube", LEG_L, LEG_R, on(TORSO, HIP_SOCKET), Role.OTHER))
    add(RestDirection.restIn(LEG_L, RestDirection.DOWN))
    add(SetRole(LEG_L, Role.LEG))
    addAll(fine(LEG_L, LIMB_RINGS, LIMB_SEGMENTS))
    add(SetOutlines(LEG_L, mapOf(View.SIDE to LEG_SIDE, View.TOP to LEG_TOP)))
    add(SetParameter(LEG_L, PlacementParams.OUTWARD, LEG_OUTWARD))
    add(HandJoints.add(LEG_L, 1, KNEE_AT, Hinge.X, KNEE_BEND, stretch = true))
    add(HandSockets.add(LEG_L, END_SOCKET, 1f, 0.5f, 0f, paired = false, sink = ON_SURFACE))

    add(pairEach("hands", "hand", Role.HAND, HAND_L to ARM_L, HAND_R to ARM_R))
    add(SetParameter(HAND_L, "size", HAND_SIZE))
    add(SetOutlines(HAND_L, mapOf(View.SIDE to HAND_SIDE, View.TOP to HAND_TOP)))
    add(pairEach("feet", "foot", Role.FOOT, FOOT_L to LEG_L, FOOT_R to LEG_R))
    add(SetParameter(FOOT_L, "size", FOOT_SIZE))
    add(SetOutlines(FOOT_L, mapOf(View.SIDE to FOOT_SIDE, View.TOP to FOOT_TOP)))
}
