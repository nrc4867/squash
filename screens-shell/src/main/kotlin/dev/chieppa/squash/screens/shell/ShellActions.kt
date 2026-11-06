package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.uidesign.generated.sheet_pane_fit
import dev.chieppa.squash.uidesign.generated.place_tool
import dev.chieppa.squash.uidesign.generated.rotate_tool
import dev.chieppa.squash.uidesign.generated.sheet_pane_swap
import dev.chieppa.squash.uidesign.generated.move_tool
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.draw.startFromTemplate
import dev.chieppa.squash.screens.draw.templateStartView
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_tool_brush
import dev.chieppa.squash.uidesign.generated.color_tool_fill
import dev.chieppa.squash.uidesign.generated.color_tool_pick
import dev.chieppa.squash.uidesign.generated.color_tool_region_border
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.draw_half_outline
import dev.chieppa.squash.uidesign.generated.edit_tool_mirror
import dev.chieppa.squash.uidesign.generated.pick_tool_add
import dev.chieppa.squash.uidesign.generated.pick_tool_select
import dev.chieppa.squash.uidesign.generated.shell_action_close
import dev.chieppa.squash.uidesign.generated.shell_action_commands
import dev.chieppa.squash.uidesign.generated.shell_action_draw
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.squash.uidesign.generated.shell_action_new
import dev.chieppa.squash.uidesign.generated.shell_action_open
import dev.chieppa.squash.uidesign.generated.shell_action_redo
import dev.chieppa.squash.uidesign.generated.shell_action_reset_layout
import dev.chieppa.squash.uidesign.generated.shell_action_put_down
import dev.chieppa.squash.uidesign.generated.shell_action_save
import dev.chieppa.squash.uidesign.generated.shell_action_save_as
import dev.chieppa.squash.uidesign.generated.shell_action_start_template
import dev.chieppa.squash.uidesign.generated.shell_action_undo
import dev.chieppa.squash.uidesign.generated.shell_drawer_help
import dev.chieppa.squash.uidesign.generated.shell_drawer_history
import dev.chieppa.squash.uidesign.generated.shell_drawer_inspector
import dev.chieppa.squash.uidesign.generated.shell_drawer_color
import dev.chieppa.squash.uidesign.generated.shell_drawer_lines
import dev.chieppa.squash.uidesign.generated.stage_face
import dev.chieppa.squash.uidesign.generated.stage_shape
import dev.chieppa.squash.uidesign.generated.shell_drawer_parts
import dev.chieppa.squash.uidesign.generated.shell_drawer_types
import dev.chieppa.squash.uidesign.generated.lines_tool_floating_stroke
import dev.chieppa.squash.uidesign.generated.lines_tool_surface_stroke
import dev.chieppa.squash.uidesign.generated.lines_tool_width
import dev.chieppa.squash.uidesign.generated.face_tool_draw
import dev.chieppa.squash.uidesign.generated.face_tool_gaze
import dev.chieppa.squash.uidesign.generated.fur_comb_tool
import dev.chieppa.squash.uidesign.generated.fur_tool
import dev.chieppa.squash.uidesign.generated.smooth_tool
import dev.chieppa.squash.uidesign.generated.spine_tool
import dev.chieppa.stretch.core.Action
import dev.chieppa.stretch.core.ActionId
import dev.chieppa.stretch.core.KeyChord
import dev.chieppa.stretch.theme.StretchIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The shell's actions. An action's text key is its id. */
object ShellActions {
    val NEW = ActionId("new")
    val OPEN = ActionId("open")
    val SAVE = ActionId("save")
    val SAVE_AS = ActionId("save-as")
    val CLOSE = ActionId("close")
    val UNDO = ActionId("undo")
    val REDO = ActionId("redo")
    val COMMANDS = ActionId("commands")
    val PARTS = ActionId("drawer-parts")
    val TYPES = ActionId("drawer-types")
    val HISTORY = ActionId("drawer-history")
    val INSPECTOR = ActionId("drawer-inspector")
    val STAGE_SHAPE = ActionId("stage-shape")
    val STAGE_COLOR = ActionId("stage-color")
    val STAGE_LINES = ActionId("stage-lines")
    val STAGE_FACE = ActionId("stage-face")
    val HELP = ActionId("drawer-help")
    val RESET_LAYOUT = ActionId("reset-layout")
    val REGION_BORDER = ActionId(BuildTool.REGION_BORDER.actionId)
    val FILL = ActionId(BuildTool.FILL.actionId)
    val BRUSH = ActionId(BuildTool.BRUSH.actionId)
    val PICK_COLOR = ActionId(BuildTool.PICK_COLOR.actionId)
    val DRAW = ActionId(BuildTool.DRAW_OUTLINE.actionId)
    val SPINE_WIDTH = ActionId(BuildTool.SPINE_WIDTH.actionId)
    val EDIT_CURVES = ActionId(BuildTool.EDIT_CURVES.actionId)
    val ERASE = ActionId(BuildTool.ERASE.actionId)
    val SMOOTH = ActionId(BuildTool.SMOOTH.actionId)
    val MOVE = ActionId(BuildTool.MOVE.actionId)
    val LINE_WIDTH = ActionId(BuildTool.LINE_WIDTH.actionId)
    val SURFACE_STROKE = ActionId(BuildTool.SURFACE_STROKE.actionId)
    val FLOATING_STROKE = ActionId(BuildTool.FLOATING_STROKE.actionId)
    val FLOATING_MARK = ActionId(BuildTool.FLOATING_MARK.actionId)
    val SURFACE_ERASE = ActionId(BuildTool.SURFACE_ERASE.actionId)
    val FUR_STROKE = ActionId(BuildTool.FUR_STROKE.actionId)
    val COMB = ActionId(BuildTool.COMB.actionId)
    val DRAW_MARK = ActionId(BuildTool.DRAW_MARK.actionId)
    val EDIT_MARK = ActionId(BuildTool.EDIT_MARK.actionId)
    val ERASE_MARK = ActionId(BuildTool.ERASE_MARK.actionId)
    val GAZE_TARGET = ActionId(BuildTool.GAZE_TARGET.actionId)
    val FIT_PANE = ActionId("fit-pane")
    val PUT_DOWN = ActionId("put-down")
    val SWAP_PANE = ActionId("swap-pane")
    val PLACE_IMAGE = ActionId(BuildTool.PLACE_IMAGE.actionId)
    val ROTATE_IMAGE = ActionId(BuildTool.ROTATE_IMAGE.actionId)
    val MIRROR = ActionId("mirror")
    val START_TEMPLATE = ActionId("start-template")
    val HALF_OUTLINE = ActionId("half-outline")
    val SELECT = ActionId("tool-select")
    val ADD_PART = ActionId("tool-add-part")
}

private val texts: Map<ActionId, StringResource> = mapOf(
    ShellActions.NEW to Res.string.shell_action_new,
    ShellActions.OPEN to Res.string.shell_action_open,
    ShellActions.SAVE to Res.string.shell_action_save,
    ShellActions.SAVE_AS to Res.string.shell_action_save_as,
    ShellActions.CLOSE to Res.string.shell_action_close,
    ShellActions.UNDO to Res.string.shell_action_undo,
    ShellActions.REDO to Res.string.shell_action_redo,
    ShellActions.COMMANDS to Res.string.shell_action_commands,
    ShellActions.PARTS to Res.string.shell_drawer_parts,
    ShellActions.TYPES to Res.string.shell_drawer_types,
    ShellActions.HISTORY to Res.string.shell_drawer_history,
    ShellActions.INSPECTOR to Res.string.shell_drawer_inspector,
    ShellActions.STAGE_SHAPE to Res.string.stage_shape,
    ShellActions.STAGE_COLOR to Res.string.shell_drawer_color,
    ShellActions.STAGE_LINES to Res.string.shell_drawer_lines,
    ShellActions.STAGE_FACE to Res.string.stage_face,
    ShellActions.HELP to Res.string.shell_drawer_help,
    ShellActions.RESET_LAYOUT to Res.string.shell_action_reset_layout,
    ShellActions.DRAW to Res.string.shell_action_draw,
    ShellActions.SPINE_WIDTH to Res.string.spine_tool,
    ShellActions.EDIT_CURVES to Res.string.shell_action_edit_curves,
    ShellActions.ERASE to Res.string.edit_tool_erase,
    ShellActions.SMOOTH to Res.string.smooth_tool,
    ShellActions.MOVE to Res.string.move_tool,
    ShellActions.REGION_BORDER to Res.string.color_tool_region_border,
    ShellActions.FILL to Res.string.color_tool_fill,
    ShellActions.BRUSH to Res.string.color_tool_brush,
    ShellActions.PICK_COLOR to Res.string.color_tool_pick,
    ShellActions.LINE_WIDTH to Res.string.lines_tool_width,
    ShellActions.SURFACE_STROKE to Res.string.lines_tool_surface_stroke,
    ShellActions.FLOATING_STROKE to Res.string.lines_tool_floating_stroke,
    ShellActions.FLOATING_MARK to Res.string.lines_tool_floating_stroke,
    ShellActions.SURFACE_ERASE to Res.string.edit_tool_erase,
    ShellActions.FUR_STROKE to Res.string.fur_tool,
    ShellActions.COMB to Res.string.fur_comb_tool,
    ShellActions.DRAW_MARK to Res.string.face_tool_draw,
    ShellActions.EDIT_MARK to Res.string.shell_action_edit_curves,
    ShellActions.ERASE_MARK to Res.string.edit_tool_erase,
    ShellActions.GAZE_TARGET to Res.string.face_tool_gaze,
    ShellActions.FIT_PANE to Res.string.sheet_pane_fit,
    ShellActions.PUT_DOWN to Res.string.shell_action_put_down,
    ShellActions.SWAP_PANE to Res.string.sheet_pane_swap,
    ShellActions.PLACE_IMAGE to Res.string.place_tool,
    ShellActions.ROTATE_IMAGE to Res.string.rotate_tool,
    ShellActions.MIRROR to Res.string.edit_tool_mirror,
    ShellActions.START_TEMPLATE to Res.string.shell_action_start_template,
    ShellActions.HALF_OUTLINE to Res.string.draw_half_outline,
    ShellActions.SELECT to Res.string.pick_tool_select,
    ShellActions.ADD_PART to Res.string.pick_tool_add,
)

/** The labels for [dev.chieppa.stretch.frame.LocalActionText]. */
@Composable
fun actionText(): (String) -> String {
    val labels = texts.entries.associate { (id, resource) -> id.value to stringResource(resource) }
    return { labels[it] ?: it }
}

/** The icon for an action's icon key. */
fun actionIcon(key: String): ImageVector? = when (key) {
    "file" -> StretchIcons.File
    "folder" -> StretchIcons.Folder
    "save" -> StretchIcons.Save
    "undo" -> StretchIcons.Undo
    "redo" -> StretchIcons.Redo
    "command" -> StretchIcons.Command
    "parts" -> SquashIcons.Parts
    "types" -> SquashIcons.PartTypes
    "history" -> StretchIcons.Clock
    "part" -> SquashIcons.Part
    "help" -> StretchIcons.Help
    "pen" -> SquashIcons.DrawOutline
    "spine-and-width" -> SquashIcons.SpineAndWidth
    "edit-curves" -> SquashIcons.EditCurves
    "erase" -> SquashIcons.Erase
    "smooth" -> SquashIcons.SmoothCurve
    "move" -> SquashIcons.Move
    "region-border" -> SquashIcons.RegionBorder
    "fill" -> SquashIcons.Fill
    "brush" -> SquashIcons.Brush
    "pick-color" -> SquashIcons.PickColor
    "line-width" -> SquashIcons.LineWidth
    "surface-stroke" -> SquashIcons.SurfaceStroke
    "fur-stroke" -> SquashIcons.FurStroke
    "comb" -> SquashIcons.Comb
    "draw-mark" -> SquashIcons.DrawMark
    "gaze-target" -> SquashIcons.GazeTarget
    "mirror" -> SquashIcons.Mirror
    "start-template" -> SquashIcons.StartFromTemplate
    "half-outline" -> SquashIcons.HalfOutline
    "select" -> SquashIcons.Select
    "add-part" -> SquashIcons.AddPart
    else -> null
}

private fun chord(keys: String) = listOf(KeyChord.parse(keys))

/** The key mode of a stage, which scopes its tools' keys so a key means one thing in each stage. */
val Stage.keyMode: String get() = name.lowercase()

/** The key mode of Pick a part type. */
const val PICK_KEY_MODE = "pick"

/**
 * Registers everything a key, a menu or the palette can do. A stage's tools are scoped to it, and where a stage's tool and an action of every
 * screen share a key the stage's tool answers; everything else answers on every screen it is available on.
 */
fun Shell.registerActions() {
    val inFrame = { state.screen.value != Screen.HOME }
    val hasCharacter = { state.session.value != null }
    val inWorkspace = { state.screen.value == Screen.WORKSPACE }
    val onPick = { state.screen.value == Screen.PICK_PART }
    val placing = { inWorkspace() && state.tool.value.places }
    val selectedIsPaired = {
        inWorkspace() && state.session.value?.document?.value?.character?.let { character ->
            state.selectedPart.value?.let(character::part)?.pair
        } != null
    }
    fun action(id: ActionId, icon: String?, keys: List<KeyChord> = emptyList(), available: () -> Boolean = { true }, mode: String? = null, run: () -> Unit) =
        registry.register(Action(id, id.value, icon, keys, mode, available, run))
    fun tool(tool: BuildTool, icon: String, key: String, available: () -> Boolean = inWorkspace) =
        action(ActionId(tool.actionId), icon, chord(key), available, tool.stage?.keyMode) { state.armTool(tool) }
    fun stage(id: ActionId, key: String, stage: Stage) = action(id, null, chord(key), inWorkspace) { state.showStage(stage) }
    fun toggle(drawer: String) {
        layout = layout.toggle(drawer)
    }

    action(ShellActions.NEW, "file", chord("Ctrl N")) { newCharacter() }
    action(ShellActions.OPEN, "folder", chord("Ctrl O")) { chooseAndOpen() }
    action(ShellActions.SAVE, "save", chord("Ctrl S"), hasCharacter) { save() }
    action(ShellActions.SAVE_AS, null, chord("Ctrl Shift S"), hasCharacter) { save(asNew = true) }
    action(ShellActions.CLOSE, null, available = hasCharacter) { close() }
    action(ShellActions.UNDO, "undo", chord("Ctrl Z"), { state.session.value?.history?.canUndo == true }) { state.undo() }
    action(ShellActions.REDO, "redo", chord("Ctrl Shift Z"), { state.session.value?.history?.canRedo == true }) { state.redo() }
    action(ShellActions.COMMANDS, "command", chord("Ctrl K")) { palette = true }
    action(ShellActions.PARTS, "parts", available = inFrame) { toggle(DrawerIds.PARTS) }
    action(ShellActions.TYPES, "types", chord("T"), hasCharacter) {
        if (state.screen.value == Screen.PICK_PART) state.screen.value = Screen.WORKSPACE else state.openPartTypes()
    }
    action(ShellActions.HISTORY, "history", available = hasCharacter) { toggle(DrawerIds.HISTORY) }
    action(ShellActions.INSPECTOR, "part", chord("Tab"), inFrame) { toggle(DrawerIds.INSPECTOR) }
    action(ShellActions.HELP, "help", available = inFrame) { toggle(DrawerIds.HELP) }
    action(ShellActions.RESET_LAYOUT, null, available = inFrame) { layout = DEFAULT_LAYOUT }
    stage(ShellActions.STAGE_SHAPE, "1", Stage.SHAPE)
    stage(ShellActions.STAGE_COLOR, "2", Stage.COLOR)
    stage(ShellActions.STAGE_LINES, "3", Stage.LINES)
    stage(ShellActions.STAGE_FACE, "4", Stage.FACE)
    action(ShellActions.SWAP_PANE, null, chord("Space"), { inWorkspace() && state.layout.value == SheetLayout.FOCUS }) { state.swapPane() }
    action(ShellActions.FIT_PANE, null, chord("F"), inWorkspace) { state.fitUnderPointer() }
    action(ShellActions.PUT_DOWN, null, chord("Esc"), { inWorkspace() && state.tool.value != BuildTool.NONE }) { state.putToolDown() }
    action(ShellActions.MIRROR, "mirror", chord("M"), selectedIsPaired) { state.mirror.value = !state.mirror.value }

    tool(BuildTool.DRAW_OUTLINE, "pen", "D")
    tool(BuildTool.SPINE_WIDTH, "spine-and-width", "W")
    tool(BuildTool.EDIT_CURVES, "edit-curves", "E")
    tool(BuildTool.ERASE, "erase", "X")
    tool(BuildTool.SMOOTH, "smooth", "S")
    tool(BuildTool.MOVE, "move", "V")
    tool(BuildTool.PLACE_IMAGE, "place-image", "G", placing)
    tool(BuildTool.ROTATE_IMAGE, "rotate-image", "R", placing)
    action(ShellActions.START_TEMPLATE, "start-template", available = { inWorkspace() && state.templateStartView() != null }, mode = Stage.SHAPE.keyMode) { state.startFromTemplate() }
    action(ShellActions.HALF_OUTLINE, "half-outline", chord("H"), inWorkspace, Stage.SHAPE.keyMode) { state.curves.halfOutline = !state.curves.halfOutline }

    tool(BuildTool.FILL, "fill", "G")
    tool(BuildTool.BRUSH, "brush", "B")
    tool(BuildTool.REGION_BORDER, "region-border", "R")
    tool(BuildTool.PICK_COLOR, "pick-color", "I")

    tool(BuildTool.LINE_WIDTH, "line-width", "W")
    tool(BuildTool.SURFACE_STROKE, "surface-stroke", "D")
    tool(BuildTool.FLOATING_STROKE, "floating-stroke", "L")
    tool(BuildTool.FUR_STROKE, "fur-stroke", "U")
    tool(BuildTool.COMB, "comb", "C")
    tool(BuildTool.SURFACE_ERASE, "erase", "X")

    tool(BuildTool.DRAW_MARK, "draw-mark", "D")
    tool(BuildTool.FLOATING_MARK, "floating-stroke", "L")
    tool(BuildTool.EDIT_MARK, "edit-curves", "E")
    tool(BuildTool.ERASE_MARK, "erase", "X")
    tool(BuildTool.GAZE_TARGET, "gaze-target", "G")

    action(ShellActions.SELECT, "select", chord("V"), onPick, PICK_KEY_MODE) { state.pickTool.value = PickTool.SELECT }
    action(ShellActions.ADD_PART, "add-part", chord("A"), onPick, PICK_KEY_MODE) { state.pickTool.value = PickTool.ADD_PART }
}
