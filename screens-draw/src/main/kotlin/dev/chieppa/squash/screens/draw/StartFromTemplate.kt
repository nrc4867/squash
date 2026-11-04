package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_action_start_template
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TOOL_SIZE = 34.dp

/** The tool strip's Start from template, faint while the active view has an outline. */
@Composable
fun StartFromTemplateButton(state: AppState) {
    state.session.collectAsState().value?.document?.collectAsState()?.value
    state.selectedPart.collectAsState().value
    state.activeView.collectAsState().value
    state.layout.collectAsState().value
    val label = stringResource(Res.string.shell_action_start_template)
    Tooltip(label, emptyList()) {
        ToolButton(
            SquashIcons.StartFromTemplate,
            label,
            false,
            state::startFromTemplate,
            Modifier.testTag(ShellTags.START_TEMPLATE),
            size = TOOL_SIZE,
            enabled = state.templateStartView() != null,
        )
    }
}
