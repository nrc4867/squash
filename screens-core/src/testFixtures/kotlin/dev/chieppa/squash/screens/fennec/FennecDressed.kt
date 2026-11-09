package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.readDocument

/** Where the saved dressed fennec lies among the test fixtures' resources. */
const val FENNEC_DRESSED_RESOURCE = "fennec-dressed.json"

val SWEATER = GarmentId("sweater")
val HOOD = GarmentId("hood")
val CAP = GarmentId("cap")
val BOOTS = GarmentId("boots")
val SCARF = GarmentId("scarf")
val NECK_SLOT = GarmentSlotId("neck")
val SCARF_WOOL = SlotName("scarf")
val FEET_SLOT = GarmentSlotId("feet")
val BOOT_LEATHER = SlotName("boot")
val HEAD_SLOT = GarmentSlotId("head")
val BODY_SLOT = GarmentSlotId("body")
val WINTER = OutfitId("winter")
val BARE = OutfitId("bare")
val HOODED = OutfitId("hooded")
val SWEATER_WOOL = SlotName("sweater")
val HOOD_WOOL = SlotName("hood")

/** Where the sweater starts along the torso, behind the ribs, to the base of the neck. */
private const val SWEATER_FROM = 0.4f
private const val SWEATER_TO = 0.97f

/** How far down the front legs the sleeves reach. */
private const val SLEEVE_TO = 0.6f

/** The hood runs from the back of the skull to past the ears' roots, open round the face. */
private const val HOOD_TO = 0.42f

/** The cap covers the crown from the back of the skull to the brow, over the top of the head, the ears coming through it. */
private const val CAP_TO = 0.45f
private const val CAP_ROUND = 0.22f

/** The paw parameter that sets how many toes it builds. */
private const val TOES = "toes"

/** The boots' shafts run up the legs from this far along them. */
private const val SHAFT_FROM = 0.72f

/** The scarf wraps the base of the neck, over the sweater, one layer out. */
private const val SCARF_FROM = 0.84f
private const val SCARF_LAYER = 2

/** The paws the boots drop the toes of, both sides by their pairs. */
private val BOOTED = listOf(FRONT_PAW_L, BACK_PAW_L)

/** The sweater's Side, a little looser than the chest, flaring at the hem behind the ribs. */
private val SWEATER_SIDE = loop(
    p(0.38f, 0.26f), p(0.56f, 0.27f), p(0.70f, 0.36f), p(0.84f, 0.48f), p(0.92f, 0.38f),
    p(0.91f, 0.16f), p(0.82f, -0.10f), p(0.64f, -0.20f), p(0.38f, -0.20f),
)

/** The commands that dress the fennec: a head and a body slot, a wool cap with the ears through it, a hood, a sweater on torso and front legs, boots that drop the paws' toes, a scarf over the sweater, and Winter (cap, scarf, sweater, boots), Hooded and Bare outfits. */
fun fennecDressing(): List<DocumentCommand> = listOf(
    SetPaletteSlot(SWEATER_WOOL, SlotColor(0.20f, 0.42f, 0.52f)),
    SetPaletteSlot(HOOD_WOOL, SlotColor(0.78f, 0.24f, 0.22f)),
    AddGarmentSlot(GarmentSlot(HEAD_SLOT, "Head")),
    AddGarmentSlot(GarmentSlot(BODY_SLOT, "Body")),
    AddGarment(Garment(SWEATER, "Sweater", BODY_SLOT)),
    CoverPart(SWEATER, GarmentBranch(TORSO, SWEATER_FROM, SWEATER_TO, outlines = mapOf(View.SIDE to SWEATER_SIDE))),
    CoverPart(SWEATER, GarmentBranch(FRONT_LEG_L, 0f, SLEEVE_TO)),
    SetGarmentColor(SWEATER, SWEATER_WOOL),
    AddGarment(Garment(HOOD, "Hood", HEAD_SLOT)),
    CoverPart(HOOD, GarmentBranch(HEAD, 0f, HOOD_TO)),
    SetGarmentColor(HOOD, HOOD_WOOL),
    AddGarment(Garment(CAP, "Cap", HEAD_SLOT)),
    CoverPart(CAP, GarmentBranch(HEAD, 0f, CAP_TO, fromV = -CAP_ROUND, toV = CAP_ROUND)),
    SetGarmentColor(CAP, HOOD_WOOL),
    SetPaletteSlot(BOOT_LEATHER, SlotColor(0.36f, 0.24f, 0.18f)),
    AddGarmentSlot(GarmentSlot(FEET_SLOT, "Feet")),
    AddGarment(Garment(BOOTS, "Boots", FEET_SLOT)),
    SetGarmentColor(BOOTS, BOOT_LEATHER),
) + BOOTED.flatMap { paw ->
    listOf(
        CoverPart(BOOTS, GarmentBranch(paw)),
        SetBodyPartChange(BOOTS, paw, BodyPartChange.Redraw(PartData(params = mapOf(TOES to 0f)))),
    )
} + listOf(FRONT_LEG_L, BACK_LEG_L).map { CoverPart(BOOTS, GarmentBranch(it, SHAFT_FROM, 1f)) } + listOf(
    SetPaletteSlot(SCARF_WOOL, SlotColor(0.92f, 0.82f, 0.36f)),
    AddGarmentSlot(GarmentSlot(NECK_SLOT, "Neck")),
    AddGarment(Garment(SCARF, "Scarf", NECK_SLOT, layer = SCARF_LAYER)),
    CoverPart(SCARF, GarmentBranch(TORSO, SCARF_FROM, SWEATER_TO)),
    SetGarmentColor(SCARF, SCARF_WOOL),
    AddOutfit(Outfit(WINTER, "Winter")),
    PickGarment(WINTER, HEAD_SLOT, CAP),
    PickGarment(WINTER, BODY_SLOT, SWEATER),
    PickGarment(WINTER, FEET_SLOT, BOOTS),
    PickGarment(WINTER, NECK_SLOT, SCARF),
    AddOutfit(Outfit(HOODED, "Hooded")),
    PickGarment(HOODED, HEAD_SLOT, HOOD),
    PickGarment(HOODED, BODY_SLOT, SWEATER),
    AddOutfit(Outfit(BARE, "Bare")),
    ShowOutfit(WINTER),
)

/** Every command that makes the dressed fennec: the fennec, then its clothes. */
fun fennecDressedCommands(): List<DocumentCommand> = fennecCommands() + fennecDressing()

/** The dressed fennec as those commands leave it. */
fun fennecDressedDocument(): SquashDocument = fennecDressedCommands().fold(EMPTY_FENNEC) { document, command -> command.apply(document).document }

/** The dressed fennec from the example file. */
fun savedFennecDressed(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(FENNEC_DRESSED_RESOURCE)) { "No $FENNEC_DRESSED_RESOURCE" }.readText())
