package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.StartFromTemplateButton
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.draw_half_outline
import dev.chieppa.squash.uidesign.generated.draw_tool_hint
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.edit_tool_mirror
import dev.chieppa.squash.uidesign.generated.edit_tool_oversketch
import dev.chieppa.squash.uidesign.generated.shell_action_draw
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.squash.uidesign.generated.spine_tool
import dev.chieppa.stretch.frame.ToolbarSeparator
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TOOL_SIZE = 34.dp

/**
 * The workspace's tool buttons for the strip above the panes: Draw outline, which is Oversketch once the part is drawn,
 * Spine and width, Edit curves and Erase, then Mirror, which only a paired part has any use for, Half outline and Start
 * from template. Pressing the armed tool disarms it.
 */
@Composable
fun WorkspaceTools(state: AppState) {
    val tool by state.tool.collectAsState()
    val drawn = rememberPartIsDrawn(state)
    val draw = stringResource(if (drawn) Res.string.edit_tool_oversketch else Res.string.shell_action_draw)
    ToolItem(
        BuildTool.DRAW_OUTLINE,
        if (drawn) SquashIcons.Oversketch else SquashIcons.DrawOutline,
        draw,
        "D",
        tool,
        state::armTool,
        hint = stringResource(Res.string.draw_tool_hint, draw),
    )
    ToolItem(BuildTool.SPINE_WIDTH, SquashIcons.SpineAndWidth, stringResource(Res.string.spine_tool), "W", tool, state::armTool)
    ToolItem(BuildTool.EDIT_CURVES, SquashIcons.EditCurves, stringResource(Res.string.shell_action_edit_curves), "E", tool, state::armTool)
    ToolItem(BuildTool.ERASE, SquashIcons.Erase, stringResource(Res.string.edit_tool_erase), "X", tool, state::armTool)
    ToolbarSeparator()
    MirrorButton(state)
    HalfOutlineButton(state)
    ToolbarSeparator()
    StartFromTemplateButton(state)
}

/** Half outline: on, a stroke draws one edge and its mirror image across the part's centre line. */
@Composable
private fun HalfOutlineButton(state: AppState) {
    val label = stringResource(Res.string.draw_half_outline)
    Tooltip(label, listOf("H")) {
        ToolButton(
            SquashIcons.HalfOutline,
            label,
            state.curves.halfOutline,
            { state.curves.halfOutline = !state.curves.halfOutline },
            Modifier.testTag(ShellTags.HALF_OUTLINE),
            size = TOOL_SIZE,
        )
    }
}

/** Mirror: on, a change writes the pair; off, the selected side alone. */
@Composable
private fun MirrorButton(state: AppState) {
    val mirror by state.mirror.collectAsState()
    val session by state.session.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val document = session?.document?.collectAsState()?.value
    val paired = selected?.let { document?.character?.part(it) }?.pair != null
    val label = stringResource(Res.string.edit_tool_mirror)
    Tooltip(label, listOf("M")) {
        ToolButton(
            SquashIcons.Mirror,
            label,
            paired && mirror,
            { state.mirror.value = !mirror },
            Modifier.testTag(ShellTags.MIRROR),
            size = TOOL_SIZE,
            enabled = paired,
        )
    }
}

/** Whether the selected part has an outline drawn, which the tools and the drawer read as a part being reshaped. */
@Composable
fun rememberPartIsDrawn(state: AppState): Boolean {
    val session by state.session.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val document = session?.document?.collectAsState()?.value
    return selected?.let { document?.character?.resolved(it) }?.outlines?.isNotEmpty() == true
}

@Composable
private fun ToolItem(tool: BuildTool, icon: ImageVector, label: String, key: String, armed: BuildTool, onArm: (BuildTool) -> Unit, hint: String = label) {
    Tooltip(hint, listOf(key)) {
        ToolButton(icon, label, tool == armed, { onArm(tool) }, Modifier.testTag(ShellTags.tool(tool)), size = TOOL_SIZE)
    }
}
