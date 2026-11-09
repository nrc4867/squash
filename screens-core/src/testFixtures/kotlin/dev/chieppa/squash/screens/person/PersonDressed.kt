package dev.chieppa.squash.screens.person

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.readDocument

/** Where the saved dressed person lies among the test fixtures' resources. */
const val PERSON_DRESSED_RESOURCE = "person-dressed.json"

val SHIRT_GARMENT = GarmentId("shirt")
val JACKET = GarmentId("jacket")
val SANDALS = GarmentId("sandals")
val BODY_SLOT = GarmentSlotId("body")
val OUTER_SLOT = GarmentSlotId("outer")
val FEET_SLOT = GarmentSlotId("feet")
val SUMMER = OutfitId("summer")
val BARE = OutfitId("bare")
val JACKET_CLOTH = SlotName("jacket")
val SANDAL_LEATHER = SlotName("sandal")

/** The shirt runs from the hips to the shoulders, its sleeves to just short of the wrists. */
private const val SHIRT_FROM = 0.15f
private const val SHIRT_TO = 1f
private const val SHIRT_SLEEVE_TO = 0.9f

/** The jacket stops a little above the shirt's hem, its sleeves a little short of the shirt's, one layer out. */
private const val JACKET_FROM = 0.22f
private const val JACKET_SLEEVE_TO = 0.82f
private const val JACKET_LAYER = 2

/** The sandal's sole, under the foot from heel to toe: round the bottom of the foot, where its v is a half. */
private const val SOLE_FROM_V = 0.3f
private const val SOLE_TO_V = 0.7f

/** The foot the sandals show: its five toes, in the skin, where the bare foot is a shoe with none. */
private const val SANDAL_TOES = 5f

/**
 * The commands that dress the person: a body, an outer and a feet slot, a shirt on torso and arms, a jacket over it, sandals that
 * show the foot redrawn with its toes in the skin, and Summer (shirt, jacket, sandals) and Bare outfits, Summer shown.
 */
fun personDressing(): List<DocumentCommand> = listOf(
    SetPaletteSlot(JACKET_CLOTH, SlotColor(0.30f, 0.34f, 0.44f)),
    SetPaletteSlot(SANDAL_LEATHER, SlotColor(0.52f, 0.34f, 0.20f)),
    AddGarmentSlot(GarmentSlot(BODY_SLOT, "Body")),
    AddGarmentSlot(GarmentSlot(OUTER_SLOT, "Outer")),
    AddGarmentSlot(GarmentSlot(FEET_SLOT, "Feet")),
    AddGarment(Garment(SHIRT_GARMENT, "Shirt", BODY_SLOT)),
    CoverPart(SHIRT_GARMENT, GarmentBranch(TORSO, SHIRT_FROM, SHIRT_TO)),
    CoverPart(SHIRT_GARMENT, GarmentBranch(ARM_L, 0f, SHIRT_SLEEVE_TO)),
    SetGarmentColor(SHIRT_GARMENT, SHIRT),
    AddGarment(Garment(JACKET, "Jacket", OUTER_SLOT, layer = JACKET_LAYER)),
    CoverPart(JACKET, GarmentBranch(TORSO, JACKET_FROM, SHIRT_TO)),
    CoverPart(JACKET, GarmentBranch(ARM_L, 0f, JACKET_SLEEVE_TO)),
    SetGarmentColor(JACKET, JACKET_CLOTH),
    AddGarment(Garment(SANDALS, "Sandals", FEET_SLOT)),
    CoverPart(SANDALS, GarmentBranch(FOOT_L, fromV = SOLE_FROM_V, toV = SOLE_TO_V)),
    SetGarmentColor(SANDALS, SANDAL_LEATHER),
    SetBodyPartChange(SANDALS, FOOT_L, BodyPartChange.Redraw(PartData(params = mapOf("toes" to SANDAL_TOES), surface = SurfaceWork(baseSlot = SKIN)))),
    AddOutfit(Outfit(SUMMER, "Summer")),
    PickGarment(SUMMER, BODY_SLOT, SHIRT_GARMENT),
    PickGarment(SUMMER, OUTER_SLOT, JACKET),
    PickGarment(SUMMER, FEET_SLOT, SANDALS),
    AddOutfit(Outfit(BARE, "Bare")),
    ShowOutfit(SUMMER),
)

/** Every command that makes the dressed person: the person, then its clothes. */
fun personDressedCommands(): List<DocumentCommand> = personCommands() + personDressing()

/** The dressed person as those commands leave it. */
fun personDressedDocument(): SquashDocument = personDressedCommands().fold(EMPTY_PERSON) { document, command -> command.apply(document).document }

/** The dressed person from the example file. */
fun savedPersonDressed(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(PERSON_DRESSED_RESOURCE)) { "No $PERSON_DRESSED_RESOURCE" }.readText())
