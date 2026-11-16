package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.LooseEndId
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View

/** Test tags for the Dress stage's inspector and Library's Garments half. */
object DressTags {
    const val TOOL = "dress.tool"
    const val OFFSET = "dress.tool.offset"
    const val SMOOTH = "dress.tool.smooth"
    const val STABILIZER = "dress.tool.stabilizer"
    const val GARMENT = "dress.garment"
    const val SLOT_CHIP = "dress.garment.slot"
    const val LAYER_CHIP = "dress.garment.layer"
    const val COVER = "dress.garment.cover"
    const val FILLET = "dress.joins.fillet"
    const val INK_HEM = "dress.joins.inkHem"
    const val INK_CUFFS = "dress.joins.inkCuffs"
    const val COLOR = "dress.color"
    const val LAYERS = "dress.layers"
    const val BODY_UNDER = "dress.bodyUnder"
    const val HOLES = "dress.holes"
    const val OUTFITS = "library.outfits"
    const val NEW_OUTFIT = "library.newOutfit"
    const val NEW_SLOT = "library.newSlot"
    const val NO_SLOT = "library.noSlot"
    const val NO_GARMENTS = "library.noGarments"
    const val SLOT_NAME = "library.slotName"
    const val OUTFIT_NAME = "library.outfitName"
    const val GARMENT_NAME = "library.garmentName"
    const val MENU_WEAR = "library.menu.wear"
    const val MENU_RENAME = "library.menu.rename"
    const val MENU_MOVE = "library.menu.move"
    const val MENU_REMOVE = "library.menu.remove"
    const val MENU_DUPLICATE = "library.menu.duplicate"
    const val MENU_NEW_OUTFIT = "library.menu.newOutfit"
    const val MENU_RENAME_SLOT = "library.menu.renameSlot"
    const val MENU_NEW_SLOT = "library.menu.newSlot"
    const val MENU_REMOVE_SLOT = "library.menu.removeSlot"

    fun covers(part: PartId) = "dress.garment.covers.${part.value}"
    fun branch(part: PartId, view: View) = "dress.garment.branch.${part.value}.${view.name}"
    fun join(part: PartId) = "dress.joins.ink.${part.value}"
    fun layer(garment: GarmentId) = "dress.layers.row.${garment.value}"
    fun layerShow(garment: GarmentId) = "dress.layers.show.${garment.value}"
    fun layerGrip(garment: GarmentId) = "dress.layers.grip.${garment.value}"
    fun clash(part: PartId) = "dress.layers.clash.${part.value}"
    fun change(part: PartId) = "dress.bodyUnder.change.${part.value}"
    fun cuff(part: PartId) = "dress.bodyUnder.cuff.${part.value}"
    fun cuffSize(part: PartId) = "dress.bodyUnder.cuffSize.${part.value}"
    fun inkCuff(part: PartId) = "dress.bodyUnder.inkCuff.${part.value}"
    fun swap(part: PartId) = "dress.bodyUnder.swap.${part.value}"
    fun hole(part: PartId) = "dress.holes.${part.value}"
    fun outfit(outfit: OutfitId) = "library.outfit.${outfit.value}"
    fun slot(slot: GarmentSlotId) = "library.slot.${slot.value}"
    fun garmentTile(garment: GarmentId) = "library.garment.${garment.value}"
    fun newIn(slot: GarmentSlotId) = "library.new.${slot.value}"
    fun moveTo(slot: GarmentSlotId?) = "library.menu.move.${slot?.value ?: "none"}"
    fun looseEnd(end: LooseEndId) = "dress.looseEnd.${end.value}"
    fun follow(end: LooseEndId) = "dress.looseEnd.follow.${end.value}"
    fun spring(end: LooseEndId) = "dress.looseEnd.spring.${end.value}"
    fun collide(end: LooseEndId) = "dress.looseEnd.collide.${end.value}"
}
