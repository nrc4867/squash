package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.screens.draw.rememberDrawOutlineTool
import dev.chieppa.squash.screens.edit.Refit
import dev.chieppa.squash.screens.edit.RefitReach
import dev.chieppa.squash.screens.edit.rememberEditCurvesTool
import dev.chieppa.squash.screens.edit.rememberEraseTool
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_toast_refit
import dev.chieppa.squash.uidesign.generated.edit_toast_refit_ends
import dev.chieppa.squash.uidesign.generated.edit_toast_refit_whole
import dev.chieppa.squash.uidesign.generated.edit_toast_section
import dev.chieppa.squash.uidesign.generated.edit_toast_sections
import dev.chieppa.squash.uidesign.generated.shell_action_undo
import dev.chieppa.stretch.frame.ToastHost
import org.jetbrains.compose.resources.getString

/** The Build workspace: the frame's bars and drawers around the panes and their tool strip. */
@Composable
fun WorkspaceScreen(shell: Shell) {
    val state = shell.state
    val armed by state.tool.collectAsState()
    val draw = rememberDrawOutlineTool(state)
    val edit = rememberEditCurvesTool(state)
    val erase = rememberEraseTool(state)
    val tool: PaneTool? = when (armed) {
        BuildTool.DRAW_OUTLINE -> draw
        BuildTool.EDIT_CURVES -> edit
        BuildTool.ERASE -> erase
        BuildTool.NONE -> null
    }
    val refit = state.curves.refit
    LaunchedEffect(refit) {
        if (refit != null) shell.toasts.show(refitMessage(refit), getString(Res.string.shell_action_undo)) { state.undo() }
    }
    BuildFrame(shell, Modifier.testTag(ShellTags.WORKSPACE)) {
        SheetPanes(
            state,
            Modifier.fillMaxSize(),
            tools = { WorkspaceTools(state) },
            tool = tool,
            paneToast = if (shell.toasts.showing) ({ ToastHost(shell.toasts) }) else null,
        )
    }
}

/** What a refit changed, counting sections from one as a person does. */
private suspend fun refitMessage(refit: Refit): String {
    val first = refit.firstSection + 1
    val sections = if (refit.sections == 1) {
        getString(Res.string.edit_toast_section, first)
    } else {
        getString(Res.string.edit_toast_sections, first, first + refit.sections - 1)
    }
    val message = when (refit.reach) {
        RefitReach.SECTIONS -> Res.string.edit_toast_refit
        RefitReach.CLOSED_ENDS -> Res.string.edit_toast_refit_ends
        RefitReach.WHOLE_PART -> Res.string.edit_toast_refit_whole
    }
    return getString(message, sections)
}
