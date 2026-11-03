package dev.chieppa.squash.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.chieppa.squash.screens.shell.HomeScreen
import dev.chieppa.squash.screens.shell.NewCharacterScreen
import dev.chieppa.squash.screens.shell.PickPartScreen
import dev.chieppa.squash.screens.shell.WorkspaceScreen

/** The window's content: the current screen over [state]. */
@Composable
fun SquashApp(state: AppState) {
    val screen by state.screen.collectAsState()
    when (screen) {
        Screen.HOME -> HomeScreen(state)
        Screen.NEW_CHARACTER -> NewCharacterScreen(state)
        Screen.PICK_PART -> PickPartScreen(state)
        Screen.WORKSPACE -> WorkspaceScreen(state)
    }
}
