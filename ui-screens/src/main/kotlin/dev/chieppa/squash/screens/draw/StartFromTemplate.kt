package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.templateOutline
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_action_start_template
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TOOL_SIZE = 34.dp

/** The view Start from template would act on: the one on show, while the selected part has no outline there and no stroke is under way. */
fun AppState.templateStartView(): View? {
    val character = session.value?.document?.value?.character ?: return null
    val part = selectedPart.value?.let(character::part) ?: return null
    if (curves.drawing != null || (layout.value == SheetLayout.SINGLE && curves.snapped == null)) return null
    return activeView.value.takeIf { it !in character.resolved(part).outlines }
}

/** Turns the template hint of [AppState.templateStartView] into an ordinary outline, as one undo step. */
fun AppState.startFromTemplate() {
    val view = templateStartView() ?: return
    val opened = session.value ?: return
    val id = selectedPart.value ?: return
    val part = opened.document.value.character.part(id) ?: return
    val curve = templateOutline(opened.templates[part.template], opened.document.value.character.resolved(part), view) ?: return
    opened.send(SetOutline(id, view, curve, thisSide))
}

/** The tool strip's Start from template, faint while the active view has an outline. */
@Composable
fun StartFromTemplateButton(state: AppState) {
    state.session.collectAsState().value?.document?.collectAsState()?.value
    state.selectedPart.collectAsState().value
    state.activeView.collectAsState().value
    state.layout.collectAsState().value
    val label = stringResource(Res.string.shell_action_start_template)
    Tooltip(label, emptyList()) {
        ToolButton(
            SquashIcons.StartFromTemplate,
            label,
            false,
            state::startFromTemplate,
            Modifier.testTag(ShellTags.START_TEMPLATE),
            size = TOOL_SIZE,
            enabled = state.templateStartView() != null,
        )
    }
}
