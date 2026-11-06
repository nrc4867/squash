package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.catalog.VIEW_ORDER
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.anchors
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.viewLabel
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_none
import dev.chieppa.squash.uidesign.generated.edit_outlines
import dev.chieppa.squash.uidesign.generated.edit_points
import dev.chieppa.squash.uidesign.generated.shell_action_start_template
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.ui.IconButton
import org.jetbrains.compose.resources.stringResource

/**
 * The selected part's outlines, a row a view: each says how many points its outline has, or none, and picking one makes that view the active
 * one. An undrawn view's row offers Start from template, which turns the template's outline there into the part's own. [status] says what a row
 * reads in place of its count, if anything.
 */
@Composable
fun OutlineRowsSection(
    state: AppState,
    outlines: Map<View, OutlineCurve>,
    tag: (View) -> String,
    divider: Boolean = true,
    status: @Composable (View) -> String? = { null },
) {
    val active by state.activeView.collectAsState()
    val start = stringResource(Res.string.shell_action_start_template)
    DrawerSection(stringResource(Res.string.edit_outlines), divider = divider) {
        VIEW_ORDER.forEach { view ->
            val anchors = outlines[view]?.anchors?.size
            ListRow(view == active, { state.activeView.value = view }, Modifier.testTag(tag(view))) {
                RowIcon(SquashIcons.EditCurves)
                RowText(viewLabel(view), Modifier.weight(1f))
                RowText(status(view) ?: if (anchors != null) stringResource(Res.string.edit_points, anchors) else stringResource(Res.string.edit_none), subtle = true)
                if (anchors == null) {
                    IconButton(SquashIcons.StartFromTemplate, start, { state.startFromTemplate(view) }, Modifier.testTag(ShellTags.startTemplate(view)), enabled = state.canStartFromTemplate(view))
                }
            }
        }
    }
}
