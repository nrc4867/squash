package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.ToolSection
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.face_tool_draw
import dev.chieppa.squash.uidesign.generated.face_tool_gaze
import dev.chieppa.squash.uidesign.generated.lines_tool_floating_stroke
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.squash.uidesign.generated.shell_tools_face
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TOOL_SIZE = 34.dp

/** The face tools as a section of the strip: Draw mark, Floating stroke, Edit curves, Erase and Gaze target. Pressing the armed tool disarms it. */
@Composable
fun faceToolSection(state: AppState): ToolSection {
    val armed by state.tool.collectAsState()
    val icons = mapOf(
        BuildTool.DRAW_MARK to SquashIcons.DrawMark,
        BuildTool.FLOATING_MARK to SquashIcons.FloatingStroke,
        BuildTool.EDIT_MARK to SquashIcons.EditCurves,
        BuildTool.ERASE_MARK to SquashIcons.Erase,
        BuildTool.GAZE_TARGET to SquashIcons.GazeTarget,
    )
    val held = armed.takeIf { it in icons }
    return ToolSection(ShellTags.section("face"), stringResource(Res.string.shell_tools_face), icons[held] ?: SquashIcons.DrawMark, icons.size, held) {
        Item(BuildTool.DRAW_MARK, SquashIcons.DrawMark, stringResource(Res.string.face_tool_draw), "D", armed, state::armTool)
        Item(BuildTool.FLOATING_MARK, SquashIcons.FloatingStroke, stringResource(Res.string.lines_tool_floating_stroke), "L", armed, state::armTool)
        Item(BuildTool.EDIT_MARK, SquashIcons.EditCurves, stringResource(Res.string.shell_action_edit_curves), "E", armed, state::armTool)
        Item(BuildTool.ERASE_MARK, SquashIcons.Erase, stringResource(Res.string.edit_tool_erase), "X", armed, state::armTool)
        Item(BuildTool.GAZE_TARGET, SquashIcons.GazeTarget, stringResource(Res.string.face_tool_gaze), "G", armed, state::armTool)
    }
}

@Composable
private fun Item(tool: BuildTool, icon: ImageVector, label: String, key: String, armed: BuildTool, onArm: (BuildTool) -> Unit) {
    Tooltip(label, listOf(key)) {
        ToolButton(icon, label, tool == armed, { onArm(tool) }, Modifier.testTag(ShellTags.tool(tool)), size = TOOL_SIZE)
    }
}
