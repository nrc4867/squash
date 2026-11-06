package dev.chieppa.squash.screens.references

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.ToolSection
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.place_tool
import dev.chieppa.squash.uidesign.generated.rotate_tool
import dev.chieppa.squash.uidesign.generated.shell_tools_place
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TOOL_SIZE = 34.dp

/** The placing tools as a section of the strip: Place image and Rotate image. */
@Composable
fun placeToolSection(state: AppState): ToolSection {
    val armed by state.tool.collectAsState()
    val place = stringResource(Res.string.place_tool)
    val rotate = stringResource(Res.string.rotate_tool)
    val held = armed.takeIf { it.places }
    return ToolSection(ShellTags.section("place"), stringResource(Res.string.shell_tools_place), if (held == BuildTool.ROTATE_IMAGE) SquashIcons.RotateImage else SquashIcons.PlaceImage, 2, held) {
        Tooltip(place, listOf("G")) {
            ToolButton(SquashIcons.PlaceImage, place, armed == BuildTool.PLACE_IMAGE, { state.armTool(BuildTool.PLACE_IMAGE) }, Modifier.testTag(ShellTags.tool(BuildTool.PLACE_IMAGE)), size = TOOL_SIZE)
        }
        Tooltip(rotate, listOf("R")) {
            ToolButton(SquashIcons.RotateImage, rotate, armed == BuildTool.ROTATE_IMAGE, { state.armTool(BuildTool.ROTATE_IMAGE) }, Modifier.testTag(ShellTags.tool(BuildTool.ROTATE_IMAGE)), size = TOOL_SIZE)
        }
    }
}
