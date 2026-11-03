package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
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
import dev.chieppa.squash.uidesign.generated.shell_action_undo
import dev.chieppa.squash.uidesign.generated.shell_drawer_help
import dev.chieppa.squash.uidesign.generated.shell_drawer_history
import dev.chieppa.squash.uidesign.generated.shell_drawer_part
import dev.chieppa.squash.uidesign.generated.shell_drawer_parts
import dev.chieppa.squash.uidesign.generated.shell_drawer_types
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
    val DRAW = ActionId("tool-draw")
    val EDIT_CURVES = ActionId("tool-edit")
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
    ShellActions.EDIT_CURVES to Res.string.shell_action_edit_curves,
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
    "edit-curves" -> SquashIcons.EditCurves
    else -> null
}

private fun chord(keys: String) = listOf(KeyChord.parse(keys))

/** Registers everything a key, a menu or the palette can do. */
fun Shell.registerActions() {
    val inFrame = { state.screen.value != Screen.HOME }
    val hasCharacter = { state.session.value != null }
    val inWorkspace = { state.screen.value == Screen.WORKSPACE }
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
    action(ShellActions.UNDO, "undo", chord("Ctrl Z"), hasCharacter) { state.undo() }
    action(ShellActions.REDO, "redo", chord("Ctrl Shift Z"), hasCharacter) { state.redo() }
    action(ShellActions.COMMANDS, "command", chord("Ctrl K")) { palette = true }
    action(ShellActions.PARTS, "parts", chord("B"), inFrame) { toggle(DrawerIds.PARTS) }
    action(ShellActions.TYPES, "types", chord("T"), hasCharacter) {
        if (state.screen.value == Screen.PICK_PART) state.screen.value = Screen.WORKSPACE else state.openPartTypes()
    }
    action(ShellActions.HISTORY, "history", available = hasCharacter) { toggle(DrawerIds.HISTORY) }
    action(ShellActions.PART, "part", available = inFrame) { toggle(DrawerIds.PART) }
    action(ShellActions.HELP, "help", available = inFrame) { toggle(DrawerIds.HELP) }
    action(ShellActions.RESET_LAYOUT, null, available = inFrame) { layout = DEFAULT_LAYOUT }
    action(ShellActions.DRAW, "pen", chord("D"), inWorkspace) { state.tool.value = BuildTool.DRAW_OUTLINE }
    action(ShellActions.EDIT_CURVES, "edit-curves", chord("E"), inWorkspace) { state.tool.value = BuildTool.EDIT_CURVES }
}
