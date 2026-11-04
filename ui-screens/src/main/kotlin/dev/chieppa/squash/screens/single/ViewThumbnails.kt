package dev.chieppa.squash.screens.single

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.fitSheet
import dev.chieppa.squash.screens.sheet.outlineSamples
import dev.chieppa.squash.screens.shell.VIEW_ORDER
import dev.chieppa.squash.uidesign.ThumbEdge

/** One view's thumbnail: its [edge], the [lines] drawn in it and the template's [hint] where none is, in thumbnail pixels. One not on the view the camera shows [snaps] there. */
data class ViewThumbnail(
    val view: View,
    val edge: ThumbEdge,
    val lines: List<List<Offset>>,
    val hint: List<List<Offset>>,
) {
    val snaps: Boolean get() = edge != ThumbEdge.CURRENT
}

/**
 * [selection]'s views as thumbnails [width] by [height] pixels at one scale, upright as the panes show them: each shows its
 * outline, or [live] in the view being drawn, or else the template's outline when [templateHint] is on. The [current] view
 * is lit and any other with nothing drawn is marked missing.
 */
fun viewThumbnails(selection: SheetSelection, current: View?, live: LiveStroke?, templateHint: Boolean, width: Int, height: Int): List<ViewThumbnail> {
    val part = selection.part
    val fit = fitSheet(part.box, part.axes, List(SheetPane.entries.size) { PixelRect(0, 0, width, height) })
    return VIEW_ORDER.map { view ->
        val camera = LockedCamera(view, part.axes, fit, width, height, part.origin, selection.drawingScale)
        val drawn = if (live?.view == view) live.points else selection.outlines[view]?.let(::outlineSamples)
        ViewThumbnail(
            view,
            when {
                view == current -> ThumbEdge.CURRENT
                drawn == null -> ThumbEdge.MISSING
                else -> ThumbEdge.PLAIN
            },
            listOfNotNull(drawn?.map(camera::toPixel)),
            if (drawn == null && templateHint) part.silhouette(view).map { path -> path.map(camera::toPixel) } else emptyList(),
        )
    }
}
