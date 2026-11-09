package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.getValue
import dev.chieppa.squash.uidesign.generated.move_tool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.ActionKeys
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.LocalActionKeys
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.screens.sheet.StripTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.mirrorApplies
import dev.chieppa.squash.screens.shell.rememberPartIsDrawn
import dev.chieppa.squash.screens.sheet.ToolSection
import dev.chieppa.squash.screens.sheet.ToolSections
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.add_part
import dev.chieppa.squash.uidesign.generated.color_tool_brush
import dev.chieppa.squash.uidesign.generated.color_tool_fill
import dev.chieppa.squash.uidesign.generated.color_tool_pick
import dev.chieppa.squash.uidesign.generated.color_tool_region_border
import dev.chieppa.squash.uidesign.generated.draw_half_outline
import dev.chieppa.squash.uidesign.generated.draw_tool_hint
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.edit_tool_mirror
import dev.chieppa.squash.uidesign.generated.edit_tool_oversketch
import dev.chieppa.squash.uidesign.generated.shell_action_draw
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.squash.uidesign.generated.shell_tools_color
import dev.chieppa.squash.uidesign.generated.shell_tools_outline
import dev.chieppa.squash.uidesign.generated.shell_tools_symmetry
import dev.chieppa.squash.uidesign.generated.smooth_tool
import dev.chieppa.squash.uidesign.generated.spine_tool
import dev.chieppa.stretch.ui.ToolButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.chieppa.squash.screens.dress.newGarment
import dev.chieppa.squash.uidesign.generated.dress_new_garment
import dev.chieppa.squash.uidesign.generated.dress_tool_cover
import dev.chieppa.squash.uidesign.generated.dress_tool_draw
import dev.chieppa.squash.uidesign.generated.dress_tool_loose_end
import dev.chieppa.squash.uidesign.generated.stage_dress
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TOOL_SIZE = 34.dp
private const val SYMMETRY_TOOLS = 2

/**
 * The tools of the stage on screen for the strip above the panes, in sections that collapse where the strip runs short. Shape holds Draw outline, which is
 * Oversketch once the part is drawn, Spine and width, Edit curves, Erase, Smooth and Move, then Mirror and Half outline, then Add part. Color holds
 * the paint tools and Lines holds [lines], Rig [rigs] and Pose [poses], each with Mirror; Face holds [faces]. Dress holds Draw garment, Edit curves,
 * Erase, Smooth, Cover and Loose end, then Mirror, always on since a branch on a pair covers both sides, and Half outline, then New garment.
 */
@Composable
fun WorkspaceTools(state: AppState, lines: ToolSection? = null, faces: ToolSection? = null, rigs: ToolSection? = null, poses: ToolSection? = null) {
    val tool by state.tool.collectAsState()
    val stage by state.stage.collectAsState()
    val addPart = stringResource(Res.string.add_part)
    val mirror = ToolSection(ShellTags.section("mirror"), stringResource(Res.string.edit_tool_mirror), SquashIcons.Mirror, 1) { MirrorButton(state) }
    val sections = when (stage) {
        Stage.SHAPE -> listOfNotNull(
            outlineSection(tool, state),
            ToolSection(ShellTags.section("symmetry"), stringResource(Res.string.shell_tools_symmetry), SquashIcons.Mirror, SYMMETRY_TOOLS) {
                MirrorButton(state)
                HalfOutlineButton(state)
            },
            ToolSection(ShellTags.section("add"), addPart, SquashIcons.AddPart, 1, tool.takeIf { it == BuildTool.ADD_PART }) {
                StripTool(BuildTool.ADD_PART, SquashIcons.AddPart, addPart, tool, state::armTool)
            },
        )
        Stage.COLOR -> listOf(paintSection(tool, state), mirror)
        Stage.LINES -> listOfNotNull(lines, mirror)
        Stage.FACE -> listOfNotNull(faces)
        Stage.RIG -> listOfNotNull(rigs, mirror)
        Stage.DRESS -> dressSections(tool, state)
        Stage.POSE -> listOfNotNull(poses, mirror)
    }
    ToolSections(sections, Modifier.fillMaxWidth())
}

@Composable
private fun outlineSection(tool: BuildTool, state: AppState): ToolSection {
    val drawn = rememberPartIsDrawn(state)
    val draw = stringResource(if (drawn) Res.string.edit_tool_oversketch else Res.string.shell_action_draw)
    val drawIcon = if (drawn) SquashIcons.Oversketch else SquashIcons.DrawOutline
    val drawHint = stringResource(Res.string.draw_tool_hint, draw)
    val spine = stringResource(Res.string.spine_tool)
    val curves = stringResource(Res.string.shell_action_edit_curves)
    val erase = stringResource(Res.string.edit_tool_erase)
    val smooth = stringResource(Res.string.smooth_tool)
    val move = stringResource(Res.string.move_tool)
    val icons = mapOf(
        BuildTool.DRAW_OUTLINE to drawIcon,
        BuildTool.SPINE_WIDTH to SquashIcons.SpineAndWidth,
        BuildTool.EDIT_CURVES to SquashIcons.EditCurves,
        BuildTool.ERASE to SquashIcons.Erase,
        BuildTool.SMOOTH to SquashIcons.SmoothCurve,
        BuildTool.MOVE to SquashIcons.Move,
    )
    val armed = tool.takeIf { it in icons }
    return ToolSection(ShellTags.section("outline"), stringResource(Res.string.shell_tools_outline), icons[armed] ?: drawIcon, icons.size, armed) {
        StripTool(BuildTool.DRAW_OUTLINE, drawIcon, draw, tool, state::armTool, hint = drawHint)
        StripTool(BuildTool.SPINE_WIDTH, SquashIcons.SpineAndWidth, spine, tool, state::armTool)
        StripTool(BuildTool.EDIT_CURVES, SquashIcons.EditCurves, curves, tool, state::armTool)
        StripTool(BuildTool.ERASE, SquashIcons.Erase, erase, tool, state::armTool)
        StripTool(BuildTool.SMOOTH, SquashIcons.SmoothCurve, smooth, tool, state::armTool)
        StripTool(BuildTool.MOVE, SquashIcons.Move, move, tool, state::armTool)
    }
}

@Composable
private fun paintSection(tool: BuildTool, state: AppState): ToolSection {
    val icons = mapOf(
        BuildTool.FILL to SquashIcons.Fill,
        BuildTool.BRUSH to SquashIcons.Brush,
        BuildTool.REGION_BORDER to SquashIcons.RegionBorder,
        BuildTool.PICK_COLOR to SquashIcons.PickColor,
        BuildTool.PAINT_ERASE to SquashIcons.Erase,
    )
    val armed = tool.takeIf { it in icons }
    return ToolSection(ShellTags.section("paint"), stringResource(Res.string.shell_tools_color), icons[armed] ?: SquashIcons.Fill, icons.size, armed) { PaintTools(tool, state) }
}

@Composable
private fun PaintTools(tool: BuildTool, state: AppState) {
    StripTool(BuildTool.FILL, SquashIcons.Fill, stringResource(Res.string.color_tool_fill), tool, state::armTool)
    StripTool(BuildTool.BRUSH, SquashIcons.Brush, stringResource(Res.string.color_tool_brush), tool, state::armTool)
    StripTool(BuildTool.REGION_BORDER, SquashIcons.RegionBorder, stringResource(Res.string.color_tool_region_border), tool, state::armTool)
    StripTool(BuildTool.PICK_COLOR, SquashIcons.PickColor, stringResource(Res.string.color_tool_pick), tool, state::armTool)
    StripTool(BuildTool.PAINT_ERASE, SquashIcons.Erase, stringResource(Res.string.edit_tool_erase), tool, state::armTool)
}

@Composable
private fun dressSections(tool: BuildTool, state: AppState): List<ToolSection> {
    val icons = mapOf(
        BuildTool.DRAW_GARMENT to SquashIcons.DrawGarment,
        BuildTool.EDIT_GARMENT to SquashIcons.EditCurves,
        BuildTool.ERASE_GARMENT to SquashIcons.Erase,
        BuildTool.SMOOTH_GARMENT to SquashIcons.SmoothCurve,
        BuildTool.COVER to SquashIcons.Cover,
        BuildTool.LOOSE_END to SquashIcons.LooseEnd,
    )
    val labels = mapOf(
        BuildTool.DRAW_GARMENT to stringResource(Res.string.dress_tool_draw),
        BuildTool.EDIT_GARMENT to stringResource(Res.string.shell_action_edit_curves),
        BuildTool.ERASE_GARMENT to stringResource(Res.string.edit_tool_erase),
        BuildTool.SMOOTH_GARMENT to stringResource(Res.string.smooth_tool),
        BuildTool.COVER to stringResource(Res.string.dress_tool_cover),
        BuildTool.LOOSE_END to stringResource(Res.string.dress_tool_loose_end),
    )
    val armed = tool.takeIf { it in icons }
    val newGarment = stringResource(Res.string.dress_new_garment)
    val scope = rememberCoroutineScope()
    return listOf(
        ToolSection(ShellTags.section("dress"), stringResource(Res.string.stage_dress), icons[armed] ?: SquashIcons.DrawGarment, icons.size, armed) {
            icons.forEach { (each, icon) -> StripTool(each, icon, labels.getValue(each), tool, state::armTool) }
        },
        ToolSection(ShellTags.section("symmetry"), stringResource(Res.string.shell_tools_symmetry), SquashIcons.Mirror, SYMMETRY_TOOLS) {
            HeldMirrorButton()
            HalfOutlineButton(state, ActionKeys.DRESS_HALF_OUTLINE)
        },
        ToolSection(ShellTags.section("new-garment"), newGarment, SquashIcons.NewGarment, 1) {
            Tooltip(newGarment, LocalActionKeys.current(ActionKeys.NEW_GARMENT)) {
                ToolButton(
                    SquashIcons.NewGarment,
                    newGarment,
                    false,
                    { scope.launch { state.newGarment() } },
                    Modifier.testTag(ShellTags.NEW_GARMENT),
                    size = TOOL_SIZE,
                    label = newGarment,
                )
            }
        },
    )
}

/** Mirror in Dress: lit and still, since a garment's branch on a pair covers both sides. */
@Composable
private fun HeldMirrorButton() {
    val label = stringResource(Res.string.edit_tool_mirror)
    Tooltip(label, LocalActionKeys.current(ActionKeys.MIRROR)) {
        ToolButton(SquashIcons.Mirror, label, true, {}, Modifier.testTag(ShellTags.MIRROR), size = TOOL_SIZE, enabled = false)
    }
}

/** Half outline: on, a stroke draws one edge and its mirror image across the part's centre line. */
@Composable
private fun HalfOutlineButton(state: AppState, key: String = ActionKeys.HALF_OUTLINE) {
    val label = stringResource(Res.string.draw_half_outline)
    Tooltip(label, LocalActionKeys.current(key)) {
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

/** Mirror: on, a change writes the pair, off, the selected side alone, enabled by [mirrorApplies] as its key is. */
@Composable
private fun MirrorButton(state: AppState) {
    val mirror by state.mirror.collectAsState()
    state.session.collectAsState().value?.document?.collectAsState()?.value
    state.selectedPart.collectAsState().value
    state.tool.collectAsState().value
    state.stage.collectAsState().value
    val paired = state.mirrorApplies()
    val label = stringResource(Res.string.edit_tool_mirror)
    Tooltip(label, LocalActionKeys.current(ActionKeys.MIRROR)) {
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
