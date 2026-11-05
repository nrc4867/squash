package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.sheet_layout
import dev.chieppa.stretch.frame.Dropdown
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchSizes
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.stringResource

/** How wide the strip must be for every tool and the Layout control to show whole. Below it the control folds into a dropdown, leaving the room to the tools. */
private val WHOLE_LAYOUT_FROM = 1140.dp
private val LAYOUT_DROPDOWN_WIDTH = 120.dp

/** The strip above the panes: [tools] on the left, which share out the room left to them, and the Layout control showing [layout] on the right. */
@Composable
fun SheetStrip(
    modifier: Modifier = Modifier,
    layout: SheetLayout = SheetLayout.SHEET,
    onLayout: (SheetLayout) -> Unit = {},
    tools: @Composable RowScope.() -> Unit = {},
) {
    val c = Stretch.colors
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val whole = maxWidth >= WHOLE_LAYOUT_FROM
            Row(
                Modifier.fillMaxWidth().heightIn(min = StretchSizes.header).background(c.surfaceRail).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tools,
                )
                if (whole) {
                    BasicText(
                        stringResource(Res.string.sheet_layout),
                        Modifier.padding(end = 4.dp),
                        style = Stretch.type.value.merge(TextStyle(color = c.inkMuted)),
                    )
                    LayoutControl(layout, onLayout)
                } else {
                    LayoutDropdown(layout, onLayout)
                }
            }
        }
        Divider()
    }
}

@Composable
private fun LayoutDropdown(layout: SheetLayout, onLayout: (SheetLayout) -> Unit) {
    val labels = SheetLayout.entries.associateWith { stringResource(it.label) }
    Dropdown(SheetLayout.entries.filter { it.built }, layout, onLayout, { labels.getValue(it) }, stringResource(Res.string.sheet_layout), Modifier.width(LAYOUT_DROPDOWN_WIDTH))
}

@Composable
private fun LayoutControl(layout: SheetLayout, onLayout: (SheetLayout) -> Unit) {
    val labels = SheetLayout.entries.associateWith { stringResource(it.label) }
    Segmented(SheetLayout.entries, layout, onLayout, { labels.getValue(it) }, enabled = { it.built })
}
