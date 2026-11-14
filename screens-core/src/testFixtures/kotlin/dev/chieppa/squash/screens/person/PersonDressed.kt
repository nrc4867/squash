package dev.chieppa.squash.screens.person

import dev.chieppa.squash.character.build.HandSockets
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
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetJoinInk
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.twin.slab
import dev.chieppa.squash.screens.woman.ownedPair
import dev.chieppa.squash.screens.woman.propShape

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

/** The sandal's sole, a slab it owns under each foot on a socket under the ankle, where the foot's v is a half: how deep and how many rings and columns it is lofted with. */
val SANDAL_SOLE_L = PartId("sandal-sole-l")
val SANDAL_SOLE_R = PartId("sandal-sole-r")
private const val SOLE_SOCKET = 2
private const val SOLE_U = 0.15f
private const val SOLE_V = 0.5f
private const val SOLE_DEPTH = 0.028f
private const val SOLE_RINGS = 32
private const val SOLE_SEGMENTS = 16

/** The sole's footprint in Top, from the socket under the ankle: the shoe's own Top drawing with a margin round it, past the toes in front. */
private const val PRINT_BACK = -0.045f
private const val PRINT_FRONT = 0.3f
private val SOLE_PRINT = loop(
    p(PRINT_FRONT, 0f), p(0.28f, 0.05f), p(0.19f, 0.072f), p(0.08f, 0.068f), p(-0.02f, 0.058f), p(PRINT_BACK, 0f),
    p(-0.02f, -0.058f), p(0.08f, -0.068f), p(0.19f, -0.072f), p(0.28f, -0.05f),
)

/** The foot the sandals show: its five toes, in the skin, where the bare foot is a shoe with none. */
private const val SANDAL_TOES = 5f

/**
 * The commands that dress the person: a body, an outer and a feet slot, a shirt on torso and arms, a jacket over it with its sleeves'
 * joins inked, sandals of a sole slab under each foot that
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
    SetJoinInk(JACKET, ARM_L, true),
    SetGarmentColor(JACKET, JACKET_CLOTH),
    AddGarment(Garment(SANDALS, "Sandals", FEET_SLOT)),
    SetGarmentColor(SANDALS, SANDAL_LEATHER),
    HandSockets.add(FOOT_L, SOLE_SOCKET, SOLE_U, SOLE_V, 0f, paired = false, sink = 0f),
    ownedPair(SANDALS, "sandal-soles", SANDAL_SOLE_L to SocketRef(FOOT_L, HandSockets.name(SOLE_SOCKET)), SANDAL_SOLE_R to SocketRef(FOOT_R, HandSockets.name(SOLE_SOCKET)), "blob"),
    *propShape(SANDAL_SOLE_L, slab(PRINT_BACK, PRINT_FRONT, SOLE_DEPTH), SOLE_PRINT, 1f, SOLE_RINGS, SOLE_SEGMENTS).toTypedArray(),
    SetBaseSlot(SANDAL_SOLE_L, SANDAL_LEATHER),
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
