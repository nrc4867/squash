package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.ToolSection
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.sheet.StripTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.face_tool_draw
import dev.chieppa.squash.uidesign.generated.face_tool_gaze
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.squash.uidesign.generated.shell_tools_face
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.getValue

private val TOOL_SIZE = 34.dp

/** The face tools as a section of the strip: Draw mark, Edit curves, Erase and Gaze target. Pressing the armed tool disarms it. */
@Composable
fun faceToolSection(state: AppState): ToolSection {
    val armed by state.tool.collectAsState()
    val icons = mapOf(
        BuildTool.DRAW_MARK to SquashIcons.DrawMark,
        BuildTool.EDIT_MARK to SquashIcons.EditCurves,
        BuildTool.ERASE_MARK to SquashIcons.Erase,
        BuildTool.GAZE_TARGET to SquashIcons.GazeTarget,
    )
    val held = armed.takeIf { it in icons }
    return ToolSection(ShellTags.section("face"), stringResource(Res.string.shell_tools_face), icons[held] ?: SquashIcons.DrawMark, icons.size, held) {
        StripTool(BuildTool.DRAW_MARK, SquashIcons.DrawMark, stringResource(Res.string.face_tool_draw), armed, state::armTool)
        StripTool(BuildTool.EDIT_MARK, SquashIcons.EditCurves, stringResource(Res.string.shell_action_edit_curves), armed, state::armTool)
        StripTool(BuildTool.ERASE_MARK, SquashIcons.Erase, stringResource(Res.string.edit_tool_erase), armed, state::armTool)
        StripTool(BuildTool.GAZE_TARGET, SquashIcons.GazeTarget, stringResource(Res.string.face_tool_gaze), armed, state::armTool)
    }
}
