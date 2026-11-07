package dev.chieppa.squash.screens.surface.pose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.StripTool
import dev.chieppa.squash.screens.sheet.ToolSection
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.pose_tool_drag
import dev.chieppa.squash.uidesign.generated.pose_tool_look
import dev.chieppa.squash.uidesign.generated.pose_tool_pin
import dev.chieppa.squash.uidesign.generated.pose_tool_turn
import dev.chieppa.squash.uidesign.generated.shell_tools_pose
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The name of a Pose stage tool. */
fun poseToolLabel(tool: BuildTool): StringResource = when (tool) {
    BuildTool.TURN -> Res.string.pose_tool_turn
    BuildTool.PIN -> Res.string.pose_tool_pin
    BuildTool.LOOK -> Res.string.pose_tool_look
    else -> Res.string.pose_tool_drag
}

/**
 * The Pose tools in the strip's order: Drag, Turn, then the body curve's place, then Pin and Look. A tool missing from [BuildTool],
 * as Body curve is, leaves its place empty.
 */
val POSE_STRIP: List<Pair<BuildTool, ImageVector>> by lazy {
    listOf(
        BuildTool.DRAG to SquashIcons.Drag,
        BuildTool.TURN to SquashIcons.Turn,
        // The body curve's place.
        BuildTool.PIN to SquashIcons.Pin,
        BuildTool.LOOK to SquashIcons.Look,
    )
}

/** The Pose tools as a section of the strip. Pressing the armed tool disarms it. */
@Composable
fun poseToolSection(state: AppState): ToolSection {
    val armed by state.tool.collectAsState()
    val icons = POSE_STRIP.toMap()
    val held = armed.takeIf { it in icons }
    return ToolSection(ShellTags.section("pose"), stringResource(Res.string.shell_tools_pose), icons[held] ?: SquashIcons.Drag, icons.size, held) {
        POSE_STRIP.forEach { (tool, icon) -> StripTool(tool, icon, stringResource(poseToolLabel(tool)), armed, state::armTool) }
    }
}
