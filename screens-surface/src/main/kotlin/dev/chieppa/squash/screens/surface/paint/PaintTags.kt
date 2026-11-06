package dev.chieppa.squash.screens.surface.paint

/** Test tags for the Color inspector. */
object PaintTags {
    const val ADD_SLOT = "color.addSlot"
    const val REMOVE_SLOT = "color.removeSlot"
    const val BASE = "color.base"
    const val ALL_SLOTS = "color.allSlots"
    const val SLOT_NAME = "color.slotName"
    const val SLOT_NAME_FIELD = "color.slotNameField"
    const val PICKER = "color.picker"
    const val BRUSH_SIZE = "color.brushSize"
    const val SOFTNESS = "color.softness"
    const val FILL_REACH = "color.fillReach"
    const val SHADING = "color.shading"
    const val FAR_PARTS = "color.farParts"

    fun slot(name: String) = "color.slot.$name"
    fun section(name: String) = "color.section.$name"
    fun sectionSlot(name: String, slot: String) = "color.sectionSlot.$name.$slot"
    fun baseSlot(slot: String) = "color.baseSlot.$slot"
    fun row(tag: String) = "$tag.row"
    fun stroke(id: String) = "color.stroke.$id"
    fun strokeSlot(id: String, slot: String) = "color.strokeSlot.$id.$slot"
    fun strokeDelete(id: String) = "color.strokeDelete.$id"
    fun region(id: String) = "color.region.$id"
    fun regionInk(id: String) = "color.regionInk.$id"
    fun regionInkLabel(id: String) = "color.regionInkLabel.$id"
    fun regionSlot(id: String, slot: String) = "color.regionSlot.$id.$slot"
    fun regionDelete(id: String) = "color.regionDelete.$id"
}
