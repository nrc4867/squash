package dev.chieppa.squash.screens.toucan

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.drawn
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.fennec.tuft

val TORSO = PartId("torso")
val HEAD = PartId("head")
val BEAK = PartId("beak")
val BEAK_LOWER = PartId("beak-lower")
val WING_L = PartId("wing-l")
val WING_R = PartId("wing-r")
val LEG_L = PartId("leg-l")
val LEG_R = PartId("leg-r")
val FOOT_L = PartId("foot-l")
val FOOT_R = PartId("foot-r")
val TAIL = PartId("tail")

/** How far the wings swing back toward the body's plane from their template's rest, and down along the sloping back, to lie folded on the flanks. */
private const val WING_FOLDED = -55f
private const val WING_DOWN = -50f

/** The legs at three quarters of the template's length: a toucan perches low. */
private const val LEG_SIZE = 0.75f

/** The template versions the example takes past the first. */
private val NEWER = mapOf("foot-bird" to 3)

private fun ref(id: String) = TemplateRef(TemplateId(id), NEWER[id] ?: 1)

private fun one(id: PartId, template: String, role: Role, on: PartId? = null, socket: String? = null) =
    AddParts(listOf(PartInstance(id, ref(template), on?.let { SocketRef(it, checkNotNull(socket)) }, role = role)))

/** A pair as Add part places it on a paired socket of the bird plan, each side on its own parent. */
private fun pair(id: String, template: String, role: Role, left: PartId, leftOn: PartId, right: PartId, rightOn: PartId, socket: String) = AddParts(
    listOf(
        PartInstance(left, ref(template), SocketRef(leftOn, socket), pair = PairId(id), side = Side.LEFT, role = role),
        PartInstance(right, ref(template), SocketRef(rightOn, socket), pair = PairId(id), side = Side.RIGHT, role = role),
    ),
)

/** The torso in Side, from the vent at the left to the chest at the right along its perched axis, the breast round in front, the back flatter. */
private val TORSO_SIDE = loop(
    p(0.00f, 0.00f), p(0.05f, 0.11f), p(0.16f, 0.18f), p(0.30f, 0.195f), p(0.43f, 0.165f), p(0.52f, 0.10f), p(0.56f, 0.00f),
    p(0.52f, -0.08f), p(0.42f, -0.125f), p(0.26f, -0.14f), p(0.12f, -0.12f), p(0.03f, -0.06f),
)

/** The torso seen from in front: plump through the breast, narrower at the vent and the neck. */
private val TORSO_FRONT = loop(
    p(0.00f, 0.00f), p(0.05f, 0.10f), p(0.17f, 0.155f), p(0.32f, 0.16f), p(0.45f, 0.13f), p(0.54f, 0.07f), p(0.56f, 0.00f),
    p(0.54f, -0.07f), p(0.45f, -0.13f), p(0.32f, -0.16f), p(0.17f, -0.155f), p(0.05f, -0.10f),
)

/** The head in Side, the back of the skull at the left and the face at the right, sitting a little above its axis. */
private val HEAD_SIDE = loop(
    p(0.00f, 0.04f), p(0.03f, 0.11f), p(0.10f, 0.15f), p(0.18f, 0.145f), p(0.24f, 0.10f), p(0.26f, 0.03f),
    p(0.24f, -0.04f), p(0.17f, -0.075f), p(0.08f, -0.07f), p(0.02f, -0.03f),
)
private val HEAD_TOP = loop(
    p(0.00f, 0.00f), p(0.03f, 0.075f), p(0.11f, 0.105f), p(0.19f, 0.095f), p(0.25f, 0.05f), p(0.26f, 0.00f),
    p(0.25f, -0.05f), p(0.19f, -0.095f), p(0.11f, -0.105f), p(0.03f, -0.075f),
)

/** The upper beak in Side: deep at the face, its ridge curving down to a point well past the head, a long banana. */
private val BEAK_SIDE = drawn(
    p(0f, 0.10f), p(0.12f, 0.11f), p(0.26f, 0.085f), p(0.38f, 0.03f), tuft(0.46f, -0.05f),
    p(0.37f, -0.02f), p(0.24f, -0.015f), p(0.11f, -0.035f), p(0f, -0.06f),
)
private val BEAK_TOP = drawn(p(0f, 0.075f), p(0.14f, 0.065f), p(0.30f, 0.04f), tuft(0.46f, 0f), p(0.30f, -0.04f), p(0.14f, -0.065f), p(0f, -0.075f))

/** The lower beak in Side: slimmer, its top tucked under the upper's edge and following its curve to a point just under the upper's. */
private val BEAK_LOWER_SIDE = drawn(
    p(0f, 0.04f), p(0.12f, 0.05f), p(0.24f, 0.045f), p(0.34f, 0.02f), tuft(0.42f, -0.03f), p(0.33f, -0.02f), p(0.2f, -0.02f), p(0.08f, -0.035f), p(0f, -0.045f),
)
private val BEAK_LOWER_TOP = drawn(p(0f, 0.065f), p(0.14f, 0.055f), p(0.30f, 0.03f), tuft(0.42f, 0f), p(0.30f, -0.03f), p(0.14f, -0.055f), p(0f, -0.065f))

/** The folded wing's planform, drawn in Top: a rounded shoulder along the back, primaries as tufts on the trailing edge toward the tail. */
private val WING_TOP = drawn(
    p(0f, 0.06f), p(0.12f, 0.09f), p(0.28f, 0.08f), p(0.40f, 0.03f), tuft(0.47f, -0.04f),
    p(0.42f, -0.07f), tuft(0.41f, -0.11f), p(0.35f, -0.10f), tuft(0.33f, -0.15f), p(0.26f, -0.13f), tuft(0.22f, -0.16f),
    p(0.13f, -0.13f), p(0f, -0.06f),
)

/** The wing's thickness, drawn in Side: thin, fullest over the arm bones, to a point at the tip. */
private val WING_SIDE = drawn(p(0f, 0.03f), p(0.18f, 0.035f), p(0.36f, 0.02f), p(0.47f, 0f), p(0.36f, -0.02f), p(0.18f, -0.035f), p(0f, -0.03f))

/** The tail fanned in Top, square-ended, and thin in Side. */
private val TAIL_TOP = drawn(p(0f, 0.05f), p(0.10f, 0.065f), p(0.22f, 0.08f), p(0.29f, 0.07f), tuft(0.31f, 0f), p(0.29f, -0.07f), p(0.22f, -0.08f), p(0.10f, -0.065f), p(0f, -0.05f))
private val TAIL_SIDE = drawn(p(0f, 0.035f), p(0.12f, 0.025f), p(0.24f, 0.02f), p(0.31f, 0f), p(0.24f, -0.02f), p(0.12f, -0.025f), p(0f, -0.035f))

/**
 * The toucan's shape on the bird plan: a plump perched torso drawn in Side and from in front, a round head, a long banana beak in two
 * halves drawn in Side and Top, wings folded along the flanks with their planform drawn in Top, the bird legs short and the feet at the
 * template's own shape and a square fan of a tail. Every part takes the role Add part gives it there.
 */
fun toucanBody(): List<DocumentCommand> = listOf(
    one(TORSO, "torso-bird", Role.BODY),
    one(HEAD, "head-bird", Role.HEAD, TORSO, "neck"),
    one(BEAK, "beak", Role.JAW, HEAD, "muzzle"),
    one(BEAK_LOWER, "beak-lower", Role.JAW, BEAK, "lower"),
    pair("wings", "wing-feathered", Role.WING, WING_L, TORSO, WING_R, TORSO, "shoulder"),
    pair("legs", "leg-bird", Role.LEG, LEG_L, TORSO, LEG_R, TORSO, "hip"),
    pair("feet", "foot-bird", Role.FOOT, FOOT_L, LEG_L, FOOT_R, LEG_R, "foot"),
    one(TAIL, "tail-feathers", Role.TAIL, TORSO, "tail"),
    SetOutlines(TORSO, mapOf(View.SIDE to TORSO_SIDE, View.TOP to TORSO_FRONT)),
    SetOutlines(HEAD, mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP)),
    SetOutlines(BEAK, mapOf(View.SIDE to BEAK_SIDE, View.TOP to BEAK_TOP)),
    SetOutlines(BEAK_LOWER, mapOf(View.SIDE to BEAK_LOWER_SIDE, View.TOP to BEAK_LOWER_TOP)),
    SetOutlines(WING_L, mapOf(View.TOP to WING_TOP, View.SIDE to WING_SIDE)),
    SetParameter(WING_L, PlacementParams.OUTWARD, WING_FOLDED),
    SetParameter(WING_L, PlacementParams.LEAN, WING_DOWN),
    SetParameter(LEG_L, "size", LEG_SIZE),
    SetOutlines(TAIL, mapOf(View.TOP to TAIL_TOP, View.SIDE to TAIL_SIDE)),
)
