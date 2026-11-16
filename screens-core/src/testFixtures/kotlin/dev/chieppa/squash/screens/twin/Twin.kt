package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument

/** Where the saved twin lies among the test fixtures' resources. */
const val TWIN_RESOURCE = "woman-twin.json"

private val shipped by lazy { TemplateFolder.shipped() }

/** The twin before any command: a name on the Blank plan, with the plan's palette, as New character starts it. */
val EMPTY_TWIN: SquashDocument by lazy {
    val plan = checkNotNull(shipped.plans.firstOrNull { it.id == BodyPlanId("blank") })
    SquashDocument(Character(CharacterId("woman-twin"), "Woman twin", plan.ref, palette = Palette(plan.palette.map { PaletteSlot(SlotName(it.name), it.color) })))
}

/**
 * Every command that makes the twin undressed: a woman built in metres from a numeric description of a reference character, standing on
 * the ground with +Y up and facing forward, so cameras placed in the reference's frame see her as the reference renders do. Shape, head,
 * hair, colour, face, the switch that builds her as one surface with its joins and her painted eyes, then the pose she is shown in.
 */
fun twinCommands(): List<DocumentCommand> =
    twinBody() + twinHead() + twinHair() + twinColor() + twinFace() + twinOneSurface() + SetDisplayPose(TWIN_DISPLAY)

/** The twin as those commands leave it. */
fun twinDocument(): SquashDocument = twinCommands().fold(EMPTY_TWIN) { document, command -> command.apply(document).document }

/** The twin from the example file. */
fun savedTwin(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(TWIN_RESOURCE)) { "No $TWIN_RESOURCE" }.readText())
