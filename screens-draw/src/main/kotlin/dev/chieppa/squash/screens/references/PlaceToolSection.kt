package dev.chieppa.squash.screens.references

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
import dev.chieppa.squash.uidesign.generated.place_tool
import dev.chieppa.squash.uidesign.generated.rotate_tool
import dev.chieppa.squash.uidesign.generated.shell_tools_place
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.getValue

private val TOOL_SIZE = 34.dp

/** The placing tools as a section of the strip: Place image and Rotate image. */
@Composable
fun placeToolSection(state: AppState): ToolSection {
    val armed by state.tool.collectAsState()
    val place = stringResource(Res.string.place_tool)
    val rotate = stringResource(Res.string.rotate_tool)
    val held = armed.takeIf { it.places }
    return ToolSection(ShellTags.section("place"), stringResource(Res.string.shell_tools_place), if (held == BuildTool.ROTATE_IMAGE) SquashIcons.RotateImage else SquashIcons.PlaceImage, 2, held) {
        StripTool(BuildTool.PLACE_IMAGE, SquashIcons.PlaceImage, place, armed, state::armTool)
        StripTool(BuildTool.ROTATE_IMAGE, SquashIcons.RotateImage, rotate, armed, state::armTool)
    }
}
