package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.catalog.ViewCounts
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_stage_assembly
import dev.chieppa.squash.uidesign.generated.shell_stage_mesh
import dev.chieppa.squash.uidesign.generated.shell_stage_slices
import dev.chieppa.squash.uidesign.generated.shell_stage_surface
import dev.chieppa.squash.uidesign.generated.shell_stage_surface_work
import dev.chieppa.squash.uidesign.generated.shell_status_conflict
import dev.chieppa.squash.uidesign.generated.shell_status_conflicts
import dev.chieppa.squash.uidesign.generated.shell_status_views
import dev.chieppa.squash.uidesign.generated.shell_status_views_all
import dev.chieppa.squash.uidesign.generated.shell_status_views_drawing
import dev.chieppa.squash.uidesign.generated.shell_status_views_of
import org.jetbrains.compose.resources.stringResource

/** Which way the status bar counts a part's views. */
enum class ViewsLine { DRAWN_FROM_TEMPLATE, DRAWING, ALL_DRAWN, WORKING }

/** The status bar's view count: a stroke in progress names itself, a run counts toward the whole, and all drawn says so. */
fun viewsLine(counts: ViewCounts, drawing: Boolean, working: Boolean): ViewsLine = when {
    drawing -> ViewsLine.DRAWING
    counts.fromTemplate == 0 -> ViewsLine.ALL_DRAWN
    working -> ViewsLine.WORKING
    else -> ViewsLine.DRAWN_FROM_TEMPLATE
}

/** [counts] with a new stroke taken out of what comes from the template while it is being drawn in an undrawn view. */
fun countsWhileDrawing(counts: ViewCounts, drawingNewView: Boolean): ViewCounts =
    if (drawingNewView) counts.copy(fromTemplate = (counts.fromTemplate - 1).coerceAtLeast(0)) else counts

/** The status bar's words for a part's views and where they disagree. */
@Composable
fun viewsStatusText(counts: ViewCounts, line: ViewsLine, conflicts: Int): String {
    val views = when (line) {
        ViewsLine.DRAWN_FROM_TEMPLATE -> stringResource(Res.string.shell_status_views, counts.drawn, counts.fromTemplate)
        ViewsLine.DRAWING -> stringResource(Res.string.shell_status_views_drawing, counts.drawn, counts.fromTemplate)
        ViewsLine.ALL_DRAWN -> stringResource(Res.string.shell_status_views_all, counts.drawn)
        ViewsLine.WORKING -> stringResource(Res.string.shell_status_views_of, counts.drawn, counts.drawn + counts.fromTemplate)
    }
    return when (conflicts) {
        0 -> views
        1 -> stringResource(Res.string.shell_status_conflict, views)
        else -> stringResource(Res.string.shell_status_conflicts, views, conflicts)
    }
}

/** The name of the graph's stage [stage] for the Working readout. */
@Composable
fun stageLabel(stage: String?): String = stringResource(
    when (stage) {
        BuildStages.SLICES -> Res.string.shell_stage_slices
        BuildStages.SURFACE -> Res.string.shell_stage_surface
        BuildStages.ASSEMBLY -> Res.string.shell_stage_assembly
        BuildStages.SURFACE_WORK -> Res.string.shell_stage_surface_work
        else -> Res.string.shell_stage_mesh
    },
)
