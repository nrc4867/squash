package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.catalog.viewCounts
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.move.MoveMode
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_status_section
import dev.chieppa.squash.uidesign.generated.edit_status_sections
import dev.chieppa.squash.uidesign.generated.refs_status_image
import dev.chieppa.squash.uidesign.generated.refs_status_images
import dev.chieppa.squash.uidesign.generated.sheet_status_swap
import dev.chieppa.squash.uidesign.generated.shell_status_parts
import dev.chieppa.squash.uidesign.generated.shell_status_pen
import dev.chieppa.squash.uidesign.generated.shell_status_ready
import dev.chieppa.squash.uidesign.generated.shell_status_settled
import dev.chieppa.squash.uidesign.generated.shell_status_view_of
import dev.chieppa.squash.uidesign.generated.shell_status_working
import dev.chieppa.squash.uidesign.generated.spine_status_writes
import dev.chieppa.squash.uidesign.generated.rig_status_counts
import dev.chieppa.squash.uidesign.generated.rig_status_tool
import dev.chieppa.squash.screens.rig.paintedSeams
import dev.chieppa.squash.screens.rig.rememberRig
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.stretch.frame.StatusBar
import dev.chieppa.stretch.frame.StatusItem
import dev.chieppa.stretch.theme.StretchIcons
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/**
 * The Build frame's status bar: the pen's pressure while one draws, or what the graph is working on, or that it has settled;
 * in Rig and Pose, the stage and its armed tool beside that; then the reference images' count with References open, Swap pane in Focus, the
 * rig's bones, parts and painted seams in Rig, the paw lifted and the paws planted in Pose, or the selected part's views and where they disagree in the workspace, or the character's
 * part count elsewhere.
 */
@Composable
fun BuildStatusBar(shell: Shell) {
    val state = shell.state
    val session by state.session.collectAsState()
    val screen by state.screen.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val open = session
    if (open == null) {
        StatusBar(start = { Box(Modifier.testTag(ShellTags.STATUS_START)) { StatusItem(stringResource(Res.string.shell_status_ready)) } })
        return
    }
    val document by open.document.collectAsState()
    val settled by open.graph.settled.collectAsState()
    val progress by open.graph.progress.collectAsState()
    val mirror by state.mirror.collectAsState()
    val tool by state.tool.collectAsState()
    val layout by state.layout.collectAsState()
    val stage by state.stage.collectAsState()
    val rig = rememberRig(state)
    val rigging = screen == Screen.WORKSPACE && stage == Stage.RIG
    val moveMode by state.move.mode.collectAsState()
    val adding = screen == Screen.WORKSPACE && stage == Stage.SHAPE && tool == BuildTool.MOVE && moveMode != MoveMode.PLACE_PART
    val posing = screen == Screen.WORKSPACE && stage == Stage.POSE
    val pen = state.curves.penPressure
    val drawing = state.curves.drawing
    val plan = state.templates.plan(document.character.bodyPlan)
    val part = selected?.let(document.character::part)
    val template = part?.let { state.templates.template(it.template) }
    val outlines = part?.let { document.character.editBase(it, !mirror).outlines }
    val touched = state.curves.refit?.takeIf { screen == Screen.WORKSPACE && it.part == part?.id && outlines?.get(it.view) == it.curve }
    StatusBar(
        start = {
            Box(Modifier.testTag(ShellTags.STATUS_START)) {
                when {
                    pen != null -> StatusItem(stringResource(Res.string.shell_status_pen, pressureLabel(pen)), SquashIcons.DrawOutline)
                    settled -> StatusItem(stringResource(Res.string.shell_status_settled), StretchIcons.Check)
                    else -> StatusItem(stringResource(Res.string.shell_status_working, stageLabel(progress.working(BuildStages.ORDER))))
                }
            }
            if (posing) PoseStatusTool(tool)
            if (screen == Screen.WORKSPACE && stage == Stage.SHAPE && tool == BuildTool.MOVE) MoveStatusTool()
            if (rigging) {
                toolLabel(tool)?.takeIf { tool.rigs }?.let { Box(Modifier.testTag(ShellTags.STATUS_STAGE)) { StatusItem(stringResource(Res.string.rig_status_tool, stringResource(it))) } }
            }
        },
        end = {
            Box(Modifier.testTag(ShellTags.STATUS_END)) {
                when {
                    tool == BuildTool.SPINE_WIDTH && drawing != null && screen == Screen.WORKSPACE -> StatusItem(stringResource(Res.string.spine_status_writes))
                    screen == Screen.WORKSPACE && shell.layout[DrawerIds.REFERENCES]?.open == true -> {
                        val count = document.references.images.size
                        StatusItem(if (count == 1) stringResource(Res.string.refs_status_image) else stringResource(Res.string.refs_status_images, count))
                    }
                    screen == Screen.WORKSPACE && layout == SheetLayout.FOCUS -> StatusItem(stringResource(Res.string.sheet_status_swap))
                    posing -> PoseStatusPaws(state, document.character)
                    adding && part != null -> AddingStatus(state, document, part, moveMode)
                    rigging && rig != null -> StatusItem(stringResource(Res.string.rig_status_counts, rig.bones.size, rig.parts.size, paintedSeams(rig, document.character)))
                    touched != null -> StatusItem(
                        stringResource(if (touched.sections == 1) Res.string.edit_status_section else Res.string.edit_status_sections, touched.sections),
                    )
                    screen == Screen.WORKSPACE && outlines != null && template != null -> {
                        val counts = countsWhileDrawing(viewCounts(template, outlines), drawing != null && drawing !in outlines)
                        val snapped = state.curves.snapped
                        if (snapped != null && settled && drawing == null && state.curves.conflicts == 0) {
                            StatusItem(stringResource(Res.string.shell_status_view_of, viewLabel(snapped), counts.drawn, counts.drawn + counts.fromTemplate))
                        } else {
                            StatusItem(viewsStatusText(counts, viewsLine(counts, drawing != null, !settled), state.curves.conflicts))
                        }
                    }
                    plan != null -> {
                        val progress = partProgress(document, plan, state.templates)
                        StatusItem(stringResource(Res.string.shell_status_parts, progress.drawn, progress.total))
                    }
                }
            }
        },
    )
}

/** A pen's pressure as the status bar reads it, to two decimals. */
fun pressureLabel(pressure: Float): String = "%.2f".format(Locale.ROOT, pressure)
