package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import dev.chieppa.squash.screens.draw.WorkspaceTools
import dev.chieppa.squash.screens.draw.rememberDrawOutlineTool
import dev.chieppa.squash.screens.edit.Refit
import dev.chieppa.squash.screens.edit.RefitReach
import dev.chieppa.squash.screens.edit.rememberEditCurvesTool
import dev.chieppa.squash.screens.edit.rememberEraseTool
import dev.chieppa.squash.screens.edit.rememberSmoothTool
import dev.chieppa.squash.screens.move.rememberAddPartTool
import dev.chieppa.squash.screens.move.rememberMoveTool
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.spine.rememberSpineWidthTool
import dev.chieppa.squash.screens.surface.paint.rememberBrushTool
import dev.chieppa.squash.screens.surface.paint.rememberFillTool
import dev.chieppa.squash.screens.surface.paint.rememberPickColorTool
import dev.chieppa.squash.screens.surface.paint.rememberPaintEraseTool
import dev.chieppa.squash.screens.surface.paint.rememberRegionBorderTool
import dev.chieppa.squash.screens.surface.lineToolSection
import dev.chieppa.squash.screens.references.over
import dev.chieppa.squash.screens.references.rememberTraceHandles
import dev.chieppa.squash.screens.surface.rememberLineWidthTool
import dev.chieppa.squash.screens.surface.rememberSurfaceEraseTool
import dev.chieppa.squash.screens.surface.rememberSurfaceLines
import dev.chieppa.squash.screens.surface.rememberSurfaceStrokeTool
import dev.chieppa.squash.screens.surface.rememberLineEditTool
import dev.chieppa.squash.screens.surface.FaceBand
import dev.chieppa.squash.screens.surface.HeadAngleBar
import dev.chieppa.squash.screens.surface.faceToolSection
import dev.chieppa.squash.screens.surface.rememberCombTool
import dev.chieppa.squash.screens.surface.rememberDrawMarkTool
import dev.chieppa.squash.screens.surface.rememberEditMarkTool
import dev.chieppa.squash.screens.surface.rememberEraseMarkTool
import dev.chieppa.squash.screens.surface.rememberGazeTargetTool
import dev.chieppa.squash.screens.surface.rememberSelectPartTool
import dev.chieppa.squash.screens.shell.AddPartPopover
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

/** Where Add part's popover opens over the panes: below the strip, at the right, as the board draws it. */
private val POPOVER_TOP = 44.dp
private val POPOVER_END = 24.dp

/** The Build workspace: the panes and their tool strip, and under them the Face stage's band, in the frame's viewport. */
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
    val paintErase = rememberPaintEraseTool(state)
    val lineWidth = rememberLineWidthTool(state)
    val surfaceStroke = rememberSurfaceStrokeTool(state)
    val lineEdit = rememberLineEditTool(state)
    val surfaceErase = rememberSurfaceEraseTool(state)
    val fur = rememberFurStrokeTool(state)
    val comb = rememberCombTool(state)
    val drawMark = rememberDrawMarkTool(state)
    val editMark = rememberEditMarkTool(state)
    val eraseMark = rememberEraseMarkTool(state)
    val gazeTarget = rememberGazeTargetTool(state)
    val traceHandles = rememberTraceHandles(state)
    val selectPart = rememberSelectPartTool(state)
    val addPart = rememberAddPartTool(state)
    val stage by state.stage.collectAsState()
    val armedTool: PaneTool = when (armed) {
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
        BuildTool.PAINT_ERASE -> paintErase
        BuildTool.LINE_WIDTH -> lineWidth
        BuildTool.SURFACE_STROKE -> surfaceStroke
        BuildTool.LINE_EDIT -> lineEdit
        BuildTool.SURFACE_ERASE -> surfaceErase
        BuildTool.FUR_STROKE -> fur
        BuildTool.COMB -> comb
        BuildTool.DRAW_MARK -> drawMark
        BuildTool.EDIT_MARK -> editMark
        BuildTool.ERASE_MARK -> eraseMark
        BuildTool.GAZE_TARGET -> gazeTarget
        BuildTool.ADD_PART -> addPart
        BuildTool.NONE -> selectPart
    }
    val tool = traceHandles.over(armedTool)
    val selected by state.selectedPart.collectAsState()
    LaunchedEffect(armed, selected) { spine.discard() }
    val refit = state.curves.refit
    LaunchedEffect(refit) {
        if (refit != null) shell.toasts.show(refitMessage(refit), getString(Res.string.shell_action_undo)) { state.undo() }
    }
    val facing = stage == Stage.FACE
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SheetPanes(
                state,
                Modifier.fillMaxSize(),
                tools = { WorkspaceTools(state, lineToolSection(state), faceToolSection(state)) },
                tool = tool,
                paneToast = if (shell.toasts.showing) ({ ToastHost(shell.toasts) }) else null,
                surface = rememberSurfaceLines(state),
                singleFoot = if (facing) ({ toViewer, turn -> HeadAngleBar(state, toViewer, turn) }) else null,
            )
            Box(Modifier.align(Alignment.TopEnd).padding(top = POPOVER_TOP, end = POPOVER_END)) { AddPartPopover(state) }
        }
        if (facing) FaceBand(state)
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
