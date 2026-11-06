package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.sheet.ToolSection
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.fur_comb_tool
import dev.chieppa.squash.uidesign.generated.fur_tool
import dev.chieppa.squash.uidesign.generated.lines_tool_floating_stroke
import dev.chieppa.squash.uidesign.generated.lines_tool_surface_stroke
import dev.chieppa.squash.uidesign.generated.lines_tool_width
import dev.chieppa.squash.uidesign.generated.shell_tools_lines
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TOOL_SIZE = 34.dp

/** The strip's tool buttons for lines and fur: Line width, Fur stroke, Comb, Surface stroke, Floating stroke and Erase. Pressing the armed tool disarms it. */
@Composable
fun LineTools(state: AppState) {
    val armed by state.tool.collectAsState()
    Item(BuildTool.LINE_WIDTH, SquashIcons.LineWidth, stringResource(Res.string.lines_tool_width), "W", armed, state::armTool)
    Item(BuildTool.FUR_STROKE, SquashIcons.FurStroke, stringResource(Res.string.fur_tool), "U", armed, state::armTool)
    Item(BuildTool.COMB, SquashIcons.Comb, stringResource(Res.string.fur_comb_tool), "G", armed, state::armTool)
    Item(BuildTool.SURFACE_STROKE, SquashIcons.SurfaceStroke, stringResource(Res.string.lines_tool_surface_stroke), "D", armed, state::armTool)
    Item(BuildTool.FLOATING_STROKE, SquashIcons.FloatingStroke, stringResource(Res.string.lines_tool_floating_stroke), "L", armed, state::armTool)
    Item(BuildTool.SURFACE_ERASE, SquashIcons.Erase, stringResource(Res.string.edit_tool_erase), "X", armed, state::armTool)
}

/** The lines and fur tools as a section of the strip. */
@Composable
fun lineToolSection(state: AppState): ToolSection {
    val armed by state.tool.collectAsState()
    val icons = mapOf(
        BuildTool.LINE_WIDTH to SquashIcons.LineWidth,
        BuildTool.FUR_STROKE to SquashIcons.FurStroke,
        BuildTool.COMB to SquashIcons.Comb,
        BuildTool.SURFACE_STROKE to SquashIcons.SurfaceStroke,
        BuildTool.FLOATING_STROKE to SquashIcons.FloatingStroke,
        BuildTool.SURFACE_ERASE to SquashIcons.Erase,
    )
    val held = armed.takeIf { it in icons }
    return ToolSection(ShellTags.section("lines"), stringResource(Res.string.shell_tools_lines), icons[held] ?: SquashIcons.LineWidth, icons.size, held) { LineTools(state) }
}

@Composable
private fun Item(tool: BuildTool, icon: ImageVector, label: String, key: String, armed: BuildTool, onArm: (BuildTool) -> Unit) {
    Tooltip(label, listOf(key)) {
        ToolButton(icon, label, tool == armed, { onArm(tool) }, Modifier.testTag(ShellTags.tool(tool)), size = TOOL_SIZE)
    }
}
