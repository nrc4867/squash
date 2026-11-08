package dev.chieppa.squash.screens.toucan

import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.face.eyeLandmark
import dev.chieppa.squash.character.face.newEyesCommand
import dev.chieppa.squash.character.face.newMouthCommand
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetMark
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

/** Where the saved toucan lies among the test fixtures' resources. */
const val TOUCAN_RESOURCE = "toucan.json"

val BODY = SlotName("body")
val BELLY = SlotName("belly")
val BEAK_COLOR = SlotName("beak")
val BEAK_TIP = SlotName("beak-tip")
val EYE_RING = SlotName("eye-ring")
val LEG_COLOR = SlotName("leg")
val FEATHER = SlotName("feather")

private val shipped by lazy { TemplateFolder.shipped() }

/** The character before any command: a name on the bird plan, with the plan's palette, as New character starts it. */
val EMPTY_TOUCAN: SquashDocument by lazy {
    val plan = checkNotNull(shipped.plans.firstOrNull { it.id == BodyPlanId("bird") })
    SquashDocument(Character(CharacterId("toucan"), "Toucan", plan.ref, palette = Palette(plan.palette.map { PaletteSlot(SlotName(it.name), it.color) })))
}

/** The pale ring round each eye: centred on the head's eye landmark, wider along the head than round it. */
private const val RING_HALF_U = 0.16f
private const val RING_HALF_V = 0.085f
private const val RING_POINTS = 16

/** Where each feather line crosses the wing, along it and from the leading edge toward the trailing one, on both faces. */
private val FEATHERS = listOf(0.3f, 0.45f, 0.6f, 0.74f)
private const val FEATHER_SLANT = 0.08f
private val FEATHER_ACROSS = listOf(0.08f, 0.18f, 0.3f, 0.42f)

/** The palette a toucan recolors the plan's to: a black body, a white-yellow bib, a yellow-orange beak with a dark tip, a pale blue-green eye ring, blue-grey legs and grey feather lines. */
fun toucanPalette(): List<DocumentCommand> = listOf(
    SetPaletteSlot(BODY, SlotColor(0.09f, 0.09f, 0.11f)),
    SetPaletteSlot(BELLY, SlotColor(0.99f, 0.94f, 0.66f)),
    SetPaletteSlot(BEAK_COLOR, SlotColor(0.98f, 0.62f, 0.13f)),
    SetPaletteSlot(EYE_RING, SlotColor(0.74f, 0.90f, 0.84f)),
    SetPaletteSlot(LEG_COLOR, SlotColor(0.45f, 0.55f, 0.70f)),
    SetPaletteSlot(BEAK_TIP, SlotColor(0.16f, 0.10f, 0.09f)),
    SetPaletteSlot(FEATHER, SlotColor(0.52f, 0.52f, 0.58f)),
)

/** A closed ring of anchors on [part] about [u] and [v]. */
private fun ring(part: PartId, u: Float, v: Float): List<Anchor> = List(RING_POINTS + 1) { i ->
    val angle = 2.0 * PI * (i % RING_POINTS) / RING_POINTS
    Anchor(part, BODY_PATCH, u + RING_HALF_U * cos(angle).toFloat(), v + RING_HALF_V * sin(angle).toFloat())
}

private fun feather(id: String, u: Float, face: (Float) -> Float) = SetMark(
    WING_L, MarkId(id),
    Mark(MarkId(id), FEATHER_ACROSS.mapIndexed { i, v -> Anchor(WING_L, BODY_PATCH, u - FEATHER_SLANT * i / FEATHER_ACROSS.lastIndex, face(v)) }, slot = FEATHER),
)

/**
 * The toucan's colors: black on the body, head, wings and tail, the bib and throat in the belly slot, the beak's two halves
 * yellow-orange with their tips dark, a pale ring round each side eye as a region, blue-grey legs and feet, and feather lines on the wings.
 */
fun toucanColor(): List<DocumentCommand> {
    val head = checkNotNull(shipped.template(TemplateId("head-bird")))
    val eye = checkNotNull(eyeLandmark(head))
    return toucanPalette() + listOf(
        SetBaseSlot(TORSO, BODY),
        SetAreaFill(TORSO, "bib", BELLY),
        SetBaseSlot(HEAD, BODY),
        SetAreaFill(HEAD, "throat", BELLY),
        SetRegion(HEAD, RegionId("eye-ring-l"), Region(RegionId("eye-ring-l"), ring(HEAD, eye.u, eye.v), EYE_RING)),
        SetRegion(HEAD, RegionId("eye-ring-r"), Region(RegionId("eye-ring-r"), ring(HEAD, eye.u, 1f - eye.v), EYE_RING)),
        SetBaseSlot(BEAK, BEAK_COLOR),
        SetAreaFill(BEAK, "tip", BEAK_TIP),
        SetBaseSlot(BEAK_LOWER, BEAK_COLOR),
        SetAreaFill(BEAK_LOWER, "tip", BEAK_TIP),
        SetBaseSlot(WING_L, BODY),
        SetBaseSlot(LEG_L, LEG_COLOR),
        SetBaseSlot(FOOT_L, LEG_COLOR),
        SetBaseSlot(TAIL, BODY),
    ) + FEATHERS.mapIndexed { i, u -> feather("feather-$i", u) { it } } + FEATHERS.mapIndexed { i, u -> feather("feather-under-$i", u) { 1f - it } }
}

/** The face: two eyes as the Face stage's Add eyes places them about the bird head's side eye landmark, and a mouth whose open swings the lower beak. */
fun toucanFace(before: Character): List<DocumentCommand> {
    val head = checkNotNull(shipped.template(TemplateId("head-bird")))
    return listOfNotNull(newEyesCommand(before, HEAD, eyeLandmark(head))) + newMouthCommand(HEAD)
}

/** Every command that makes the toucan, in the order a person would send them. */
fun toucanCommands(): List<DocumentCommand> {
    val shaped = (toucanBody() + toucanColor()).fold(EMPTY_TOUCAN) { document, command -> command.apply(document).document }
    return toucanBody() + toucanColor() + toucanFace(shaped.character)
}

/** The toucan as those commands leave it. */
fun toucanDocument(): SquashDocument = toucanCommands().fold(EMPTY_TOUCAN) { document, command -> command.apply(document).document }

/** The toucan from the example file. */
fun savedToucan(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(TOUCAN_RESOURCE)) { "No $TOUCAN_RESOURCE" }.readText())
