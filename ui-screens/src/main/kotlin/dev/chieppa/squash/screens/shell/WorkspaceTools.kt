package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_action_draw
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TOOL_SIZE = 34.dp

/** The workspace's tool buttons for the strip above the panes: Draw outline and Edit curves. */
@Composable
fun WorkspaceTools(state: AppState) {
    val tool by state.tool.collectAsState()
    ToolItem(BuildTool.DRAW_OUTLINE, StretchIcons.Pen, stringResource(Res.string.shell_action_draw), "D", tool) { state.tool.value = it }
    ToolItem(BuildTool.EDIT_CURVES, SquashIcons.EditCurves, stringResource(Res.string.shell_action_edit_curves), "E", tool) { state.tool.value = it }
}

@Composable
private fun ToolItem(tool: BuildTool, icon: ImageVector, label: String, key: String, armed: BuildTool, onArm: (BuildTool) -> Unit) {
    Tooltip(label, listOf(key)) {
        ToolButton(icon, label, tool == armed, { onArm(tool) }, Modifier.testTag(ShellTags.tool(tool)), size = TOOL_SIZE)
    }
}
