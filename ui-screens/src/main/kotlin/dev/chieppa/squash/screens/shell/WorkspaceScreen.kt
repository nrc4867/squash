package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.screens.draw.rememberDrawOutlineTool
import dev.chieppa.squash.screens.sheet.SheetPanes

/** The Build workspace: the frame's bars and drawers around the panes and their tool strip. */
@Composable
fun WorkspaceScreen(shell: Shell) {
    val state = shell.state
    val armed by state.tool.collectAsState()
    val draw = rememberDrawOutlineTool(state)
    BuildFrame(shell, Modifier.testTag(ShellTags.WORKSPACE)) {
        SheetPanes(
            state,
            Modifier.fillMaxSize(),
            tools = { WorkspaceTools(state) },
            tool = draw.takeIf { armed == BuildTool.DRAW_OUTLINE },
        )
    }
}
