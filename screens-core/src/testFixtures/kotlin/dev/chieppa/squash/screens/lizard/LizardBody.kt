package dev.chieppa.squash.screens.lizard

import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
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
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.drawn
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val LIZARD_BODY = PartId("body")
val TAIL = PartId("tail")
val HEAD = PartId("head")
val EYE_L = PartId("eye-l")
val EYE_R = PartId("eye-r")
val FRONT_LEG_L = PartId("front-leg-l")
val FRONT_LEG_R = PartId("front-leg-r")
val BACK_LEG_L = PartId("back-leg-l")
val BACK_LEG_R = PartId("back-leg-r")
val FRONT_FOOT_L = PartId("front-foot-l")
val FRONT_FOOT_R = PartId("front-foot-r")
val BACK_FOOT_L = PartId("back-foot-l")
val BACK_FOOT_R = PartId("back-foot-r")

/** The hand-made sockets on the body, by number: the tail at its rear end, the legs on its sides and the head at its front end. */
const val TAIL_SOCKET = 1
const val FRONT_LEG_SOCKET = 2
const val BACK_LEG_SOCKET = 3
const val HEAD_SOCKET = 4

/** The hand-made socket at a leg's end that takes its foot, and the one on the head that takes the eyes. */
const val FOOT_SOCKET = 1
const val EYE_SOCKET = 1

/** The body's hand-made joints: the tail's base, two along the spine and the neck. */
private val BODY_JOINTS = listOf(0.12f, 0.4f, 0.64f, 0.88f)
private val BODY_BEND = Limits(-30f, 30f)

/** The tail's hand-made joints, a third and two thirds along it. */
private val TAIL_JOINTS = listOf(0.33f, 0.66f)
private val TAIL_BEND = Limits(-45f, 45f)

/** A leg's knee, halfway down it, bending one way. */
private const val KNEE = 0.5f
private val KNEE_BEND = Limits(0f, 120f)

private fun ref(id: String) = TemplateRef(TemplateId(id), 1)

private fun on(part: PartId, socket: Int) = SocketRef(part, HandSockets.name(socket))

/** A part as Add part places it on a hand-made socket: a generic template, its role a guess until it is chosen. */
private fun guessed(id: PartId, template: String, socket: SocketRef, role: Role) = PartInstance(id, ref(template), socket, role = role, roleGuessed = true)

/** A pair as Add part places it on a paired hand-made socket, each side on [left] and [right]. */
private fun pair(id: String, template: String, left: Pair<PartId, SocketRef>, right: Pair<PartId, SocketRef>, role: Role) = AddParts(
    listOf(
        PartInstance(left.first, ref(template), left.second, pair = PairId(id), side = Side.LEFT, role = role, roleGuessed = true),
        PartInstance(right.first, ref(template), right.second, pair = PairId(id), side = Side.RIGHT, role = role, roleGuessed = true),
    ),
)

/** The body in Side: long and low, deepest at the middle, its rear end thick where the tail leaves it and its front narrowing to the neck. */
private val BODY_SIDE = loop(
    p(0.00f, 0.035f), p(0.10f, 0.055f), p(0.30f, 0.075f), p(0.50f, 0.08f), p(0.70f, 0.07f), p(0.86f, 0.05f), p(0.95f, 0.035f),
    p(1.00f, 0.0f), p(0.95f, -0.035f), p(0.86f, -0.045f), p(0.70f, -0.065f), p(0.50f, -0.075f), p(0.30f, -0.07f), p(0.10f, -0.05f),
    p(0.00f, -0.03f),
)

/** The body in Top: wider than it is deep, bellied in the middle. */
private val BODY_TOP = loop(
    p(0.00f, 0.04f), p(0.10f, 0.06f), p(0.30f, 0.095f), p(0.50f, 0.105f), p(0.70f, 0.09f), p(0.86f, 0.06f), p(0.95f, 0.04f),
    p(1.00f, 0.0f), p(0.95f, -0.04f), p(0.86f, -0.06f), p(0.70f, -0.09f), p(0.50f, -0.105f), p(0.30f, -0.095f), p(0.10f, -0.06f),
    p(0.00f, -0.04f),
)

/** The tail in Side and Top: as thick as the body's rear where it leaves it, tapering over a long reach to a point. */
private val TAIL_SIDE = drawn(p(0f, 0.035f), p(0.25f, 0.026f), p(0.6f, 0.014f), p(0.9f, 0.0f), p(0.6f, -0.012f), p(0.25f, -0.024f), p(0f, -0.032f))
private val TAIL_TOP = drawn(p(0f, 0.04f), p(0.25f, 0.03f), p(0.6f, 0.016f), p(0.9f, 0.0f), p(0.6f, -0.016f), p(0.25f, -0.03f), p(0f, -0.04f))

/** A foot in Side and Top: a flat paddle, wider than it is deep, rounded at its toes. */
private val FOOT_SIDE = drawn(p(0f, 0.014f), p(0.05f, 0.012f), p(0.09f, 0.005f), p(0.1f, 0f), p(0.09f, -0.01f), p(0.05f, -0.012f), p(0f, -0.012f))
private val FOOT_TOP = drawn(p(0f, 0.018f), p(0.05f, 0.03f), p(0.09f, 0.025f), p(0.1f, 0f), p(0.09f, -0.025f), p(0.05f, -0.03f), p(0f, -0.018f))

/** The head in Side and Top: a blunt wedge, flat underneath, longer than it is wide. */
private val HEAD_SIDE = loop(
    p(0.00f, 0.03f), p(0.06f, 0.05f), p(0.14f, 0.045f), p(0.22f, 0.025f), p(0.25f, 0.0f), p(0.22f, -0.02f), p(0.12f, -0.03f), p(0.03f, -0.03f),
)
private val HEAD_TOP = loop(
    p(0.00f, 0.045f), p(0.08f, 0.055f), p(0.17f, 0.04f), p(0.24f, 0.015f), p(0.25f, 0.0f), p(0.24f, -0.015f), p(0.17f, -0.04f), p(0.08f, -0.055f),
    p(0.00f, -0.045f),
)

/**
 * The lizard's shape on the Blank plan, built by hand: a long tube body drawn in Side and Top with four joints along it; a long tube
 * tail on a socket at its rear end, turned to rest back and given two joints, which makes the guess say tail; four short tube legs on
 * two paired sockets low on its sides, each resting down and splayed out, with a knee and a flat tube foot on a socket at its end; and
 * a blob head on a socket at its front end, drawn as a wedge, with a landmark for its face and two blob eyes on a paired socket. Every
 * part's role is the guess Add part gives it, then chosen.
 */
fun lizardBody(): List<DocumentCommand> = buildList {
    add(AddParts(listOf(PartInstance(LIZARD_BODY, ref("tube"), role = Role.BODY))))
    add(SetOutlines(LIZARD_BODY, mapOf(View.SIDE to BODY_SIDE, View.TOP to BODY_TOP)))
    BODY_JOINTS.forEachIndexed { i, u -> add(HandJoints.add(LIZARD_BODY, i + 1, u, Hinge.X, BODY_BEND, stretch = false)) }
    add(HandSockets.add(LIZARD_BODY, TAIL_SOCKET, 0f, 0f, 0f, paired = false))
    add(HandSockets.add(LIZARD_BODY, FRONT_LEG_SOCKET, 0.74f, 0.36f, 0f, paired = true))
    add(HandSockets.add(LIZARD_BODY, BACK_LEG_SOCKET, 0.22f, 0.36f, 0f, paired = true))
    add(HandSockets.add(LIZARD_BODY, HEAD_SOCKET, 1f, 0f, 0f, paired = false))

    add(AddParts(listOf(guessed(TAIL, "tube", on(LIZARD_BODY, TAIL_SOCKET), Role.OTHER))))
    add(RestDirection.restIn(TAIL, RestDirection.BACK))
    add(SetRole(TAIL, Role.TAIL))
    add(SetOutlines(TAIL, mapOf(View.SIDE to TAIL_SIDE, View.TOP to TAIL_TOP)))
    TAIL_JOINTS.forEachIndexed { i, u -> add(HandJoints.add(TAIL, i + 1, u, Hinge.X, TAIL_BEND, stretch = false)) }

    add(pair("front-legs", "tube", FRONT_LEG_L to on(LIZARD_BODY, FRONT_LEG_SOCKET), FRONT_LEG_R to on(LIZARD_BODY, FRONT_LEG_SOCKET), Role.OTHER))
    add(pair("back-legs", "tube", BACK_LEG_L to on(LIZARD_BODY, BACK_LEG_SOCKET), BACK_LEG_R to on(LIZARD_BODY, BACK_LEG_SOCKET), Role.OTHER))
    for (leg in listOf(FRONT_LEG_L, BACK_LEG_L)) {
        add(RestDirection.restIn(leg, RestDirection.DOWN))
        add(SetParameter(leg, PlacementParams.OUTWARD, 35f))
        add(SetParameter(leg, "size", 0.4f))
        add(SetParameter(leg, "girth", 0.13f))
        add(HandJoints.add(leg, 1, KNEE, Hinge.X, KNEE_BEND, stretch = true))
        add(HandSockets.add(leg, FOOT_SOCKET, 1f, 0.5f, 0f, paired = false))
    }
    add(SetRole(FRONT_LEG_L, Role.ARM))
    add(SetRole(BACK_LEG_L, Role.LEG))
    add(pair("front-feet", "tube", FRONT_FOOT_L to on(FRONT_LEG_L, FOOT_SOCKET), FRONT_FOOT_R to on(FRONT_LEG_R, FOOT_SOCKET), Role.OTHER))
    add(pair("back-feet", "tube", BACK_FOOT_L to on(BACK_LEG_L, FOOT_SOCKET), BACK_FOOT_R to on(BACK_LEG_R, FOOT_SOCKET), Role.OTHER))
    for (foot in listOf(FRONT_FOOT_L, BACK_FOOT_L)) add(SetOutlines(foot, mapOf(View.SIDE to FOOT_SIDE, View.TOP to FOOT_TOP)))
    add(SetRole(FRONT_FOOT_L, Role.HAND))
    add(SetRole(BACK_FOOT_L, Role.FOOT))

    add(AddParts(listOf(guessed(HEAD, "blob", on(LIZARD_BODY, HEAD_SOCKET), Role.HEAD))))
    add(SetRole(HEAD, Role.HEAD))
    add(SetOutlines(HEAD, mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP)))
    add(HandLandmarks.add(HEAD, 1, 0.45f, 0.2f))
    add(HandSockets.add(HEAD, EYE_SOCKET, 0.45f, 0.2f, 0f, paired = true))
    add(pair("eyes", "blob", EYE_L to on(HEAD, EYE_SOCKET), EYE_R to on(HEAD, EYE_SOCKET), Role.OTHER))
    add(SetRole(EYE_L, Role.OTHER))
    add(SetParameter(EYE_L, "size", 0.22f))
}
