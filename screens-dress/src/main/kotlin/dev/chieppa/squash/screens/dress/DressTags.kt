package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.LooseEndId
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.TuckKind

/** Test tags for the Dress stage's inspector and Library's Garments half. */
object DressTags {
    const val TOOL = "dress.tool"
    const val SMOOTH = "dress.tool.smooth"
    const val STABILIZER = "dress.tool.stabilizer"
    const val GARMENT = "dress.garment"
    const val SLOT_CHIP = "dress.garment.slot"
    const val LAYER_CHIP = "dress.garment.layer"
    const val FIT = "dress.garment.fit"
    const val FIT_PART = "dress.garment.fitPart"
    const val INK_HEM = "dress.garment.inkHem"
    const val COLOR = "dress.color"
    const val LAYERS = "dress.layers"
    const val OUTFITS = "library.outfits"
    const val NEW_OUTFIT = "library.newOutfit"
    const val NEW_SLOT = "library.newSlot"
    const val NO_SLOT = "library.noSlot"
    const val NO_GARMENTS = "library.noGarments"
    const val SLOT_NAME = "library.slotName"
    const val OUTFIT_NAME = "library.outfitName"
    const val GARMENT_NAME = "library.garmentName"

    /** An outfit or garment tile's thumbnail, once rendered. */
    const val THUMBNAIL = "library.thumbnail"
    const val MENU_WEAR = "library.menu.wear"
    const val MENU_RENAME = "library.menu.rename"
    const val MENU_MOVE = "library.menu.move"
    const val MENU_REMOVE = "library.menu.remove"
    const val MENU_DUPLICATE = "library.menu.duplicate"
    const val MENU_NEW_OUTFIT = "library.menu.newOutfit"
    const val MENU_RENAME_SLOT = "library.menu.renameSlot"
    const val MENU_NEW_SLOT = "library.menu.newSlot"
    const val MENU_REMOVE_SLOT = "library.menu.removeSlot"

    fun partFit(part: PartId) = "dress.garment.fit.${part.value}"
    fun removeFit(part: PartId) = "dress.garment.fit.remove.${part.value}"
    fun edge(index: Int) = "dress.garment.edge.$index"
    fun removeEdge(index: Int) = "dress.garment.edge.remove.$index"
    fun tuck(part: PartId, kind: TuckKind) = "dress.tuck.${part.value}.${kind.name}"
    fun tuckChoice(part: PartId, kind: TuckKind) = "dress.tuck.choice.${part.value}.${kind.name}"
    fun layer(garment: GarmentId) = "dress.layers.row.${garment.value}"
    fun layerShow(garment: GarmentId) = "dress.layers.show.${garment.value}"
    fun clash(part: PartId) = "dress.layers.clash.${part.value}"
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
