package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument

/** Where the saved dressed cat lies among the test fixtures' resources. */
const val CAT_DRESSED_RESOURCE = "cat-dressed.json"

val BODY_SLOT = GarmentSlotId("body")
val OUTER_SLOT = GarmentSlotId("outer")
val FACE_SLOT = GarmentSlotId("face")
val VAMPIRE = OutfitId("vampire")
val HOTDOG = OutfitId("hotdog")
val CAT_BARE = OutfitId("bare")

/** The commands that dress the cat: body, outer and face slots, the vampire's and the hotdog's garments, and the Vampire, Hotdog and Bare outfits. */
fun catDressing(): List<DocumentCommand> = listOf(
    AddGarmentSlot(GarmentSlot(BODY_SLOT, "Body")),
    AddGarmentSlot(GarmentSlot(OUTER_SLOT, "Outer")),
    AddGarmentSlot(GarmentSlot(FACE_SLOT, "Face")),
) + vampire() + hotdog() + listOf(
    AddOutfit(Outfit(VAMPIRE, "Vampire")),
    PickGarment(VAMPIRE, OUTER_SLOT, CAPE),
    PickGarment(VAMPIRE, FACE_SLOT, FANGS),
    AddOutfit(Outfit(HOTDOG, "Hotdog")),
    PickGarment(HOTDOG, BODY_SLOT, SAUSAGE),
    PickGarment(HOTDOG, OUTER_SLOT, BUN),
    AddOutfit(Outfit(CAT_BARE, "Bare")),
    ShowOutfit(VAMPIRE),
)

/** Every command that makes the dressed cat: the cat, then its clothes. */
fun catDressedCommands(): List<DocumentCommand> = catCommands() + catDressing()

/** The dressed cat as those commands leave it. */
fun catDressedDocument(): SquashDocument = catDressedCommands().fold(EMPTY_CAT) { document, command -> command.apply(document).document }

/** The dressed cat from the example file. */
fun savedCatDressed(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(CAT_DRESSED_RESOURCE)) { "No $CAT_DRESSED_RESOURCE" }.readText())
