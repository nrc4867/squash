package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        menus = { BarMenus(shellMenus(shell, home)) },
        modes = {
            if (!home) {
                val build = stringResource(Res.string.shell_mode_build)
                val shot = stringResource(Res.string.shell_mode_shot)
                Segmented(listOf(build, shot), build, {}, { it }, mode = true, enabled = { it == build })
            }
        },
        actions = {
            val history = session?.history
            TopBarActions(
                onUndo = history?.takeUnless { home }?.let { { it.undo() } },
                onRedo = history?.takeUnless { home }?.let { { it.redo() } },
                onCommands = { shell.palette = true },
            )
        },
    )
}

/** The menus with something to do on this screen; what has no action here is left out. */
@Composable
private fun shellMenus(shell: Shell, home: Boolean): List<BarMenu> {
    val hasCharacter = shell.state.session.collectAsState().value != null
    val file = BarMenu(stringResource(Res.string.shell_menu_file)) { close ->
        Item(shell, ShellActions.NEW, close)
        Item(shell, ShellActions.OPEN, close)
        if (hasCharacter) {
            Item(shell, ShellActions.SAVE, close)
            Item(shell, ShellActions.SAVE_AS, close)
            Item(shell, ShellActions.CLOSE, close)
        }
        MenuDivider()
        RecentFilesMenuItems(shell.state.recent, FileStorage, shell::open, close)
    }
    if (home) return listOf(file)
    val edit = BarMenu(stringResource(Res.string.shell_menu_edit)) { close ->
        Item(shell, ShellActions.UNDO, close)
        Item(shell, ShellActions.REDO, close)
    }
    val view = BarMenu(stringResource(Res.string.shell_menu_view)) { close ->
        Item(shell, ShellActions.PARTS, close)
        if (hasCharacter) {
            Item(shell, ShellActions.TYPES, close)
            Item(shell, ShellActions.HISTORY, close)
        }
        Item(shell, ShellActions.PART, close)
        MenuDivider()
        Item(shell, ShellActions.RESET_LAYOUT, close)
    }
    val help = BarMenu(stringResource(Res.string.shell_menu_help)) { close -> Item(shell, ShellActions.HELP, close) }
    return if (hasCharacter) listOf(file, edit, view, help) else listOf(file, view, help)
}

@Composable
private fun Item(shell: Shell, id: ActionId, close: () -> Unit) {
    val action = shell.registry[id] ?: return
    ActionMenuItem(action, shell.keymap, ::actionIcon, close)
}
