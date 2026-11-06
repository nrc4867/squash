package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.app_name
import dev.chieppa.squash.uidesign.generated.shell_draft
import dev.chieppa.squash.uidesign.generated.shell_menu_edit
import dev.chieppa.squash.uidesign.generated.shell_menu_file
import dev.chieppa.squash.uidesign.generated.shell_menu_help
import dev.chieppa.squash.uidesign.generated.shell_menu_view
import dev.chieppa.squash.uidesign.generated.shell_mode_build
import dev.chieppa.squash.uidesign.generated.shell_mode_shot
import dev.chieppa.squash.uidesign.generated.shell_no_character
import dev.chieppa.stretch.core.ActionId
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.document.RecentFilesMenuItems
import dev.chieppa.stretch.document.rememberEdited
import dev.chieppa.stretch.frame.ActionMenuItem
import dev.chieppa.stretch.frame.BarMenu
import dev.chieppa.stretch.frame.BarMenus
import dev.chieppa.stretch.frame.MenuDivider
import dev.chieppa.stretch.frame.TopBarActions
import dev.chieppa.stretch.frame.WindowTopBar
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import dev.chieppa.squash.uidesign.generated.stage_dress
import dev.chieppa.squash.uidesign.generated.shell_drawer_rig
import dev.chieppa.squash.screens.AppState
import androidx.compose.runtime.getValue

/** The window's top bar on every screen: menus, the open character, the mode and the bar's actions. */
@Composable
fun ShellTopBar(shell: Shell) {
    val state = shell.state
    val screen by state.screen.collectAsState()
    val session by state.session.collectAsState()
    val file by state.file.collectAsState()
    val home = screen == Screen.HOME
    val name = session?.document?.collectAsState()?.value?.character?.name
    val document = when {
        home -> ""
        name != null -> stringResource(Res.string.shell_draft, name)
        else -> stringResource(Res.string.shell_no_character)
    }
    val edited = file?.let { rememberEdited(it) } == true
    WindowTopBar(
        appName = stringResource(Res.string.app_name),
        document = document,
        edited = !home && edited,
        menus = { BarMenus(shellMenus(shell)) },
        modes = {
            if (!home) {
                val build = stringResource(Res.string.shell_mode_build)
                val shot = stringResource(Res.string.shell_mode_shot)
                Segmented(listOf(build, shot), build, {}, { it }, mode = true, enabled = { it == build })
                if (screen == Screen.WORKSPACE) StageSwitcher(state)
            }
        },
        actions = {
            val steps = session?.history?.steps?.collectAsState()?.value
            TopBarActions(
                onUndo = if (home) null else ({ state.undo() }),
                onRedo = if (home) null else ({ state.redo() }),
                onCommands = { shell.palette = true },
                onSettings = if (home) ({ shell.settings = true }) else null,
                undoEnabled = steps?.done?.isNotEmpty() == true,
                redoEnabled = steps?.undone?.isNotEmpty() == true,
                settingsPopup = { SettingsPopover(shell) },
            )
        },
    )
}

/** The stages of Build, the one on screen lit, with the stages not built yet faint after them. */
@Composable
private fun StageSwitcher(state: AppState) {
    val stage by state.stage.collectAsState()
    val built = Stage.entries.associateBy { stringResource(it.label) }
    val later = listOf(stringResource(Res.string.shell_drawer_rig), stringResource(Res.string.stage_dress))
    Segmented(
        built.keys.toList() + later,
        stringResource(stage.label),
        { name -> built[name]?.let(state::showStage) },
        { it },
        Modifier.testTag(ShellTags.STAGES),
        enabled = { it in built },
    )
}

/** The menus the boards draw on every screen. What cannot act here stays in its place, faint. */
@Composable
private fun shellMenus(shell: Shell): List<BarMenu> = listOf(
    BarMenu(stringResource(Res.string.shell_menu_file)) { close ->
        Item(shell, ShellActions.NEW, close)
        Item(shell, ShellActions.OPEN, close)
        Item(shell, ShellActions.SAVE, close)
        Item(shell, ShellActions.SAVE_AS, close)
        Item(shell, ShellActions.CLOSE, close)
        MenuDivider()
        RecentFilesMenuItems(shell.state.recent, FileStorage, shell::open, close)
    },
    BarMenu(stringResource(Res.string.shell_menu_edit)) { close ->
        Item(shell, ShellActions.UNDO, close)
        Item(shell, ShellActions.REDO, close)
        MenuDivider()
        Item(shell, ShellActions.DRAW, close)
        Item(shell, ShellActions.SPINE_WIDTH, close)
        Item(shell, ShellActions.EDIT_CURVES, close)
        Item(shell, ShellActions.ERASE, close)
        Item(shell, ShellActions.SMOOTH, close)
        Item(shell, ShellActions.ADD_PART, close)
        MenuDivider()
        Item(shell, ShellActions.MIRROR, close)
    },
    BarMenu(stringResource(Res.string.shell_menu_view)) { close ->
        Item(shell, ShellActions.PARTS, close)
        Item(shell, ShellActions.HISTORY, close)
        Item(shell, ShellActions.INSPECTOR, close)
        MenuDivider()
        Item(shell, ShellActions.RESET_LAYOUT, close)
    },
    BarMenu(stringResource(Res.string.shell_menu_help)) { close -> Item(shell, ShellActions.HELP, close) },
)

@Composable
private fun Item(shell: Shell, id: ActionId, close: () -> Unit) {
    val action = shell.registry[id] ?: return
    ActionMenuItem(action, shell.keymap, ::actionIcon, close)
}
