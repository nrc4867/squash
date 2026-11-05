package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.chieppa.squash.screens.draw.WorkspaceTools
import dev.chieppa.squash.screens.draw.rememberDrawOutlineTool
import dev.chieppa.squash.screens.edit.Refit
import dev.chieppa.squash.screens.edit.RefitReach
import dev.chieppa.squash.screens.edit.rememberEditCurvesTool
import dev.chieppa.squash.screens.edit.rememberEraseTool
import dev.chieppa.squash.screens.edit.rememberSmoothTool
import dev.chieppa.squash.screens.move.rememberMoveTool
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.spine.rememberSpineWidthTool
import dev.chieppa.squash.screens.surface.paint.rememberBrushTool
import dev.chieppa.squash.screens.surface.paint.rememberFillTool
import dev.chieppa.squash.screens.surface.paint.rememberPickColorTool
import dev.chieppa.squash.screens.surface.paint.rememberRegionBorderTool
import dev.chieppa.squash.screens.surface.lineToolSection
import dev.chieppa.squash.screens.surface.rememberLineWidthTool
import dev.chieppa.squash.screens.surface.rememberSurfaceEraseTool
import dev.chieppa.squash.screens.surface.rememberSurfaceLines
import dev.chieppa.squash.screens.surface.rememberSurfaceStrokeTool
import dev.chieppa.squash.screens.surface.rememberCombTool
import dev.chieppa.squash.screens.surface.rememberFurStrokeTool
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_toast_refit
import dev.chieppa.squash.uidesign.generated.edit_toast_refit_ends
import dev.chieppa.squash.uidesign.generated.edit_toast_refit_whole
import dev.chieppa.squash.uidesign.generated.edit_toast_section
import dev.chieppa.squash.uidesign.generated.edit_toast_sections
import dev.chieppa.squash.uidesign.generated.shell_action_undo
import dev.chieppa.stretch.frame.ToastHost
import org.jetbrains.compose.resources.getString

/** The Build workspace: the panes and their tool strip, in the frame's viewport. */
@Composable
fun WorkspaceScreen(shell: Shell) {
    val state = shell.state
    val armed by state.tool.collectAsState()
    val draw = rememberDrawOutlineTool(state)
    val spine = rememberSpineWidthTool(state)
    val edit = rememberEditCurvesTool(state)
    val erase = rememberEraseTool(state)
    val smooth = rememberSmoothTool(state)
    val move = rememberMoveTool(state)
    val region = rememberRegionBorderTool(state)
    val fill = rememberFillTool(state)
    val brush = rememberBrushTool(state)
    val pick = rememberPickColorTool(state)
    val lineWidth = rememberLineWidthTool(state)
    val surfaceStroke = rememberSurfaceStrokeTool(state)
    val surfaceErase = rememberSurfaceEraseTool(state)
    val fur = rememberFurStrokeTool(state)
    val comb = rememberCombTool(state)
    val linesOpen = shell.showsLineTools
    LaunchedEffect(linesOpen) {
        val tool = state.tool.value
        if (linesOpen && !tool.inks) state.tool.value = BuildTool.LINE_WIDTH
        if (!linesOpen && tool.inks) state.tool.value = BuildTool.DRAW_OUTLINE
    }
    val tool: PaneTool? = when (armed) {
        BuildTool.DRAW_OUTLINE -> draw
        BuildTool.SPINE_WIDTH -> spine
        BuildTool.EDIT_CURVES -> edit
        BuildTool.ERASE -> erase
        BuildTool.SMOOTH -> smooth
        BuildTool.MOVE -> move
        BuildTool.REGION_BORDER -> region
        BuildTool.FILL -> fill
        BuildTool.BRUSH -> brush
        BuildTool.PICK_COLOR -> pick
        BuildTool.LINE_WIDTH -> lineWidth
        BuildTool.SURFACE_STROKE -> surfaceStroke
        BuildTool.SURFACE_ERASE -> surfaceErase
        BuildTool.FUR_STROKE -> fur
        BuildTool.COMB -> comb
        BuildTool.NONE -> null
    }
    val selected by state.selectedPart.collectAsState()
    LaunchedEffect(armed, selected) { spine.discard() }
    val refit = state.curves.refit
    LaunchedEffect(refit) {
        if (refit != null) shell.toasts.show(refitMessage(refit), getString(Res.string.shell_action_undo)) { state.undo() }
    }
    SheetPanes(
        state,
        Modifier.fillMaxSize(),
        tools = { WorkspaceTools(state, lineToolSection(state)) },
        tool = tool,
        paneToast = if (shell.toasts.showing) ({ ToastHost(shell.toasts) }) else null,
        surface = rememberSurfaceLines(state),
    )
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
