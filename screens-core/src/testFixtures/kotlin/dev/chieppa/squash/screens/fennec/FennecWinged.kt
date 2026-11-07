package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.readDocument

/** Where the saved winged fennec lies among the test fixtures' resources. */
const val FENNEC_WINGED_RESOURCE = "fennec-winged.json"

val WING_L = PartId("wing-l")
val WING_R = PartId("wing-r")

/** The hand-made socket on the torso's shoulder the wings sit on. */
const val WING_SOCKET = 1

private const val SHOULDER_U = 0.72f
private const val SHOULDER_V = 0.17f

/** How far the wings swing back from their template's rest, toward the body's plane, to lie along the flanks. */
private const val FOLDED = -50f

/** The planform, drawn in Top: a leading edge along the back, primaries as tufts on the trailing edge down the flank. */
private val WING_TOP = drawn(
    p(0f, 0.05f), p(0.12f, 0.08f), p(0.30f, 0.07f), p(0.46f, 0.03f), tuft(0.58f, -0.05f),
    p(0.53f, -0.09f), tuft(0.52f, -0.14f), p(0.46f, -0.13f), tuft(0.43f, -0.19f), p(0.36f, -0.17f), tuft(0.32f, -0.22f),
    p(0.24f, -0.20f), tuft(0.19f, -0.22f), p(0.10f, -0.16f), p(0f, -0.06f),
)

/** The thickness, drawn in Side: thin, thickest over the arm bones, to a point at the tip. */
private val WING_SIDE = drawn(p(0f, 0.025f), p(0.2f, 0.03f), p(0.45f, 0.02f), p(0.58f, 0f), p(0.45f, -0.02f), p(0.2f, -0.03f), p(0f, -0.025f))

/** Where each feather line crosses the wing, along it and from the leading edge toward the trailing one, on both faces. */
private val FEATHERS = listOf(0.34f, 0.5f, 0.64f, 0.78f)
private const val FEATHER_SLANT = 0.08f
private val FEATHER_ACROSS = listOf(0.08f, 0.18f, 0.3f, 0.42f)

private fun feather(id: String, u: Float, face: (Float) -> Float) = SetMark(
    WING_L, MarkId(id),
    Mark(MarkId(id), FEATHER_ACROSS.mapIndexed { i, v -> Anchor(WING_L, BODY_PATCH, u - FEATHER_SLANT * i / FEATHER_ACROSS.lastIndex, face(v)) }),
)

/**
 * Wings on the fennec: a paired hand-made socket on the torso's shoulder, a wing pair on it swung back to lie along the flanks, its
 * planform drawn in Top and its thickness in Side, in the fur slot with a cream tip, and feather lines on both faces.
 */
fun fennecWings(): List<DocumentCommand> = listOf(
    HandSockets.add(TORSO, WING_SOCKET, SHOULDER_U, SHOULDER_V, 0f, paired = true),
    pair("wings", "wing-feathered", Role.WING, WING_L, TORSO, WING_R, TORSO, HandSockets.name(WING_SOCKET)),
    SetParameter(WING_L, PlacementParams.OUTWARD, FOLDED),
    SetOutlines(WING_L, mapOf(View.TOP to WING_TOP, View.SIDE to WING_SIDE)),
    SetBaseSlot(WING_L, FUR),
    SetRegion(WING_L, RegionId("tip"), Region(RegionId("tip"), Array(16) { i -> Anchor(WING_L, BODY_PATCH, 0.8f + (if (i % 2 == 0) 0.015f else -0.015f), i / 16f) }.toList(), CREAM)),
) + FEATHERS.mapIndexed { i, u -> feather("feather-$i", u) { it } } + FEATHERS.mapIndexed { i, u -> feather("feather-under-$i", u) { 1f - it } }

/** Every command that makes the winged fennec: the fennec's, then its wings'. */
fun fennecWingedCommands(): List<DocumentCommand> = fennecCommands() + fennecWings()

/** The winged fennec as those commands leave it. */
fun fennecWingedDocument(): SquashDocument = fennecWingedCommands().fold(EMPTY_FENNEC) { document, command -> command.apply(document).document }

/** The winged fennec from the example file. */
fun savedFennecWinged(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(FENNEC_WINGED_RESOURCE)) { "No $FENNEC_WINGED_RESOURCE" }.readText())
