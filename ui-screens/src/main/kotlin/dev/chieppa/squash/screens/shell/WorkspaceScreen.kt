package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.screens.sheet.SheetPanes

/** The Build workspace: the frame's bars and drawers around the panes and their tool strip. */
@Composable
fun WorkspaceScreen(shell: Shell) {
    BuildFrame(shell, Modifier.testTag(ShellTags.WORKSPACE)) {
        SheetPanes(shell.state, Modifier.fillMaxSize(), tools = { WorkspaceTools(shell.state) })
    }
}
