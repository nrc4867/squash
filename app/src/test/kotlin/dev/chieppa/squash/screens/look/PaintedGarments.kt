package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.dress.NewGarmentNames
import dev.chieppa.squash.screens.dress.PaintStyle
import dev.chieppa.squash.screens.dress.bandStroke
import dev.chieppa.squash.screens.dress.bodyChart
import dev.chieppa.squash.screens.dress.brushPlaces
import dev.chieppa.squash.screens.dress.brushStroke
import dev.chieppa.squash.screens.dress.roundStroke
import dev.chieppa.squash.screens.dress.holeCommand
import dev.chieppa.squash.screens.dress.newAreaCommand
import dev.chieppa.squash.screens.dress.newGarmentCommand
import dev.chieppa.squash.screens.dress.newGarmentIn
import dev.chieppa.squash.screens.dress.paintCommand
import dev.chieppa.squash.screens.dress.paintedOf
import dev.chieppa.squash.screens.dress.shownTucks
import dev.chieppa.squash.screens.dress.straightenCommand
import dev.chieppa.squash.screens.dress.tuckCommand
import dev.chieppa.squash.screens.fennec.BACK_LEG_L
import dev.chieppa.squash.screens.fennec.BACK_PAW_L
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

/** How big a brush unpaints the neck with, in body units: small enough to leave the shoulders. */
private const val NECK_BRUSH = 0.03f

/** Where the hood's front edge rings the head, from the back of the skull toward the snout, in u. */
private const val HOOD_FRONT_U = 0.6f

/** Where the tee's hem lies on the torso, where the torso's paint stops below the neck and how far the sleeves run, in u. */
private const val TEE_HEM_U = 0.2f
private const val TEE_TOP_U = 0.97f
private const val TEE_SLEEVE_U = 0.3f

/** Where the boots' shafts start on the back legs. */
private const val SHAFT_U = 0.55f

/** The sandals' ankle strap on the front leg and toe strap on the paw, in u. */
private const val ANKLE_FROM_U = 0.8f
private const val TOE_TO_U = 0.35f

/**
 * Garments painted on a body only with Dress's own edits, as Paint, Hole and the inspector send them: a session over [start] sends New
 * garment, then each of [edits] read on the garment and body as they stand, and the document they leave is the subject.
 */
private fun painted(start: SquashDocument, name: String, edits: List<Edit>): SquashDocument = TestWork().use { work ->
    val session = CharacterSession(start, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
    work.settle(session)
    val character = session.document.value.character
    val garment = newGarmentIn(character.wardrobe) { name }
    session.send(character.newGarmentCommand(garment, null, NewGarmentNames(name, name)))
    work.settle(session)
    var area: dev.chieppa.squash.document.GarmentAreaId? = null
    for (edit in edits) {
        val now = session.document.value.character
        val body = checkNotNull(session.assembly.output.value)
        val held = checkNotNull(now.wardrobe.garment(garment.id))
        when (edit) {
            is Edit.Send -> edit.command(now, body, held)?.let(session::send)
            is Edit.NewArea -> now.newAreaCommand(body, held).let { (command, id) ->
                session.send(command)
                area = id
            }
            is Edit.Paint -> session.send(now.paintCommand(body, held, area, edit.stroke(now, body), PaintStyle(erase = edit.erase, clean = edit.clean)))
        }
        work.settle(session)
    }
    session.document.value
}

/** One edit a subject makes: a stroke laid in the area last made, a new area, or another command. */
private sealed interface Edit {
    class Paint(val clean: Float = DEFAULT_CLEAN, val erase: Boolean = false, val stroke: (Character, AssembledCharacter) -> dev.chieppa.squash.character.shape.SurfaceMask) : Edit
    object NewArea : Edit
    class Send(val command: (Character, AssembledCharacter, Garment) -> DocumentCommand?) : Edit
}

private const val DEFAULT_CLEAN = 0.6f

/** A brush stroke down [part] from [fromU] to [toU] all round it (see [roundStroke]). */
private fun brush(part: PartId, fromU: Float, toU: Float): Edit = Edit.Paint { character, body -> roundStroke(character, body, part, fromU, toU) }

/** A small brush stroked with Alt down [part] all round it, unpainting what another stroke caught of it. */
private fun unpaint(part: PartId): Edit = Edit.Paint(erase = true) { character, body ->
    brushStroke(character, body, brushPlaces(character, body, part, 0f, 1f), NECK_BRUSH)
}

/** A stroke laid exactly over [part] from [fromU] to [toU], as a box drawn round it in a pane lays. */
private fun band(part: PartId, fromU: Float, toU: Float): Edit = Edit.Paint(clean = 0f) { character, body -> bandStroke(character, body, part, fromU, toU) }

/** A Hole press on [part]. */
private fun hole(part: PartId): Edit = Edit.Send { character, body, garment ->
    val patch = checkNotNull(bodyChart(character, body).part(part)).patch
    character.holeCommand(body, garment, Anchor(part, patch, 0.5f, 0f), cuffOnSecondPress = true)
}

/** Straighten on the first edge of [garment] on [part]. */
private fun straighten(part: PartId): Edit = Edit.Send { character, body, garment ->
    val index = paintedOf(character, body, garment).edges.indexOfFirst { it.edge.part == part }
    if (index < 0) null else character.straightenCommand(body, garment, index)
}

/** The Tuck row for [part] set [tucked]. */
private fun tuck(part: PartId, tucked: Boolean): Edit = Edit.Send { character, body, garment ->
    shownTucks(character, body, garment, null).firstOrNull { it.part == part }?.let { tuckCommand(character, body, garment, it, tucked) }
}

private val TEE: List<Edit> = listOf(brush(WOMAN_TORSO, TEE_HEM_U, TEE_TOP_U), unpaint(NECK), brush(ARM_L, 0.02f, TEE_SLEEVE_U), hole(NECK), hole(NECK))

/** A tee on the woman: the torso brushed, what it caught of the neck unpainted, then the upper arms, the neck left open. */
fun womanTeePaintedDocument(): SquashDocument = painted(womanDocument(), "Tee", TEE.take(3))

/** The tee's first [steps] edits: the torso, the neck unpainted, the arms, then a Hole press on the neck, then a second for its cuff. */
fun womanTeeStepDocument(steps: Int): SquashDocument = painted(womanDocument(), "Tee", TEE.take(steps))

private val BOOTS: List<Edit> = listOf(brush(BACK_LEG_L, SHAFT_U, 1f), brush(BACK_PAW_L, 0f, 1f), straighten(BACK_LEG_L))

/** Boots on the fennec: one area over the back legs and paws, mirrored to the pair, the shaft's edge straightened, the toes tucked. */
fun fennecBootsPaintedDocument(): SquashDocument = painted(fennecDocument(), "Boots", BOOTS)

/** The boots' first [steps] edits: the legs, then the paws, then Straighten on the shaft's edge. */
fun fennecBootsStepDocument(steps: Int): SquashDocument = painted(fennecDocument(), "Boots", BOOTS.take(steps))

private val SANDALS: List<Edit> = listOf(band(FRONT_LEG_L, ANKLE_FROM_U, 1f), Edit.NewArea, band(FRONT_PAW_L, 0f, TOE_TO_U))

/** Sandals on the fennec: an ankle strap and a toe strap painted as two areas meeting at the paw's root along an inked seam. */
fun fennecSandalsPaintedDocument(): SquashDocument = painted(fennecDocument(), "Sandals", SANDALS)

/** The sandals' ankle strap alone. */
fun fennecSandalsFirstDocument(): SquashDocument = painted(fennecDocument(), "Sandals", SANDALS.take(1))

private val HOOD: List<Edit> = listOf(band(HEAD, 0f, HOOD_FRONT_U), band(EAR_L, 0f, 1f))

/** A hood on the fennec: the head and ears painted, the cloth shaped over the ears. */
fun fennecHoodPaintedDocument(): SquashDocument = painted(fennecDocument(), "Hood", HOOD)

/** The hood with the ears tucked, the cloth smooth over them. */
fun fennecHoodTuckedDocument(): SquashDocument = painted(fennecDocument(), "Hood", HOOD + tuck(EAR_L, tucked = true))
