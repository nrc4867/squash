package dev.chieppa.squash.screens.surface.paint

/** Test tags for the Color drawer. */
object PaintTags {
    const val ADD_SLOT = "color.addSlot"
    const val REMOVE_SLOT = "color.removeSlot"
    const val PICKER = "color.picker"
    const val BRUSH_SIZE = "color.brushSize"
    const val SOFTNESS = "color.softness"
    const val FILL_REACH = "color.fillReach"
    const val SHADING = "color.shading"
    const val FAR_PARTS = "color.farParts"

    fun slot(name: String) = "color.slot.$name"
    fun section(name: String) = "color.section.$name"
    fun region(id: String) = "color.region.$id"
    fun regionInk(id: String) = "color.regionInk.$id"
}
