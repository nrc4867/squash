package dev.chieppa.squash.screens.single

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.fitSheet
import dev.chieppa.squash.screens.sheet.outlineSamples
import dev.chieppa.squash.character.catalog.DIRECTION_ORDER
import dev.chieppa.squash.uidesign.ThumbEdge

/** One view's thumbnail, named for the [body] direction that view is looked at from: its [edge], the [lines] drawn in it, the [preview] of an outline release will write and the template's [hint] where none is, in thumbnail pixels. One not on the view the camera shows [snaps] there. */
data class ViewThumbnail(
    val body: BodyView,
    val view: View,
    val edge: ThumbEdge,
    val lines: List<List<Offset>>,
    val hint: List<List<Offset>>,
    val preview: List<List<Offset>> = emptyList(),
) {
    val snaps: Boolean get() = edge != ThumbEdge.CURRENT
}

/**
 * [selection]'s views as thumbnails [width] by [height] pixels at one scale, upright as the panes show them, in [DIRECTION_ORDER]: each shows its
 * outline, or [live] or one of [alongside] in the view being drawn, or else the template's outline when [templateHint] is on.
 * The [current] view is lit and any other with nothing drawn is marked missing.
 */
fun viewThumbnails(
    selection: SheetSelection,
    current: View?,
    live: LiveStroke?,
    templateHint: Boolean,
    width: Int,
    height: Int,
    alongside: List<LiveStroke> = emptyList(),
): List<ViewThumbnail> {
    val part = selection.part
    val fit = fitSheet(part.box, part.axes, List(SheetPane.entries.size) { PixelRect(0, 0, width, height) })
    return DIRECTION_ORDER.map { body ->
        val view = part.axes.framing.getValue(body)
        val camera = LockedCamera(view, part.axes, fit, width, height, part.origin, selection.drawingScale)
        val stroke = (listOfNotNull(live) + alongside).firstOrNull { it.view == view }
        val drawn = stroke?.points ?: selection.outlines[view]?.let(::outlineSamples)
        val previewing = stroke?.preview == true
        ViewThumbnail(
            body,
            view,
            when {
                view == current -> ThumbEdge.CURRENT
                drawn == null -> ThumbEdge.MISSING
                else -> ThumbEdge.PLAIN
            },
            if (previewing) listOf(stroke.centre.map(camera::toPixel)).filter { it.isNotEmpty() } else listOfNotNull(drawn?.map(camera::toPixel)),
            if (drawn == null && templateHint) part.silhouette(view).map { path -> path.map(camera::toPixel) } else emptyList(),
            if (previewing) listOf(stroke.points.map(camera::toPixel)) else emptyList(),
        )
    }
}
