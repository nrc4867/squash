package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.sheet.StripTool
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.getValue

private val TOOL_SIZE = 34.dp

/** The strip's tool buttons for lines and fur: Line width, Fur stroke, Comb, Surface stroke, Floating stroke and Erase. Pressing the armed tool disarms it. */
@Composable
fun LineTools(state: AppState) {
    val armed by state.tool.collectAsState()
    StripTool(BuildTool.LINE_WIDTH, SquashIcons.LineWidth, stringResource(Res.string.lines_tool_width), armed, state::armTool)
    StripTool(BuildTool.FUR_STROKE, SquashIcons.FurStroke, stringResource(Res.string.fur_tool), armed, state::armTool)
    StripTool(BuildTool.COMB, SquashIcons.Comb, stringResource(Res.string.fur_comb_tool), armed, state::armTool)
    StripTool(BuildTool.SURFACE_STROKE, SquashIcons.SurfaceStroke, stringResource(Res.string.lines_tool_surface_stroke), armed, state::armTool)
    StripTool(BuildTool.FLOATING_STROKE, SquashIcons.FloatingStroke, stringResource(Res.string.lines_tool_floating_stroke), armed, state::armTool)
    StripTool(BuildTool.SURFACE_ERASE, SquashIcons.Erase, stringResource(Res.string.edit_tool_erase), armed, state::armTool)
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
