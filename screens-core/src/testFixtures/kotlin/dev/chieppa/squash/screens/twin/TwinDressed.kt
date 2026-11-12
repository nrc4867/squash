package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.screens.woman.BOTTOM_SLOT
import dev.chieppa.squash.screens.woman.EYES_SLOT
import dev.chieppa.squash.screens.woman.FACE_SLOT
import dev.chieppa.squash.screens.woman.FEET_SLOT
import dev.chieppa.squash.screens.woman.NECK_SLOT
import dev.chieppa.squash.screens.woman.OUTER_SLOT
import dev.chieppa.squash.screens.woman.TOP_SLOT
import dev.chieppa.squash.screens.woman.WAIST_SLOT

/** Where the saved dressed twin lies among the test fixtures' resources. */
const val TWIN_DRESSED_RESOURCE = "woman-twin-dressed.json"

/** The outfits by the reference's names: the natural look at the beach, the office, the dress and the goth look. */
val BEACH = OutfitId("beach")
val LAWYER = OutfitId("lawyer")
val DRESS = OutfitId("dress")
val GOTH = OutfitId("goth")

val LAWYER_FACE = GarmentId("lawyer-face")
val DRESS_FACE = GarmentId("dress-face")
val GOTH_FACE = GarmentId("goth-face")

/** The reference's face looks for the office, the dress and the goth outfit; the beach wears the natural look the face is painted in. */
private val LAWYER_LOOK = FaceLook(lips = 0xC4625F, shadow = 0xB98B7E, shadowAmount = 0.5f, blushAmount = 0.30f)
private val DRESS_LOOK = FaceLook(lips = 0xD8727A, shadow = 0xD69A8A, shadowAmount = 0.45f, blushAmount = 0.50f)
private val GOTH_LOOK = FaceLook(lips = 0x3B1630, shadow = 0x3A2438, shadowAmount = 0.95f, blushAmount = 0f)

/** A face look worn as a garment: lips, eyeshadow and blush painted over the natural face in their own palette slots. */
private fun look(id: GarmentId, name: String, look: FaceLook): List<DocumentCommand> {
    val lips = SlotName("${id.value}-lips")
    val lid = SlotName("${id.value}-lid")
    val blush = SlotName("${id.value}-blush")
    val face = BodyPartChange.Redraw(PartData(surface = SurfaceWork(regions = lipRegions(lips) + lidShadows(lid) + blushRegions(blush))))
    return listOf(SetPaletteSlot(lips, look.lipColor), SetPaletteSlot(lid, look.lidColor), SetPaletteSlot(blush, look.blushColor)) +
        twinGarment(id, name, FACE_SLOT, lips, emptyList(), changes = mapOf(HEAD to face))
}

/** Each outfit's name and what it picks per slot. */
val TWIN_OUTFITS: List<Triple<OutfitId, String, Map<GarmentSlotId, GarmentId>>> = listOf(
    Triple(BEACH, "Beach", mapOf(TOP_SLOT to BIKINI, BOTTOM_SLOT to SHORTS, FEET_SLOT to SANDALS, STRAPS_SLOT to SANDAL_STRAPS, EYES_SLOT to SHADES)),
    Triple(LAWYER, "Lawyer", mapOf(TOP_SLOT to BLOUSE, OUTER_SLOT to BLAZER, BOTTOM_SLOT to TROUSERS, FEET_SLOT to PUMPS, STRAPS_SLOT to PUMP_VAMPS, FACE_SLOT to LAWYER_FACE)),
    Triple(DRESS, "Dress", mapOf(TOP_SLOT to CREAM_TOP, BOTTOM_SLOT to SKIRT, WAIST_SLOT to DRESS_BELT, FEET_SLOT to FLATS, STRAPS_SLOT to FLAT_VAMPS, FACE_SLOT to DRESS_FACE)),
    Triple(
        GOTH, "Goth",
        mapOf(TOP_SLOT to DARK_TOP, BOTTOM_SLOT to BLACK_JEANS, WAIST_SLOT to GOTH_BELT, FEET_SLOT to BOOTS, NECK_SLOT to CHOKER, FACE_SLOT to GOTH_FACE),
    ),
)

private fun outfits(): List<DocumentCommand> = TWIN_OUTFITS.flatMap { (id, name, picks) ->
    listOf(AddOutfit(Outfit(id, name))) + picks.map { (slot, garment) -> PickGarment(id, slot, garment) }
}

/** The commands that dress her: the slots, every garment, the face looks and the four outfits, the beach shown. */
fun twinDressing(): List<DocumentCommand> = twinSlots() + twinBeach() + twinShades() + twinLawyer() + twinDress() + twinGoth() + twinShoes() +
    look(LAWYER_FACE, "Lawyer face", LAWYER_LOOK) + look(DRESS_FACE, "Dress face", DRESS_LOOK) + look(GOTH_FACE, "Goth face", GOTH_LOOK) +
    outfits() + ShowOutfit(BEACH)

/** Every command that makes the dressed twin: the twin, then her clothes. */
fun twinDressedCommands(): List<DocumentCommand> = twinCommands() + twinDressing()

/** The dressed twin as those commands leave it. */
fun twinDressedDocument(): SquashDocument = twinDressedCommands().fold(EMPTY_TWIN) { document, command -> command.apply(document).document }

/** The dressed twin from the example file. */
fun savedTwinDressed(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(TWIN_DRESSED_RESOURCE)) { "No $TWIN_DRESSED_RESOURCE" }.readText())
