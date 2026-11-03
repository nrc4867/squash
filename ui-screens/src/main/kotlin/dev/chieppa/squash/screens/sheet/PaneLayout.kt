package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.PixelRect

/** The Sheet layout's panes in reading order, two by two. Full has no locked [view]. */
enum class SheetPane(val view: View?) {
    TOP(View.TOP),
    FULL(null),
    SIDE(View.SIDE),
    FRONT(View.FRONT),
}

private const val COLUMNS = 2

/** Each pane's rectangle in a [width] by [height] area with [gap] pixels between them, in [SheetPane] order. */
fun sheetPaneRects(width: Int, height: Int, gap: Int): List<PixelRect> {
    val left = (width - gap) / COLUMNS
    val top = (height - gap) / COLUMNS
    val rightX = left + gap
    val bottomY = top + gap
    return SheetPane.entries.map { pane ->
        val inRightColumn = pane.ordinal % COLUMNS == 1
        val inBottomRow = pane.ordinal >= COLUMNS
        PixelRect(
            x = if (inRightColumn) rightX else 0,
            y = if (inBottomRow) bottomY else 0,
            width = if (inRightColumn) width - rightX else left,
            height = if (inBottomRow) height - bottomY else top,
        )
    }
}

/** The bars between the panes [rects] of a [width] by [height] area, the upright one first. */
fun sheetDividers(rects: List<PixelRect>, width: Int, height: Int): List<PixelRect> {
    val topLeft = rects[SheetPane.TOP.ordinal]
    val bottomRight = rects[SheetPane.FRONT.ordinal]
    return listOf(
        PixelRect(topLeft.right, 0, bottomRight.x - topLeft.right, height),
        PixelRect(0, topLeft.bottom, width, bottomRight.y - topLeft.bottom),
    )
}
