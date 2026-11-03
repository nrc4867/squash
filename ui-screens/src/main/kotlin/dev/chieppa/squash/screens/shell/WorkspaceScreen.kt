package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SheetPanes

/** The Build workspace: the frame's bars and drawers around the panes. */
@Composable
fun WorkspaceScreen(state: AppState) {
    SheetPanes(state, Modifier.fillMaxSize())
}
