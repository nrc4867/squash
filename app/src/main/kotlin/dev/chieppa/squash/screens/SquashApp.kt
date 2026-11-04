package dev.chieppa.squash.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.draw.ToolDrawerSections
import dev.chieppa.squash.screens.shell.BuildContent
import dev.chieppa.squash.screens.shell.BuildScreens
import dev.chieppa.squash.screens.shell.HomeScreen
import dev.chieppa.squash.screens.shell.Shell
import dev.chieppa.squash.screens.shell.ShellActions
import dev.chieppa.squash.screens.shell.WorkspaceScreen
import dev.chieppa.squash.screens.shell.actionIcon
import dev.chieppa.squash.screens.surface.LinesDrawer
import dev.chieppa.squash.screens.surface.MarksDrawer
import dev.chieppa.squash.screens.shell.actionText
import dev.chieppa.squash.screens.shell.registerActions
import dev.chieppa.squash.screens.surface.paint.ColorDrawerContent
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

private val BUILD_CONTENT = BuildContent(
    workspace = { shell -> WorkspaceScreen(shell) },
    toolSections = { kind, state, session, part, outlines -> ToolDrawerSections(kind, state, session, part, outlines) },
    colorDrawer = { state, session -> ColorDrawerContent(state, session) },
    linesDrawer = { state -> LinesDrawer(state) },
    marksDrawer = { state, onOpenLines -> MarksDrawer(state, onOpenLines) },
)

/** The window's content: the current screen over [state], with what every screen shares. */
@Composable
fun SquashApp(state: AppState, chooser: FileChooser = rememberFileChooser(null)) {
    val scope = rememberCoroutineScope()
    val shell = remember(state, chooser) { Shell(state, chooser, ToastState(), scope, BUILD_CONTENT).apply { registerActions() } }
    val screen by state.screen.collectAsState()
    val file by state.file.collectAsState()
    remember(screen) { shell.onScreen(screen) }
    CompositionLocalProvider(LocalActionText provides actionText()) {
        Box(Modifier.fillMaxSize().focusHome().actionKeys(shell.keymap, null)) {
            if (screen == Screen.HOME) HomeScreen(shell) else BuildScreens(shell, screen)
            if (screen != Screen.WORKSPACE) ToastHost(shell.toasts, Modifier.align(Alignment.BottomCenter).padding(bottom = TOAST_LIFT))
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
