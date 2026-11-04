package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.sheet_layout
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchSizes
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.stringResource

/** The strip above the panes: [tools] on the left, scrolling where they run out of room, and the Layout control showing [layout] always whole on the right. */
@Composable
fun SheetStrip(
    modifier: Modifier = Modifier,
    layout: SheetLayout = SheetLayout.SHEET,
    onLayout: (SheetLayout) -> Unit = {},
    tools: @Composable RowScope.() -> Unit = {},
) {
    val c = Stretch.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = StretchSizes.header).background(c.surfaceRail).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tools,
            )
            BasicText(
                stringResource(Res.string.sheet_layout),
                Modifier.padding(end = 4.dp),
                style = Stretch.type.value.merge(TextStyle(color = c.inkMuted)),
            )
            LayoutControl(layout, onLayout)
        }
        Divider()
    }
}

@Composable
private fun LayoutControl(layout: SheetLayout, onLayout: (SheetLayout) -> Unit) {
    val labels = SheetLayout.entries.associateWith { stringResource(it.label) }
    Segmented(SheetLayout.entries, layout, onLayout, { labels.getValue(it) }, enabled = { it.built })
}
