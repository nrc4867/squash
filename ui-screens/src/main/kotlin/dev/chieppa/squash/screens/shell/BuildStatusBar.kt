package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_status_building
import dev.chieppa.squash.uidesign.generated.shell_status_parts
import dev.chieppa.squash.uidesign.generated.shell_status_ready
import dev.chieppa.squash.uidesign.generated.shell_status_settled
import dev.chieppa.squash.uidesign.generated.shell_status_views
import dev.chieppa.stretch.frame.StatusBar
import dev.chieppa.stretch.frame.StatusItem
import dev.chieppa.stretch.theme.StretchIcons
import org.jetbrains.compose.resources.stringResource

/**
 * The Build frame's status bar: whether the graph has settled, then the selected part's views in the workspace or the
 * character's part count elsewhere.
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
    val plan = state.templates.plan(document.character.bodyPlan)
    val part = selected?.let(document.character::part)
    val template = part?.let { state.templates.template(it.template) }
    StatusBar(
        start = {
            Box(Modifier.testTag(ShellTags.STATUS_START)) {
                if (settled) {
                    StatusItem(stringResource(Res.string.shell_status_settled), StretchIcons.Check)
                } else {
                    StatusItem(stringResource(Res.string.shell_status_building))
                }
            }
        },
        end = {
            Box(Modifier.testTag(ShellTags.STATUS_END)) {
                when {
                    screen == Screen.WORKSPACE && part != null && template != null -> {
                        val counts = viewCounts(template, part)
                        StatusItem(stringResource(Res.string.shell_status_views, counts.drawn, counts.fromTemplate))
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
