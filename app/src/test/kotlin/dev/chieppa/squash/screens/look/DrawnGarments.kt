package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.shape.strokeCurve
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.dress.NewGarmentNames
import dev.chieppa.squash.screens.dress.bodyChart
import dev.chieppa.squash.screens.dress.edgeCommand
import dev.chieppa.squash.screens.dress.fillCommand
import dev.chieppa.squash.screens.dress.newGarmentCommand
import dev.chieppa.squash.screens.dress.newGarmentIn
import dev.chieppa.squash.screens.dress.rootHoleCommand
import dev.chieppa.squash.screens.fennec.BACK_LEG_L
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_PAW_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.smoke.CloseUps
import dev.chieppa.squash.screens.woman.ARM_L
import dev.chieppa.squash.screens.woman.NECK
import dev.chieppa.squash.screens.woman.womanDocument
import dev.chieppa.squash.screens.woman.TORSO as WOMAN_TORSO

/** How many places a stroke across the front of a part takes, as a drag across it in a view lays one. */
private const val STROKE_POINTS = 9

/** The front half round a part a stroke across it runs over, from v -0.25 to 0.25. */
private const val HALF_ROUND = 0.5f

/** Where the boots' shafts end on the legs, the tee's hem on the torso and its sleeves on the arms, in u. */
private const val BOOT_SHAFT_U = 0.72f
private const val TEE_HEM_U = 0.12f
private const val TEE_SLEEVE_U = 0.3f

/** Where the chest is pressed to fill the tee. */
private const val CHEST_U = 0.6f

/**
 * Garments drawn on a body only with Dress's own edits, as Edge and Hole make them: a session over [start] sends New garment, then
 * each of [edits] read on the garment and body as they stand, and the document they leave is the subject.
 */
private fun drawn(start: SquashDocument, name: String, edits: List<Edit>): SquashDocument = TestWork().use { work ->
    val session = CharacterSession(start, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
    work.settle(session)
    val character = session.document.value.character
    val garment = newGarmentIn(character.wardrobe) { name }
    session.send(character.newGarmentCommand(garment, null, NewGarmentNames(name, name)))
    work.settle(session)
    for (edit in edits) {
        val now = session.document.value.character
        val body = checkNotNull(session.assembly.output.value)
        val held = checkNotNull(now.wardrobe.garment(garment.id))
        edit(session, now, body, held)?.let(session::send)
        work.settle(session)
    }
    session.document.value
}

private typealias Edit = (CharacterSession, dev.chieppa.squash.document.Character, dev.chieppa.squash.character.build.AssembledCharacter, Garment) -> DocumentCommand?

/** An Edge stroke across the front of [part] at [u], round it from [fromV] to [toV], closing round its far side. */
private fun across(part: PartId, u: Float, fromV: Float = -HALF_ROUND / 2f, toV: Float = HALF_ROUND / 2f): Edit = { _, character, body, garment ->
    val patch = checkNotNull(bodyChart(character, body).part(part)).patch
    val points = List(STROKE_POINTS) { i -> Anchor(part, patch, u, fromV + (toV - fromV) * i / (STROKE_POINTS - 1)) }
    character.edgeCommand(body, garment, strokeCurve(points, meets = false))
}

/** An Edge loop on [part] drawn round the box [fromU] to [toU] along it and [fromV] to [toV] round it, ending where it began. */
private fun loop(part: PartId, fromU: Float, toU: Float, fromV: Float, toV: Float): Edit = { _, character, body, garment ->
    val patch = checkNotNull(bodyChart(character, body).part(part)).patch
    val corners = listOf(Anchor(part, patch, fromU, fromV), Anchor(part, patch, toU, fromV), Anchor(part, patch, toU, toV), Anchor(part, patch, fromU, toV))
    character.edgeCommand(body, garment, strokeCurve(corners, meets = true))
}

/** An Edge press on [part] at [u] and [v]. */
private fun press(part: PartId, u: Float, v: Float = 0f): Edit = { _, character, body, garment ->
    val patch = checkNotNull(bodyChart(character, body).part(part)).patch
    character.fillCommand(body, garment, Anchor(part, patch, u, v))
}

/** A Hole press on [part]. */
private fun hole(part: PartId): Edit = { _, character, body, garment ->
    val patch = checkNotNull(bodyChart(character, body).part(part)).patch
    character.rootHoleCommand(body, garment, Anchor(part, patch, 0.5f, 0f))
}

private val BOOTS: List<Edit> = listOf(across(FRONT_LEG_L, BOOT_SHAFT_U), across(BACK_LEG_L, BOOT_SHAFT_U))

private val TEE: List<Edit> = listOf(across(WOMAN_TORSO, TEE_HEM_U), across(ARM_L, TEE_SLEEVE_U), press(WOMAN_TORSO, CHEST_U), hole(NECK), hole(NECK))

/** Boots on the fennec: an edge across each leg above the hock, both paws' sides filling, their toes tucked. */
fun fennecBootsDrawnDocument(): SquashDocument = drawn(fennecDocument(), "Boots", BOOTS)

/** The boots after their first edge alone, across the front legs. */
fun fennecBootsFirstDocument(): SquashDocument = drawn(fennecDocument(), "Boots", BOOTS.take(1))

/** A tee on the woman: a hem across the hips, a sleeve hem on the arm, a press on the chest and a hole round the neck. */
fun womanTeeDrawnDocument(): SquashDocument = drawn(womanDocument(), "Tee", TEE.take(4))

/** The tee's first [steps] edits: the hem alone, then the sleeve, then the press on the chest. */
fun womanTeeStepDocument(steps: Int): SquashDocument = drawn(womanDocument(), "Tee", TEE.take(steps))

/** The tee with a second Hole press on the neck: a cuff band round the neck hole. */
fun womanTeeBandDrawnDocument(): SquashDocument = drawn(womanDocument(), "Tee", TEE)

/** Where on the paw the sandal's sole loop lies, along it and round its underside, and its strap's two edges along it. */
private const val SOLE_FROM_U = 0.15f
private const val SOLE_TO_U = 0.95f
private const val SOLE_FROM_V = 0.3f
private const val SOLE_TO_V = 0.7f
private const val STRAP_FROM_U = 0.35f
private const val STRAP_TO_U = 0.5f

/** Where on the head the hat's loop runs round its crown, along it and round its top. */
private const val HAT_FROM_U = 0.1f
private const val HAT_TO_U = 0.45f
private const val HAT_HALF_V = 0.18f

/**
 * Sandals on the fennec as far as Edge reaches: a loop round each front paw's underside for the sole and two strokes across it for a
 * strap, a press on the sole and on the strap.
 */
fun fennecSandalsDrawnDocument(): SquashDocument = fennecSandalsStepDocument(SANDALS.size)

/** The sandals' first [steps] edits: the sole's loop, then the strap's edges, then the presses. */
fun fennecSandalsStepDocument(steps: Int): SquashDocument = drawn(fennecDocument(), "Sandals", SANDALS.take(steps))

private val SANDALS: List<Edit> = listOf(
    loop(FRONT_PAW_L, SOLE_FROM_U, SOLE_TO_U, SOLE_FROM_V, SOLE_TO_V),
    across(FRONT_PAW_L, STRAP_FROM_U),
    across(FRONT_PAW_L, STRAP_TO_U),
    press(FRONT_PAW_L, (SOLE_FROM_U + SOLE_TO_U) / 2f, 0.5f),
    press(FRONT_PAW_L, (STRAP_FROM_U + STRAP_TO_U) / 2f, 0f),
)

/** A top hat on the fennec as far as Edge and Hole reach: a loop round the crown of the head filling, the ears out through holes. */
fun fennecTopHatDrawnDocument(): SquashDocument =
    drawn(fennecDocument(), "Top hat", listOf(loop(HEAD, HAT_FROM_U, HAT_TO_U, -HAT_HALF_V, HAT_HALF_V), hole(EAR_L)))

/** The hat's crown loop alone, the ears left inside it. */
fun fennecTopHatEarsInDocument(): SquashDocument = drawn(fennecDocument(), "Top hat", listOf(loop(HEAD, HAT_FROM_U, HAT_TO_U, -HAT_HALF_V, HAT_HALF_V)))
