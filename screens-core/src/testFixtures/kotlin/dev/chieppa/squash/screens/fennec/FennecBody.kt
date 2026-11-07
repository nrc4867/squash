package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View

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

/** The newest shipped version of [id], which a part placed now takes. */
private fun ref(id: String): TemplateRef = requireNotNull(shippedTemplates.template(TemplateId(id))) { "No template $id" }.ref

private val shippedTemplates by lazy { TemplateFolder.shipped() }

private fun pair(id: String, template: String, left: PartId, leftOn: PartId, right: PartId, rightOn: PartId, socket: String) = AddParts(
    listOf(
        PartInstance(left, ref(template), SocketRef(leftOn, socket), pair = PairId(id), side = Side.LEFT),
        PartInstance(right, ref(template), SocketRef(rightOn, socket), pair = PairId(id), side = Side.RIGHT),
    ),
)

private val TORSO_SIDE = loop(
    p(0.00f, 0.03f), p(0.05f, 0.16f), p(0.18f, 0.22f), p(0.38f, 0.20f), p(0.56f, 0.22f), p(0.70f, 0.32f), p(0.82f, 0.44f),
    p(0.88f, 0.36f), p(0.89f, 0.18f), p(0.81f, -0.05f), p(0.64f, -0.15f), p(0.41f, -0.12f), p(0.21f, -0.14f), p(0.06f, -0.10f),
)
private val TORSO_TOP = loop(
    p(0.00f, 0.00f), p(0.07f, 0.13f), p(0.23f, 0.16f), p(0.43f, 0.14f), p(0.66f, 0.17f), p(0.82f, 0.13f), p(0.89f, 0.00f),
    p(0.82f, -0.13f), p(0.66f, -0.17f), p(0.43f, -0.14f), p(0.23f, -0.16f), p(0.07f, -0.13f),
)

private val TORSO_FRONT = loop(
    p(0f, 1f), p(0.62f, 0.86f), p(0.95f, 0.35f), p(1f, -0.2f), p(0.88f, -0.72f), p(0.45f, -1f), p(-0.45f, -1f), p(-0.88f, -0.72f), p(-1f, -0.2f), p(-0.95f, 0.35f), p(-0.62f, 0.86f),
)

private val HEAD_SIDE = loop(
    p(0.00f, 0.03f), p(0.05f, 0.17f), p(0.18f, 0.25f), p(0.33f, 0.23f), p(0.44f, 0.12f), p(0.60f, 0.05f), p(0.70f, 0.01f),
    p(0.71f, -0.03f), p(0.60f, -0.07f), p(0.42f, -0.10f), p(0.24f, -0.17f), p(0.08f, -0.13f),
)
private val HEAD_TOP = loop(
    p(0.00f, 0.00f), p(0.05f, 0.17f), p(0.20f, 0.27f), p(0.33f, 0.22f), p(0.46f, 0.09f), p(0.62f, 0.05f), p(0.71f, 0.00f),
    p(0.62f, -0.05f), p(0.46f, -0.09f), p(0.33f, -0.22f), p(0.20f, -0.27f), p(0.05f, -0.17f),
)

private val EAR_SIDE = drawn(p(0f, 0.045f), p(0.25f, 0.04f), p(0.55f, 0.03f), p(0.72f, 0f), p(0.55f, -0.03f), p(0.25f, -0.04f), p(0f, -0.045f))
private val EAR_TOP = drawn(p(0f, 0.17f), p(0.18f, 0.21f), p(0.42f, 0.16f), p(0.62f, 0.07f), tuft(0.72f, 0f), p(0.62f, -0.07f), p(0.42f, -0.16f), p(0.18f, -0.21f), p(0f, -0.17f))

private val FRONT_LEG_SIDE = drawn(p(0f, 0.08f), p(0.14f, 0.07f), p(0.32f, 0.045f), p(0.527f, 0.045f), p(0.567f, 0f), p(0.527f, -0.05f), p(0.32f, -0.05f), p(0.14f, -0.08f), p(0f, -0.10f))
private val FRONT_LEG_TOP = drawn(p(0f, 0.055f), p(0.2f, 0.052f), p(0.5f, 0.04f), p(0.567f, 0f), p(0.5f, -0.04f), p(0.2f, -0.052f), p(0f, -0.055f))
private val BACK_LEG_SIDE = drawn(p(0f, 0.11f), p(0.14f, 0.09f), p(0.28f, 0.02f), p(0.40f, 0.03f), p(0.50f, 0.045f), p(0.54f, 0f), p(0.50f, -0.04f), p(0.40f, -0.06f), p(0.28f, -0.10f), p(0.14f, -0.14f), p(0f, -0.13f))
private val BACK_LEG_TOP = drawn(p(0f, 0.065f), p(0.2f, 0.065f), p(0.5f, 0.04f), p(0.54f, 0f), p(0.5f, -0.04f), p(0.2f, -0.065f), p(0f, -0.065f))

private val PAW_SIDE = loop(p(0f, 0f), p(0.05f, 0.05f), p(0.15f, 0.06f), p(0.25f, 0.035f), p(0.28f, -0.01f), p(0.22f, -0.045f), p(0.08f, -0.045f))
private val PAW_TOP = loop(p(0f, 0f), p(0.05f, 0.06f), p(0.16f, 0.08f), p(0.26f, 0.05f), p(0.28f, 0f), p(0.26f, -0.05f), p(0.16f, -0.08f), p(0.05f, -0.06f))

private val TAIL_SIDE = drawn(
    p(0f, 0.05f), p(0.08f, 0.08f), p(0.20f, 0.125f), p(0.36f, 0.16f), p(0.50f, 0.16f), p(0.64f, 0.12f), p(0.76f, 0.06f), tuft(0.84f, 0f),
    p(0.76f, -0.06f), p(0.64f, -0.12f), p(0.50f, -0.17f), p(0.36f, -0.17f), p(0.20f, -0.135f), p(0.08f, -0.09f), p(0f, -0.05f),
)
private val TAIL_TOP = drawn(
    p(0f, 0.05f), p(0.08f, 0.08f), p(0.20f, 0.115f), p(0.36f, 0.14f), p(0.50f, 0.14f), p(0.64f, 0.105f), p(0.76f, 0.05f), tuft(0.84f, 0f),
    p(0.76f, -0.05f), p(0.64f, -0.105f), p(0.50f, -0.14f), p(0.36f, -0.14f), p(0.20f, -0.115f), p(0.08f, -0.08f), p(0f, -0.05f),
)

/** The parts on the canine plan's sockets, drawn from outlines only, with the Move tool's lean on the tail and ears. */
fun fennecBody(): List<DocumentCommand> = listOf(
    AddParts(listOf(PartInstance(TORSO, ref("torso")))),
    AddParts(listOf(PartInstance(HEAD, ref("head"), SocketRef(TORSO, "neck")), PartInstance(TAIL, ref("tail"), SocketRef(TORSO, "tail")))),
    pair("ears", "ear", EAR_L, HEAD, EAR_R, HEAD, "ear"),
    pair("front-legs", "leg", FRONT_LEG_L, TORSO, FRONT_LEG_R, TORSO, "front-leg"),
    pair("back-legs", "leg", BACK_LEG_L, TORSO, BACK_LEG_R, TORSO, "back-leg"),
    pair("front-paws", "paw", FRONT_PAW_L, FRONT_LEG_L, FRONT_PAW_R, FRONT_LEG_R, "paw"),
    pair("back-paws", "paw", BACK_PAW_L, BACK_LEG_L, BACK_PAW_R, BACK_LEG_R, "paw"),
    SetOutlines(TORSO, mapOf(View.SIDE to TORSO_SIDE, View.TOP to TORSO_TOP, View.FRONT to TORSO_FRONT)),
    SetOutlines(HEAD, mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP)),
    SetParameter(HEAD, "size", 1.12f),
    SetOutlines(EAR_L, mapOf(View.SIDE to EAR_SIDE, View.TOP to EAR_TOP)),
    SetParameter(EAR_L, "cup", 0.3f),
    SetParameter(EAR_L, PlacementParams.OUTWARD, 16f),
    SetOutlines(TAIL, mapOf(View.SIDE to TAIL_SIDE, View.TOP to TAIL_TOP)),
    SetParameter(TAIL, PlacementParams.LEAN, -55f),
    SetOutlines(FRONT_LEG_L, mapOf(View.SIDE to FRONT_LEG_SIDE, View.TOP to FRONT_LEG_TOP)),
    SetOutlines(BACK_LEG_L, mapOf(View.SIDE to BACK_LEG_SIDE, View.TOP to BACK_LEG_TOP)),
    SetOutlines(FRONT_PAW_L, mapOf(View.SIDE to PAW_SIDE, View.TOP to PAW_TOP)),
    SetOutlines(BACK_PAW_L, mapOf(View.SIDE to PAW_SIDE, View.TOP to PAW_TOP)),
    SetParameter(FRONT_PAW_L, "toes", 3f),
    SetParameter(BACK_PAW_L, "toes", 3f),
)
