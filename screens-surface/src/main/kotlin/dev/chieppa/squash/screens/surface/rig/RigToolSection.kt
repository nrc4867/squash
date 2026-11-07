package dev.chieppa.squash.screens.surface.rig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.StripTool
import dev.chieppa.squash.screens.sheet.ToolSection
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.rig_tool_erase
import dev.chieppa.squash.uidesign.generated.rig_tool_paint
import dev.chieppa.squash.uidesign.generated.rig_tool_smooth
import dev.chieppa.squash.uidesign.generated.shell_tools_rig
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The name of a Rig stage tool. */
fun rigToolLabel(tool: BuildTool): StringResource = when (tool) {
    BuildTool.ERASE_WEIGHT -> Res.string.rig_tool_erase
    BuildTool.SMOOTH_WEIGHT -> Res.string.rig_tool_smooth
    else -> Res.string.rig_tool_paint
}

/** The Rig tools as a section of the strip: Paint weight, Erase weight and Smooth weight. Pressing the armed tool disarms it. */
@Composable
fun rigToolSection(state: AppState): ToolSection {
    val armed by state.tool.collectAsState()
    val icons = mapOf(
        BuildTool.PAINT_WEIGHT to SquashIcons.PaintWeight,
        BuildTool.ERASE_WEIGHT to SquashIcons.EraseWeight,
        BuildTool.SMOOTH_WEIGHT to SquashIcons.SmoothWeight,
    )
    val held = armed.takeIf { it in icons }
    return ToolSection(ShellTags.section("rig"), stringResource(Res.string.shell_tools_rig), icons[held] ?: SquashIcons.PaintWeight, icons.size, held) {
        icons.forEach { (tool, icon) -> StripTool(tool, icon, stringResource(rigToolLabel(tool)), armed, state::armTool) }
    }
}
