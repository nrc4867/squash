package dev.chieppa.squash.screens.dragon

import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.face.eyeLandmark
import dev.chieppa.squash.character.face.newEyesCommand
import dev.chieppa.squash.character.face.newMouthCommand
import dev.chieppa.squash.character.load.TemplateFolder
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
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.readDocument
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where the saved dragon lies among the test fixtures' resources. */
const val DRAGON_RESOURCE = "dragon.json"

val SCALES = SlotName("scales")
val BELLY = SlotName("belly")
val WING_SKIN = SlotName("wing-skin")

private const val LOOP_POINTS = 14

private val SCALED_PARTS = listOf(TORSO, HEAD, TAIL, EAR_L, FRONT_LEG_L, BACK_LEG_L, FRONT_PAW_L, BACK_PAW_L, WING_L)

/** The spikes down the back and the tail. */
private val SPIKED = (0 until 7).map { spikeOf(TORSO, it) } + (0 until 6).map { spikeOf(TAIL, it) }

private val shipped by lazy { TemplateFolder.shipped() }

/** The character before any command: a name on the canine plan. */
val EMPTY_DRAGON = SquashDocument(Character(CharacterId("dragon"), "Dragon", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))

/** A loop of anchors on [part] round ([u], [v]), [du] along it and [dv] round it. */
private fun spot(part: PartId, id: String, slot: SlotName, u: Float, v: Float, du: Float, dv: Float) = SetRegion(
    part, RegionId(id),
    Region(RegionId(id), List(LOOP_POINTS) { i -> (2.0 * PI * i / LOOP_POINTS).let { Anchor(part, BODY_PATCH, u + du * cos(it).toFloat(), v + dv * sin(it).toFloat()) } }, slot),
)

/** The dragon's colors: red scales, a pale gold belly along the body, tail and jaw and on its spikes and horns, and darker wine-red skin on its wings. */
fun dragonColor(): List<DocumentCommand> = listOf(
    SetPaletteSlot(SCALES, SlotColor(0.78f, 0.22f, 0.18f)),
    SetPaletteSlot(BELLY, SlotColor(0.96f, 0.82f, 0.52f)),
    SetPaletteSlot(WING_SKIN, SlotColor(0.52f, 0.13f, 0.18f)),
) + SCALED_PARTS.map { SetBaseSlot(it, SCALES) } + (SPIKED + HORN_L).map { SetBaseSlot(it, BELLY) } + listOf(
    SetAreaFill(WING_L, "web", WING_SKIN),
    SetAreaFill(WING_L, "skin", WING_SKIN),
    spot(TORSO, "belly", BELLY, 0.5f, 0.5f, 0.44f, 0.15f),
    spot(TAIL, "belly", BELLY, 0.3f, 0.5f, 0.28f, 0.12f),
    spot(HEAD, "chin", BELLY, 0.45f, 0.5f, 0.3f, 0.12f),
)

/** The face: two eyes as Add eyes places them about the head's eye landmark, and a mouth. */
fun dragonFace(before: Character): List<DocumentCommand> {
    val head = checkNotNull(shipped.template(TemplateId("head")))
    return listOfNotNull(newEyesCommand(before, HEAD, eyeLandmark(head))) + newMouthCommand(HEAD)
}

/** Every command that makes the dragon, in the order a person would send them. */
fun dragonCommands(): List<DocumentCommand> {
    val shaped = (dragonBody() + dragonColor()).fold(EMPTY_DRAGON) { document, command -> command.apply(document).document }
    return dragonBody() + dragonColor() + dragonFace(shaped.character)
}

/** The dragon as those commands leave it. */
fun dragonDocument(): SquashDocument = dragonCommands().fold(EMPTY_DRAGON) { document, command -> command.apply(document).document }

/** The dragon from the example file. */
fun savedDragon(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(DRAGON_RESOURCE)) { "No $DRAGON_RESOURCE" }.readText())
