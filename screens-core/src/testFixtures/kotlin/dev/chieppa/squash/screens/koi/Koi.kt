package dev.chieppa.squash.screens.koi

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

/** Where the saved koi lies among the test fixtures' resources. */
const val KOI_RESOURCE = "koi.json"

val SCALE = SlotName("scale")
val BELLY = SlotName("belly")
val PATCH = SlotName("patch")
val FIN = SlotName("fin")
val EYE = SlotName("eye")
val PUPIL = SlotName("pupil")

private const val LOOP_POINTS = 12

/** The character before any command: a name on the Blank plan. */
val EMPTY_KOI = SquashDocument(Character(CharacterId("koi"), "Koi", BodyPlanRef(BodyPlanId("blank"), 1)))

/** A loop of anchors on [part] round ([u], [v]), [du] along it and [dv] round it. */
private fun spot(part: PartId, id: String, slot: SlotName, u: Float, v: Float, du: Float, dv: Float) = SetRegion(
    part, RegionId(id),
    Region(RegionId(id), List(LOOP_POINTS) { i -> (2.0 * PI * i / LOOP_POINTS).let { Anchor(part, BODY_PATCH, u + du * cos(it).toFloat(), v + dv * sin(it).toFloat()) } }, slot),
)

/**
 * The koi's colors: an orange body with a white belly and a few black patches on its back and sides, each a region, paler fins, and
 * eyes with a dark pupil as a region on each.
 */
fun koiColor(): List<DocumentCommand> = listOf(
    SetPaletteSlot(SCALE, SlotColor(0.95f, 0.45f, 0.13f)),
    SetPaletteSlot(BELLY, SlotColor(0.98f, 0.96f, 0.92f)),
    SetPaletteSlot(PATCH, SlotColor(0.12f, 0.10f, 0.10f)),
    SetPaletteSlot(FIN, SlotColor(0.99f, 0.78f, 0.58f)),
    SetPaletteSlot(EYE, SlotColor(0.96f, 0.86f, 0.55f)),
    SetPaletteSlot(PUPIL, SlotColor(0.08f, 0.06f, 0.07f)),
    SetBaseSlot(KOI_BODY, SCALE),
    SetBaseSlot(DORSAL, FIN),
    SetBaseSlot(TAIL_FIN, FIN),
    SetBaseSlot(PECTORAL_L, FIN),
    SetBaseSlot(ANAL, FIN),
    SetBaseSlot(EYE_L, EYE),
    spot(KOI_BODY, "belly", BELLY, 0.5f, 0.5f, 0.38f, 0.17f),
    spot(KOI_BODY, "saddle", PATCH, 0.52f, 0.12f, 0.08f, 0.06f),
    spot(KOI_BODY, "shoulder", PATCH, 0.7f, 0.86f, 0.05f, 0.05f),
    spot(KOI_BODY, "flank", PATCH, 0.33f, 0.2f, 0.06f, 0.05f),
    spot(KOI_BODY, "stalk", PATCH, 0.17f, 0.82f, 0.05f, 0.06f),
    spot(EYE_L, "pupil", PUPIL, 0.55f, 0.25f, 0.25f, 0.11f),
)

/** Every command that makes the koi, in the order a person would send them. */
fun koiCommands(): List<DocumentCommand> = koiBody() + koiColor()

/** The koi as those commands leave it. */
fun koiDocument(): SquashDocument = koiCommands().fold(EMPTY_KOI) { document, command -> command.apply(document).document }

/** The koi from the example file. */
fun savedKoi(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(KOI_RESOURCE)) { "No $KOI_RESOURCE" }.readText())
