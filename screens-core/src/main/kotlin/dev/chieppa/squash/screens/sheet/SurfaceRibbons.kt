package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.RibbonDraw

/** The lines a frame draws on the character. The screens that hold the line rules supply it, and the panes ask per camera. */
interface SurfaceRibbons {
    /** The whole figure's lines through the free camera of [view], which fills [rect]. */
    fun whole(view: FullView, rect: PixelRect): List<RibbonDraw>

    /** The whole figure's lines through [view], any camera, for panes that show the figure whole. */
    fun figure(view: SurfaceView): List<RibbonDraw>

    /** The lines of [part] alone through [view], in the depth a selection draws in, in front of what is ghosted behind it. */
    fun selected(part: PartId, view: SurfaceView): List<RibbonDraw>
}
