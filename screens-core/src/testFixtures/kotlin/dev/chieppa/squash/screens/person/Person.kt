package dev.chieppa.squash.screens.person

import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.face.eyeLandmark
import dev.chieppa.squash.character.face.newEyesCommand
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.mouthGroup
import dev.chieppa.squash.document.readDocument

/** Where the saved person lies among the test fixtures' resources. */
const val PERSON_RESOURCE = "person.json"

val SKIN = SlotName("skin")
val SHIRT = SlotName("shirt")
val TROUSERS = SlotName("trousers")
val HAIR_COLOR = SlotName("hair")
val SHOE = SlotName("shoe")

/** The character before any command: a name on the biped plan, with the plan's palette, as New character starts it. */
val EMPTY_PERSON: SquashDocument by lazy {
    val plan = checkNotNull(shipped.plans.firstOrNull { it.id == BodyPlanId("biped") })
    SquashDocument(Character(CharacterId("person"), "Person", plan.ref, palette = Palette(plan.palette.map { PaletteSlot(SlotName(it.name), it.color) })))
}

/** Where the shirt runs along the torso, from just above the hips to the top, all the way round. */
private const val SHIRT_FROM = 0.2f
private const val SHIRT_TO = 1f
private const val BAND_STEPS = 8

/** The smile: round the front of the head's lower face, from one cheek to the other. */
private const val SMILE_U = 0.92f
private val SMILE_V = listOf(0.36f, 0.42f, 0.5f, 0.58f, 0.64f)

private val shipped by lazy { TemplateFolder.shipped() }

/** A band of anchors round [part] from [from] to [to] along it, closing on itself across the seam. */
private fun band(part: PartId, from: Float, to: Float): List<Anchor> =
    List(BAND_STEPS + 1) { Anchor(part, BODY_PATCH, from, it.toFloat() / BAND_STEPS) } + List(BAND_STEPS + 1) { Anchor(part, BODY_PATCH, to, 1f - it.toFloat() / BAND_STEPS) }

/**
 * The person's colors from the biped plan's palette: skin on the head and hands, trousers on the torso under a shirt region round it
 * from the hips up, shirt sleeves on the arms, trousers on the legs, shoes on the feet and hair on the hair.
 */
fun personColor(): List<DocumentCommand> = listOf(
    SetBaseSlot(TORSO, TROUSERS),
    SetRegion(TORSO, RegionId("shirt"), Region(RegionId("shirt"), band(TORSO, SHIRT_FROM, SHIRT_TO), SHIRT)),
    SetBaseSlot(HEAD, SKIN),
    SetBaseSlot(ARM_L, SHIRT),
    SetBaseSlot(HAND_L, SKIN),
    SetBaseSlot(LEG_L, TROUSERS),
    SetBaseSlot(FOOT_L, SHOE),
    SetBaseSlot(HAIR, HAIR_COLOR),
)

/** A smiling lip line and the mouth group that holds it. */
private fun mouth(): List<DocumentCommand> {
    val smile = Mark(MarkId("mouth-smile"), SMILE_V.map { Anchor(HEAD, BODY_PATCH, SMILE_U, it) })
    val empty = mouthGroup(MarkGroupId("mouth"), "mouth")
    val group = empty.copy(roles = empty.roles.map { if (it.role == MarkRole.LIP_LINE) it.copy(marks = listOf(smile.id)) else it })
    return listOf(SetMark(HEAD, smile.id, smile), SetMarkGroup(HEAD, group.id, group))
}

/** The face: two eyes as the Face stage's Add eyes places them about the round head's eye landmark, and a smile. */
fun personFace(before: Character): List<DocumentCommand> {
    val head = checkNotNull(shipped.template(TemplateId("head-round")))
    return listOfNotNull(newEyesCommand(before, HEAD, eyeLandmark(head))) + mouth()
}

/** Every command that makes the person, in the order a person would send them. */
fun personCommands(): List<DocumentCommand> {
    val shaped = (personBody() + personColor()).fold(EMPTY_PERSON) { document, command -> command.apply(document).document }
    return personBody() + personColor() + personFace(shaped.character)
}

/** The person as those commands leave it. */
fun personDocument(): SquashDocument = personCommands().fold(EMPTY_PERSON) { document, command -> command.apply(document).document }

/** The person from the example file. */
fun savedPerson(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(PERSON_RESOURCE)) { "No $PERSON_RESOURCE" }.readText())
