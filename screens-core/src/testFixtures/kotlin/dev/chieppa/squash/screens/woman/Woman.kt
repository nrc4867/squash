package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument

/** Where the saved woman lies among the test fixtures' resources. */
const val WOMAN_RESOURCE = "woman.json"

private val shipped by lazy { TemplateFolder.shipped() }

/** The character before any command: a name on the Blank plan, with the plan's palette, as New character starts it. */
val EMPTY_WOMAN: SquashDocument by lazy {
    val plan = checkNotNull(shipped.plans.firstOrNull { it.id == BodyPlanId("blank") })
    SquashDocument(Character(CharacterId("woman"), "Woman", plan.ref, palette = Palette(plan.palette.map { PaletteSlot(SlotName(it.name), it.color) })))
}

/**
 * Every command that makes the woman undressed, in the order a person would send them: shape, hair, color, the face, the switch that
 * builds her as one surface with its joins and her painted eyes, then the pose she is shown in.
 */
fun womanCommands(): List<DocumentCommand> =
    womanBody() + womanHead() + womanColor() + womanFace() + womanOneSurface() + SetDisplayPose(WOMAN_DISPLAY)

/** The woman as those commands leave it. */
fun womanDocument(): SquashDocument = womanCommands().fold(EMPTY_WOMAN) { document, command -> command.apply(document).document }

/** The woman from the example file. */
fun savedWoman(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(WOMAN_RESOURCE)) { "No $WOMAN_RESOURCE" }.readText())
