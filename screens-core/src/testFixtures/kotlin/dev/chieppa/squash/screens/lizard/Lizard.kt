package dev.chieppa.squash.screens.lizard

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where the saved lizard lies among the test fixtures' resources. */
const val LIZARD_RESOURCE = "lizard.json"

val SKIN = SlotName("skin")
val BELLY = SlotName("belly")
val STRIPE = SlotName("stripe")
val EYE = SlotName("eye")
val PUPIL = SlotName("pupil")

private const val LOOP_POINTS = 12

/** Where the body's and the tail's stripes cross their backs, along u, and how far round from the top each reaches. */
private val BODY_STRIPES = listOf(0.2f, 0.34f, 0.48f, 0.62f, 0.76f)
private val TAIL_STRIPES = listOf(0.15f, 0.32f, 0.5f, 0.68f)
private const val STRIPE_HALF_U = 0.025f
private const val STRIPE_HALF_V = 0.17f

/** The character before any command: a name on the Blank plan. */
val EMPTY_LIZARD = SquashDocument(Character(CharacterId("lizard"), "Lizard", BodyPlanRef(BodyPlanId("blank"), 1)))

/** A loop of anchors on [part] round ([u], [v]), [du] along it and [dv] round it. */
private fun spot(part: PartId, id: String, slot: SlotName, u: Float, v: Float, du: Float, dv: Float) = SetRegion(
    part, RegionId(id),
    Region(RegionId(id), List(LOOP_POINTS) { i -> (2.0 * PI * i / LOOP_POINTS).let { Anchor(part, BODY_PATCH, u + du * cos(it).toFloat(), v + dv * sin(it).toFloat()) } }, slot),
)

/** Dark bands across [part]'s back at each of [at], over the top from side to side. */
private fun stripes(part: PartId, at: List<Float>): List<DocumentCommand> =
    at.mapIndexed { i, u -> spot(part, "stripe-$i", STRIPE, u, 0f, STRIPE_HALF_U, STRIPE_HALF_V) }

/**
 * The lizard's colors: green skin with a paler belly along the body's and the tail's undersides and dark stripes across the back
 * and the tail, each a region; the legs, feet and head the skin's green, and yellow eyes with a dark pupil as a region on each.
 */
fun lizardColor(): List<DocumentCommand> = listOf(
    SetPaletteSlot(SKIN, SlotColor(0.36f, 0.62f, 0.27f)),
    SetPaletteSlot(BELLY, SlotColor(0.80f, 0.87f, 0.56f)),
    SetPaletteSlot(STRIPE, SlotColor(0.16f, 0.30f, 0.13f)),
    SetPaletteSlot(EYE, SlotColor(0.95f, 0.80f, 0.25f)),
    SetPaletteSlot(PUPIL, SlotColor(0.08f, 0.06f, 0.07f)),
    SetBaseSlot(LIZARD_BODY, SKIN),
    SetBaseSlot(TAIL, SKIN),
    SetBaseSlot(HEAD, SKIN),
    SetBaseSlot(FRONT_LEG_L, SKIN),
    SetBaseSlot(BACK_LEG_L, SKIN),
    SetBaseSlot(FRONT_FOOT_L, SKIN),
    SetBaseSlot(BACK_FOOT_L, SKIN),
    SetBaseSlot(EYE_L, EYE),
    spot(LIZARD_BODY, "belly", BELLY, 0.5f, 0.5f, 0.42f, 0.16f),
    spot(TAIL, "belly", BELLY, 0.3f, 0.5f, 0.28f, 0.12f),
    spot(HEAD, "chin", BELLY, 0.4f, 0.5f, 0.3f, 0.14f),
    spot(EYE_L, "pupil", PUPIL, 0.6f, 0.25f, 0.22f, 0.1f),
) + stripes(LIZARD_BODY, BODY_STRIPES) + stripes(TAIL, TAIL_STRIPES)

/** Every command that makes the lizard, in the order a person would send them. */
fun lizardCommands(): List<DocumentCommand> = lizardBody() + lizardColor()

/** The lizard as those commands leave it. */
fun lizardDocument(): SquashDocument = lizardCommands().fold(EMPTY_LIZARD) { document, command -> command.apply(document).document }

/** The lizard from the example file. */
fun savedLizard(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(LIZARD_RESOURCE)) { "No $LIZARD_RESOURCE" }.readText())
