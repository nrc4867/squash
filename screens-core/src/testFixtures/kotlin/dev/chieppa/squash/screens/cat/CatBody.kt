package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.drawn
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.fennec.pair
import dev.chieppa.squash.screens.fennec.ref
import dev.chieppa.squash.screens.fennec.tuft

val CAT_TORSO = PartId("torso")
val CAT_HEAD = PartId("head")
val CAT_TAIL = PartId("tail")
val CAT_EAR_L = PartId("ear-l")
val CAT_EAR_R = PartId("ear-r")
val CAT_FRONT_LEG_L = PartId("front-leg-l")
val CAT_FRONT_LEG_R = PartId("front-leg-r")
val CAT_BACK_LEG_L = PartId("back-leg-l")
val CAT_BACK_LEG_R = PartId("back-leg-r")
val CAT_FRONT_PAW_L = PartId("front-paw-l")
val CAT_FRONT_PAW_R = PartId("front-paw-r")
val CAT_BACK_PAW_L = PartId("back-paw-l")
val CAT_BACK_PAW_R = PartId("back-paw-r")

/** The torso's drawn length, which turns its drawn x into the u a garment branch is given in. */
const val CAT_TORSO_LENGTH = 0.88f

/** The body from the side, rump to chest, back up: a soft round rump, a level back rising to the shoulders, a low belly. */
private val TORSO_SIDE = loop(
    p(0.00f, 0.05f), p(0.05f, 0.19f), p(0.17f, 0.25f), p(0.35f, 0.24f), p(0.53f, 0.23f), p(0.68f, 0.26f), p(0.79f, 0.32f),
    p(0.86f, 0.29f), p(0.88f, 0.12f), p(0.82f, -0.09f), p(0.67f, -0.18f), p(0.46f, -0.21f), p(0.25f, -0.19f), p(0.08f, -0.12f),
)

/** The body from above: broad hips and a broad chest with a slight waist between. */
private val TORSO_TOP = loop(
    p(0.00f, 0.00f), p(0.06f, 0.15f), p(0.20f, 0.19f), p(0.42f, 0.17f), p(0.64f, 0.18f), p(0.80f, 0.14f), p(0.88f, 0.00f),
    p(0.80f, -0.14f), p(0.64f, -0.18f), p(0.42f, -0.17f), p(0.20f, -0.19f), p(0.06f, -0.15f),
)

/** The body's section: an egg, a round back swelling to full flanks and narrowing a little to a soft belly. */
private val TORSO_FRONT = loop(
    p(0f, 1f), p(0.70f, 0.78f), p(1f, 0.15f), p(0.90f, -0.48f), p(0.55f, -0.90f), p(0f, -1f), p(-0.55f, -0.90f), p(-0.90f, -0.48f),
    p(-1f, 0.15f), p(-0.70f, 0.78f),
)

/** The head from the side, forward along x: a round skull, a short muzzle and a small chin under it. */
private val HEAD_SIDE = loop(
    p(0.00f, 0.02f), p(0.04f, 0.18f), p(0.15f, 0.28f), p(0.29f, 0.28f), p(0.39f, 0.21f), p(0.43f, 0.11f), p(0.48f, 0.06f),
    p(0.50f, 0.00f), p(0.48f, -0.06f), p(0.40f, -0.11f), p(0.24f, -0.16f), p(0.08f, -0.14f),
)

/** The head from above: full cheeks, narrowing to a small blunt muzzle. */
private val HEAD_TOP = loop(
    p(0.00f, 0.00f), p(0.04f, 0.18f), p(0.16f, 0.29f), p(0.30f, 0.28f), p(0.40f, 0.20f), p(0.46f, 0.11f), p(0.50f, 0.00f),
    p(0.46f, -0.11f), p(0.40f, -0.20f), p(0.30f, -0.28f), p(0.16f, -0.29f), p(0.04f, -0.18f),
)

/** An ear: thin, a broad base to a point. */
private val EAR_SIDE = drawn(p(0f, 0.035f), p(0.12f, 0.03f), p(0.22f, 0.018f), p(0.27f, 0f), p(0.22f, -0.018f), p(0.12f, -0.03f), p(0f, -0.035f))
private val EAR_TOP = drawn(p(0f, 0.15f), p(0.08f, 0.14f), p(0.17f, 0.09f), tuft(0.27f, 0f), p(0.17f, -0.09f), p(0.08f, -0.14f), p(0f, -0.15f))

/** The front leg: a full forearm narrowing a little to the wrist. */
private val FRONT_LEG_SIDE = drawn(p(0f, 0.09f), p(0.11f, 0.08f), p(0.24f, 0.064f), p(0.35f, 0.058f), p(0.39f, 0f), p(0.35f, -0.06f), p(0.24f, -0.066f), p(0.11f, -0.09f), p(0f, -0.105f))
private val FRONT_LEG_TOP = drawn(p(0f, 0.072f), p(0.16f, 0.068f), p(0.34f, 0.058f), p(0.39f, 0f), p(0.34f, -0.058f), p(0.16f, -0.068f), p(0f, -0.072f))

/** The back leg: a big round thigh down to a slim hock. */
private val BACK_LEG_SIDE = drawn(
    p(0f, 0.14f), p(0.10f, 0.13f), p(0.20f, 0.07f), p(0.29f, 0.05f), p(0.37f, 0.056f), p(0.41f, 0f), p(0.37f, -0.056f), p(0.29f, -0.07f),
    p(0.20f, -0.13f), p(0.10f, -0.18f), p(0f, -0.16f),
)
private val BACK_LEG_TOP = drawn(p(0f, 0.09f), p(0.15f, 0.085f), p(0.33f, 0.058f), p(0.41f, 0f), p(0.33f, -0.058f), p(0.15f, -0.085f), p(0f, -0.09f))

/** The tail: long and even, a little fuller at the root, ending round. */
private val TAIL_SIDE = drawn(
    p(0f, 0.05f), p(0.12f, 0.048f), p(0.40f, 0.044f), p(0.70f, 0.042f), p(0.84f, 0.036f), p(0.90f, 0f),
    p(0.84f, -0.036f), p(0.70f, -0.042f), p(0.40f, -0.044f), p(0.12f, -0.048f), p(0f, -0.05f),
)
private val TAIL_TOP = TAIL_SIDE

/** Every paw is a mitten: four toes merged into one soft mass with shallow lobes, claws drawn in, beans as soft bumps underneath. */
private val MITTEN = mapOf(
    "toes" to 4f,
    "merge" to 1.4f,
    "toeLength" to 0.8f,
    "toeThickness" to 1.15f,
    "claws" to 0f,
    "beans" to 0.25f,
    "width" to 0.21f,
    "height" to 0.16f,
)

/**
 * The parts on the canine plan's sockets: a round body, a big round head with a short muzzle, upright pointed ears, a long tail, sturdy
 * legs and mitten paws. The front legs and paws take a four-legged body's arm and hand roles.
 */
fun catBody(): List<DocumentCommand> = listOf(
    AddParts(listOf(PartInstance(CAT_TORSO, ref("torso"), role = Role.BODY))),
    AddParts(
        listOf(
            PartInstance(CAT_HEAD, ref("head"), SocketRef(CAT_TORSO, "neck"), role = Role.HEAD),
            PartInstance(CAT_TAIL, ref("tail"), SocketRef(CAT_TORSO, "tail"), role = Role.TAIL),
        ),
    ),
    pair("ears", "ear", Role.EAR, CAT_EAR_L, CAT_HEAD, CAT_EAR_R, CAT_HEAD, "ear"),
    pair("front-legs", "leg", Role.ARM, CAT_FRONT_LEG_L, CAT_TORSO, CAT_FRONT_LEG_R, CAT_TORSO, "front-leg"),
    pair("back-legs", "leg", Role.LEG, CAT_BACK_LEG_L, CAT_TORSO, CAT_BACK_LEG_R, CAT_TORSO, "back-leg"),
    pair("front-paws", "paw", Role.HAND, CAT_FRONT_PAW_L, CAT_FRONT_LEG_L, CAT_FRONT_PAW_R, CAT_FRONT_LEG_R, "paw"),
    pair("back-paws", "paw", Role.FOOT, CAT_BACK_PAW_L, CAT_BACK_LEG_L, CAT_BACK_PAW_R, CAT_BACK_LEG_R, "paw"),
    SetOutlines(CAT_TORSO, mapOf(View.SIDE to TORSO_SIDE, View.TOP to TORSO_TOP, View.FRONT to TORSO_FRONT)),
    SetOutlines(CAT_HEAD, mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP)),
    SetParameter(CAT_HEAD, "size", 1.12f),
    SetOutlines(CAT_EAR_L, mapOf(View.SIDE to EAR_SIDE, View.TOP to EAR_TOP)),
    SetParameter(CAT_EAR_L, "cup", 0.35f),
    SetParameter(CAT_EAR_L, PlacementParams.OUTWARD, 12f),
    SetOutlines(CAT_TAIL, mapOf(View.SIDE to TAIL_SIDE, View.TOP to TAIL_TOP)),
    SetParameter(CAT_TAIL, PlacementParams.LEAN, -40f),
    SetOutlines(CAT_FRONT_LEG_L, mapOf(View.SIDE to FRONT_LEG_SIDE, View.TOP to FRONT_LEG_TOP)),
    SetOutlines(CAT_BACK_LEG_L, mapOf(View.SIDE to BACK_LEG_SIDE, View.TOP to BACK_LEG_TOP)),
) + listOf(CAT_FRONT_PAW_L, CAT_BACK_PAW_L).flatMap { paw -> MITTEN.map { (name, value) -> SetParameter(paw, name, value) } }
