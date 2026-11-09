package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
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
val HEAD_SLOT = GarmentSlotId("head")
val BODY_SLOT = GarmentSlotId("body")
val WINTER = OutfitId("winter")
val BARE = OutfitId("bare")
val SWEATER_WOOL = SlotName("sweater")
val HOOD_WOOL = SlotName("hood")

/** Where the sweater starts along the torso, behind the ribs, to the base of the neck. */
private const val SWEATER_FROM = 0.4f
private const val SWEATER_TO = 0.97f

/** How far down the front legs the sleeves reach. */
private const val SLEEVE_TO = 0.6f

/** The hood runs from the back of the skull to past the ears' roots, open round the face. */
private const val HOOD_TO = 0.42f

/** The sweater's Side, a little looser than the chest, flaring at the hem behind the ribs. */
private val SWEATER_SIDE = loop(
    p(0.38f, 0.26f), p(0.56f, 0.27f), p(0.70f, 0.36f), p(0.84f, 0.48f), p(0.92f, 0.38f),
    p(0.91f, 0.16f), p(0.82f, -0.10f), p(0.64f, -0.20f), p(0.38f, -0.20f),
)

/** The commands that dress the fennec: a head and a body slot, a wool hood and a sweater on torso and front legs, and Winter and Bare outfits. */
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
    AddOutfit(Outfit(WINTER, "Winter")),
    PickGarment(WINTER, HEAD_SLOT, HOOD),
    PickGarment(WINTER, BODY_SLOT, SWEATER),
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
