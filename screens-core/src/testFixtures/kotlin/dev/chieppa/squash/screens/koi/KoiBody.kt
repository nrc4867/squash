package dev.chieppa.squash.screens.koi

import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Limits
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
import dev.chieppa.squash.screens.fennec.tuft

val KOI_BODY = PartId("body")
val DORSAL = PartId("dorsal")
val TAIL_FIN = PartId("tail-fin")
val PECTORAL_L = PartId("pectoral-l")
val PECTORAL_R = PartId("pectoral-r")
val ANAL = PartId("anal")
val EYE_L = PartId("eye-l")
val EYE_R = PartId("eye-r")

/** How far each of the body's hand-made joints bends either way, in degrees. */
private val BODY_BEND = Limits(-35f, 35f)

/** The fish plan's sockets on the body. */
const val DORSAL_SOCKET = "dorsal"
const val TAIL_SOCKET = "tail"
const val PECTORAL_SOCKET = "pectoral"
const val ANAL_SOCKET = "anal"
const val EYE_SOCKET = "eye"

private fun ref(id: String) = TemplateRef(TemplateId(id), 1)

private fun on(socket: String) = SocketRef(KOI_BODY, socket)

/** A part as Add part places it on a fish plan socket: a generic template, its role a guess until it is chosen. */
private fun guessed(id: PartId, template: String, socket: String, role: Role) = PartInstance(id, ref(template), on(socket), role = role, roleGuessed = true)

private fun finPair() = AddParts(
    listOf(
        PartInstance(PECTORAL_L, ref("fin"), on(PECTORAL_SOCKET), pair = PairId("pectorals"), side = Side.LEFT, role = Role.FIN),
        PartInstance(PECTORAL_R, ref("fin"), on(PECTORAL_SOCKET), pair = PairId("pectorals"), side = Side.RIGHT, role = Role.FIN),
    ),
)

private fun pair(id: String, template: String, socket: String, role: Role, left: PartId, right: PartId) = AddParts(
    listOf(
        PartInstance(left, ref(template), on(socket), pair = PairId(id), side = Side.LEFT, role = role, roleGuessed = true),
        PartInstance(right, ref(template), on(socket), pair = PairId(id), side = Side.RIGHT, role = role, roleGuessed = true),
    ),
)

/** The body in Side: a deep back that arches over the shoulders and tapers to a slim tail stalk, a flatter belly, a blunt snout. */
private val BODY_SIDE = loop(
    p(0.00f, 0.015f), p(0.07f, 0.04f), p(0.20f, 0.09f), p(0.36f, 0.14f), p(0.52f, 0.165f), p(0.67f, 0.155f), p(0.79f, 0.12f),
    p(0.88f, 0.065f), p(0.92f, 0.01f), p(0.90f, -0.04f), p(0.81f, -0.085f), p(0.64f, -0.115f), p(0.46f, -0.115f), p(0.29f, -0.09f),
    p(0.13f, -0.05f), p(0.03f, -0.02f),
)

/** The body in Top: narrower than it is deep, widest behind the head. */
private val BODY_TOP = loop(
    p(0.00f, 0.00f), p(0.07f, 0.025f), p(0.22f, 0.055f), p(0.42f, 0.085f), p(0.62f, 0.09f), p(0.77f, 0.075f), p(0.87f, 0.045f), p(0.92f, 0.00f),
    p(0.87f, -0.045f), p(0.77f, -0.075f), p(0.62f, -0.09f), p(0.42f, -0.085f), p(0.22f, -0.055f), p(0.07f, -0.025f),
)

/** A fin's thickness: thin at its root, to an edge at its tip. */
private fun thin(reach: Float, root: Float) = drawn(p(0f, root), p(reach * 0.5f, root * 0.7f), p(reach, 0f), p(reach * 0.5f, -root * 0.7f), p(0f, -root))

/** The dorsal fin's planform in Side: a long base along the back, swept back to a rounded peak. */
private val DORSAL_SIDE = drawn(p(0f, 0.13f), p(0.05f, 0.11f), p(0.10f, 0.07f), p(0.13f, 0.02f), p(0.12f, -0.03f), p(0.07f, -0.07f), p(0f, -0.09f))

/** The tail fin's planform in Side: a narrow root fanning out to two broad lobes. */
private val TAIL_SIDE = drawn(
    p(0f, 0.035f), p(0.06f, 0.07f), p(0.13f, 0.13f), tuft(0.19f, 0.17f), p(0.18f, 0.09f), p(0.16f, 0.02f), p(0.16f, -0.02f),
    p(0.18f, -0.09f), tuft(0.19f, -0.17f), p(0.13f, -0.13f), p(0.06f, -0.07f), p(0f, -0.035f),
)

/** A pectoral fin's planform in Top, as it lies out from the side: a rounded paddle. */
private val PECTORAL_TOP = drawn(p(0f, 0.03f), p(0.05f, 0.05f), p(0.10f, 0.045f), p(0.13f, 0.01f), p(0.12f, -0.03f), p(0.07f, -0.045f), p(0f, -0.03f))

/** The anal fin's planform in Side: small and swept back. */
private val ANAL_SIDE = drawn(p(0f, 0.05f), p(0.04f, 0.045f), p(0.075f, 0.02f), p(0.08f, -0.01f), p(0.05f, -0.03f), p(0f, -0.035f))

/**
 * The koi's shape on the fish plan: its body tube drawn in Side and Top with three hand-made joints along it, so the body curve
 * bends it along its length, a dorsal fin on the plan's back socket, a pair of pectoral fins low on the sides behind the head, an
 * anal fin underneath and a tail fin at the rear, each a fin or sheet drawn as a planform in its best view and its thickness in the
 * other, turned into place with Move's angles; and a pair of blob eyes on the head. A fin takes the role its template gives it, the
 * tail's sheet and the eyes' blob the guess Add part gives them, then chosen.
 */
fun koiBody(): List<DocumentCommand> = listOf(
    AddParts(listOf(PartInstance(KOI_BODY, ref("tube"), role = Role.BODY))),
    SetOutlines(KOI_BODY, mapOf(View.SIDE to BODY_SIDE, View.TOP to BODY_TOP)),
    HandJoints.add(KOI_BODY, 1, 0.3f, Hinge.X, BODY_BEND, stretch = false),
    HandJoints.add(KOI_BODY, 2, 0.5f, Hinge.X, BODY_BEND, stretch = false),
    HandJoints.add(KOI_BODY, 3, 0.7f, Hinge.X, BODY_BEND, stretch = false),
    AddParts(listOf(PartInstance(DORSAL, ref("fin"), on(DORSAL_SOCKET), role = Role.FIN))),
    AddParts(listOf(guessed(TAIL_FIN, "sheet", TAIL_SOCKET, Role.TAIL))),
    finPair(),
    AddParts(listOf(PartInstance(ANAL, ref("fin"), on(ANAL_SOCKET), role = Role.FIN))),
    pair("eyes", "blob", EYE_SOCKET, Role.OTHER, EYE_L, EYE_R),
    SetRole(TAIL_FIN, Role.TAIL),
    SetRole(EYE_L, Role.OTHER),
    SetOutlines(DORSAL, mapOf(View.SIDE to DORSAL_SIDE, View.TOP to thin(0.13f, 0.012f))),
    SetParameter(DORSAL, PlacementParams.LEAN, 55f),
    SetOutlines(TAIL_FIN, mapOf(View.SIDE to TAIL_SIDE, View.TOP to thin(0.19f, 0.014f))),
    SetOutlines(PECTORAL_L, mapOf(View.TOP to PECTORAL_TOP, View.SIDE to thin(0.13f, 0.01f))),
    SetParameter(PECTORAL_L, PlacementParams.OUTWARD, 50f),
    SetParameter(PECTORAL_L, PlacementParams.LEAN, -20f),
    SetOutlines(ANAL, mapOf(View.SIDE to ANAL_SIDE, View.TOP to thin(0.08f, 0.008f))),
    SetParameter(ANAL, PlacementParams.LEAN, -50f),
    SetParameter(EYE_L, "size", 0.38f),
)
