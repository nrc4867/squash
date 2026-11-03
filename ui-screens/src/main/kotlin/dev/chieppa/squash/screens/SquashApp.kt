package dev.chieppa.squash.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.shell.HomeScreen
import dev.chieppa.squash.screens.shell.NewCharacterScreen
import dev.chieppa.squash.screens.shell.PickPartScreen
import dev.chieppa.squash.screens.shell.Shell
import dev.chieppa.squash.screens.shell.ShellActions
import dev.chieppa.squash.screens.shell.WorkspaceScreen
import dev.chieppa.squash.screens.shell.actionIcon
import dev.chieppa.squash.screens.shell.actionText
import dev.chieppa.squash.screens.shell.registerActions
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_save_failed
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.document.UnsavedChangesDialog
import dev.chieppa.stretch.document.rememberFileChooser
import dev.chieppa.stretch.frame.CommandPalette
import dev.chieppa.stretch.frame.LocalActionText
import dev.chieppa.stretch.frame.ToastHost
import dev.chieppa.stretch.frame.ToastState
import dev.chieppa.stretch.frame.actionKeys
import dev.chieppa.stretch.ui.focusHome
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

private val TOAST_LIFT = 40.dp

/** The window's content: the current screen over [state], with what every screen shares. */
@Composable
fun SquashApp(state: AppState, chooser: FileChooser = rememberFileChooser(null)) {
    val scope = rememberCoroutineScope()
    val shell = remember(state, chooser) { Shell(state, chooser, ToastState(), scope).apply { registerActions() } }
    val screen by state.screen.collectAsState()
    val file by state.file.collectAsState()
    var previous by remember { mutableStateOf<Screen?>(null) }
    LaunchedEffect(screen) {
        shell.onScreen(screen, previous)
        previous = screen
    }
    CompositionLocalProvider(LocalActionText provides actionText()) {
        Box(Modifier.fillMaxSize().focusHome().actionKeys(shell.keymap, null)) {
            when (screen) {
                Screen.HOME -> HomeScreen(shell)
                Screen.NEW_CHARACTER -> NewCharacterScreen(shell)
                Screen.PICK_PART -> PickPartScreen(shell)
                Screen.WORKSPACE -> WorkspaceScreen(shell)
            }
            ToastHost(shell.toasts, Modifier.align(Alignment.BottomCenter).padding(bottom = TOAST_LIFT))
        }
        CommandPalette(
            shell.palette,
            shell.registry.available(null).filter { it.id != ShellActions.COMMANDS },
            shell.keymap,
            { shell.palette = false },
            ::actionIcon,
        )
        file?.let { open ->
            UnsavedChangesDialog(open, chooser) { failure ->
                scope.launch { shell.toasts.show(getString(Res.string.shell_save_failed, failure.message.orEmpty())) }
            }
        }
    }
}
