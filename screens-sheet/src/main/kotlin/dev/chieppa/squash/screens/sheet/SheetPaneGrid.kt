package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/** The four panes where [paneRects] puts them in [layout], so Compose and the render thread agree to the pixel. */
@Composable
fun SheetPaneGrid(gap: Int, modifier: Modifier = Modifier, layout: SheetLayout = SheetLayout.SHEET, focused: SheetPane = SheetPane.SIDE, pane: @Composable (SheetPane) -> Unit) {
    Layout(contents = SheetPane.entries.map { each -> { pane(each) } }, modifier = modifier) { measurables, constraints ->
        val rects = paneRects(layout, constraints.maxWidth, constraints.maxHeight, gap, focused)
        val placeables = measurables.mapIndexed { i, slot ->
            slot.map { it.measure(Constraints.fixed(rects[i].width.coerceAtLeast(0), rects[i].height.coerceAtLeast(0))) }
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, slot -> slot.forEach { it.place(rects[i].x, rects[i].y) } }
        }
    }
}
