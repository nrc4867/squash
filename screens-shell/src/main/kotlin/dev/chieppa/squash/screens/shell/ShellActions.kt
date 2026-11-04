package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.uidesign.generated.sheet_pane_fit
import dev.chieppa.squash.uidesign.generated.move_tool
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.chieppa.squash.uidesign.generated.shell_action_save
import dev.chieppa.squash.uidesign.generated.shell_action_save_as
import dev.chieppa.squash.uidesign.generated.shell_action_start_template
import dev.chieppa.squash.uidesign.generated.shell_action_undo
import dev.chieppa.squash.uidesign.generated.shell_drawer_help
import dev.chieppa.squash.uidesign.generated.shell_drawer_history
import dev.chieppa.squash.uidesign.generated.shell_drawer_part
import dev.chieppa.squash.uidesign.generated.shell_drawer_parts
import dev.chieppa.squash.uidesign.generated.shell_drawer_types
import dev.chieppa.squash.uidesign.generated.lines_tool_surface_stroke
import dev.chieppa.squash.uidesign.generated.lines_tool_width
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
    val PART = ActionId("drawer-part")
    val HELP = ActionId("drawer-help")
    val RESET_LAYOUT = ActionId("reset-layout")
    val REGION_BORDER = ActionId("tool-region-border")
    val FILL = ActionId("tool-fill")
    val BRUSH = ActionId("tool-brush")
    val PICK_COLOR = ActionId("tool-pick-color")
    val DRAW = ActionId("tool-draw")
    val SPINE_WIDTH = ActionId("tool-spine-width")
    val EDIT_CURVES = ActionId("tool-edit")
    val ERASE = ActionId("tool-erase")
    val SMOOTH = ActionId("tool-smooth")
    val MOVE = ActionId("tool-move")
    val LINE_WIDTH = ActionId("tool-line-width")
    val SURFACE_STROKE = ActionId("tool-surface-stroke")
    val SURFACE_ERASE = ActionId("tool-surface-erase")
    val FIT_PANE = ActionId("fit-pane")
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
    ShellActions.PART to Res.string.shell_drawer_part,
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
    ShellActions.SURFACE_ERASE to Res.string.edit_tool_erase,
    ShellActions.FIT_PANE to Res.string.sheet_pane_fit,
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
    "mirror" -> SquashIcons.Mirror
    "start-template" -> SquashIcons.StartFromTemplate
    "half-outline" -> SquashIcons.HalfOutline
    "select" -> SquashIcons.Select
    "add-part" -> SquashIcons.AddPart
    else -> null
}

private fun chord(keys: String) = listOf(KeyChord.parse(keys))

/** Registers everything a key, a menu or the palette can do. */
fun Shell.registerActions() {
    val inFrame = { state.screen.value != Screen.HOME }
    val hasCharacter = { state.session.value != null }
    val inWorkspace = { state.screen.value == Screen.WORKSPACE }
    val onPick = { state.screen.value == Screen.PICK_PART }
    val inking = { inWorkspace() && state.tool.value.inks }
    val onOutlines = { inWorkspace() && !inking() }
    val selectedIsPaired = {
        inWorkspace() && state.session.value?.document?.value?.character?.let { character ->
            state.selectedPart.value?.let(character::part)?.pair
        } != null
    }
    fun action(id: ActionId, icon: String?, keys: List<KeyChord> = emptyList(), available: () -> Boolean = { true }, run: () -> Unit) =
        registry.register(Action(id, id.value, icon, keys, isAvailable = available, run = run))
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
    val painting = { inWorkspace() && state.tool.value.paints }
    action(ShellActions.PARTS, "parts", chord("B"), { inFrame() && !painting() }) { toggle(DrawerIds.PARTS) }
    action(ShellActions.TYPES, "types", chord("T"), hasCharacter) {
        if (state.screen.value == Screen.PICK_PART) state.screen.value = Screen.WORKSPACE else state.openPartTypes()
    }
    action(ShellActions.HISTORY, "history", available = hasCharacter) { toggle(DrawerIds.HISTORY) }
    action(ShellActions.PART, "part", available = inFrame) { toggle(DrawerIds.PART) }
    action(ShellActions.HELP, "help", available = inFrame) { toggle(DrawerIds.HELP) }
    action(ShellActions.RESET_LAYOUT, null, available = inFrame) { layout = DEFAULT_LAYOUT }
    action(ShellActions.DRAW, "pen", chord("D"), onOutlines) { state.armTool(BuildTool.DRAW_OUTLINE) }
    action(ShellActions.SPINE_WIDTH, "spine-and-width", chord("W"), onOutlines) { state.armTool(BuildTool.SPINE_WIDTH) }
    action(ShellActions.EDIT_CURVES, "edit-curves", chord("E"), onOutlines) { state.armTool(BuildTool.EDIT_CURVES) }
    action(ShellActions.ERASE, "erase", chord("X"), onOutlines) { state.armTool(BuildTool.ERASE) }
    action(ShellActions.SMOOTH, "smooth", chord("S"), onOutlines) { state.armTool(BuildTool.SMOOTH) }
    action(ShellActions.MOVE, "move", chord("V"), onOutlines) { state.armTool(BuildTool.MOVE) }
    action(ShellActions.FIT_PANE, null, chord("F"), { inWorkspace() && !painting() }) { state.paneNavigation.fit(state.activeView.value) }
    action(ShellActions.REGION_BORDER, "region-border", chord("R"), inWorkspace) { state.armTool(BuildTool.REGION_BORDER) }
    action(ShellActions.PICK_COLOR, "pick-color", chord("I"), inWorkspace) { state.armTool(BuildTool.PICK_COLOR) }
    action(ShellActions.FILL, "fill", chord("F"), painting) { state.armTool(BuildTool.FILL) }
    action(ShellActions.BRUSH, "brush", chord("B"), painting) { state.armTool(BuildTool.BRUSH) }
    action(ShellActions.LINE_WIDTH, "line-width", chord("W"), inking) { state.armTool(BuildTool.LINE_WIDTH) }
    action(ShellActions.SURFACE_STROKE, "surface-stroke", chord("D"), inking) { state.armTool(BuildTool.SURFACE_STROKE) }
    action(ShellActions.SURFACE_ERASE, "erase", chord("X"), inking) { state.armTool(BuildTool.SURFACE_ERASE) }
    action(ShellActions.MIRROR, "mirror", chord("M"), selectedIsPaired) { state.mirror.value = !state.mirror.value }
    action(ShellActions.START_TEMPLATE, "start-template", available = { inWorkspace() && state.templateStartView() != null }) { state.startFromTemplate() }
    action(ShellActions.HALF_OUTLINE, "half-outline", chord("H"), inWorkspace) { state.curves.halfOutline = !state.curves.halfOutline }
    action(ShellActions.SELECT, "select", chord("V"), onPick) { state.pickTool.value = PickTool.SELECT }
    action(ShellActions.ADD_PART, "add-part", chord("A"), onPick) { state.pickTool.value = PickTool.ADD_PART }
}
