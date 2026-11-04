package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.render.PixelRect

/**
 * The Sheet layout's panes in reading order, two by two, each named and labelled for how it looks at the body. Which of a part's
 * views each holds comes from the part's axes. Full has no locked [body] view.
 */
enum class SheetPane(val body: BodyView?) {
    TOP(BodyView.TOP),
    FULL(null),
    SIDE(BodyView.SIDE),
    FRONT(BodyView.FRONT),
}

private const val COLUMNS = 2

/** How many sheets sit in Full first's lower row, which holds this share of the height. */
private const val FULL_FIRST_SHEETS = 3

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

/** Each pane's rectangle in Full first: Full across the top two thirds, then Top, Side and Front side by side, in [SheetPane] order. */
fun fullFirstPaneRects(width: Int, height: Int, gap: Int): List<PixelRect> {
    val sheetHeight = (height - gap) / FULL_FIRST_SHEETS
    val sheetY = height - sheetHeight
    val sheetWidth = (width - (FULL_FIRST_SHEETS - 1) * gap) / FULL_FIRST_SHEETS
    val step = sheetWidth + gap
    return SheetPane.entries.map { pane ->
        when (pane) {
            SheetPane.FULL -> PixelRect(0, 0, width, sheetY - gap)
            SheetPane.TOP -> PixelRect(0, sheetY, sheetWidth, sheetHeight)
            SheetPane.SIDE -> PixelRect(step, sheetY, sheetWidth, sheetHeight)
            SheetPane.FRONT -> PixelRect(2 * step, sheetY, width - 2 * step, sheetHeight)
        }
    }
}

/** The bars between Full first's panes [rects] of a [width] wide area: the one under Full, then the two between the sheets. */
fun fullFirstDividers(rects: List<PixelRect>, width: Int): List<PixelRect> {
    val top = rects[SheetPane.TOP.ordinal]
    val side = rects[SheetPane.SIDE.ordinal]
    val front = rects[SheetPane.FRONT.ordinal]
    val full = rects[SheetPane.FULL.ordinal]
    return listOf(
        PixelRect(0, full.bottom, width, top.y - full.bottom),
        PixelRect(top.right, top.y, side.x - top.right, top.height),
        PixelRect(side.right, top.y, front.x - side.right, top.height),
    )
}

/** Each pane's rectangle in [layout], in [SheetPane] order. */
fun paneRects(layout: SheetLayout, width: Int, height: Int, gap: Int): List<PixelRect> =
    if (layout == SheetLayout.FULL_FIRST) fullFirstPaneRects(width, height, gap) else sheetPaneRects(width, height, gap)

/** The bars between [layout]'s panes [rects] of a [width] by [height] area. */
fun paneDividers(layout: SheetLayout, rects: List<PixelRect>, width: Int, height: Int): List<PixelRect> =
    if (layout == SheetLayout.FULL_FIRST) fullFirstDividers(rects, width) else sheetDividers(rects, width, height)
