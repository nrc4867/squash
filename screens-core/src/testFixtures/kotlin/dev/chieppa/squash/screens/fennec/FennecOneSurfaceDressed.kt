package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SquashDocument

/** The outfit the one-surface fennec is dressed in: the winter outfit's sweater alone. */
val WINTER_SWEATER = OutfitId("winter-sweater")

/** The fennec built as one surface, its wardrobe the dressed fennec's, wearing the winter sweater alone. */
fun fennecOneSurfaceDressedDocument(): SquashDocument =
    (fennecDressing() + listOf(AddOutfit(Outfit(WINTER_SWEATER, "Winter sweater")), PickGarment(WINTER_SWEATER, BODY_SLOT, SWEATER), ShowOutfit(WINTER_SWEATER)))
        .fold(fennecOneSurfaceDocument()) { document, command -> command.apply(document).document }
