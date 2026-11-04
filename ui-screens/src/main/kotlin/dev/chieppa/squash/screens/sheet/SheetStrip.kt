package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import dev.chieppa.squash.uidesign.generated.sheet_layout_focus
import dev.chieppa.squash.uidesign.generated.sheet_layout_full_first
import dev.chieppa.squash.uidesign.generated.sheet_layout_sheet
import dev.chieppa.squash.uidesign.generated.sheet_layout_single
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchSizes
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The workspace's layouts, in the Layout control's order. */
enum class SheetLayout(val label: StringResource, val built: Boolean = false) {
    SINGLE(Res.string.sheet_layout_single, built = true),
    FOCUS(Res.string.sheet_layout_focus),
    SHEET(Res.string.sheet_layout_sheet, built = true),
    FULL_FIRST(Res.string.sheet_layout_full_first),
}

/** The strip above the panes: [tools] on the left, the Layout control showing [layout] on the right. */
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
            tools()
            Spacer(Modifier.weight(1f))
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
