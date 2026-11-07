package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.changeTemplate
import dev.chieppa.squash.screens.templateChoices
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.anatomy_template
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.IconButton
import org.jetbrains.compose.resources.stringResource

/**
 * Template: a small menu of the templates the part can change to keeping its drawing, its own checked, a choice being one undo step;
 * shown only where there is one to change to. See HLD: parts, Re-templating keeps the drawing.
 */
@Composable
internal fun TemplateRow(state: AppState, part: PartId, template: PartTemplate) {
    val choices = state.templateChoices(part)
    if (choices.isEmpty()) return
    val label = stringResource(Res.string.anatomy_template)
    var open by remember(part) { mutableStateOf(false) }
    SettingRow(label) {
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(SquashIcons.PartTemplate, label, { open = true }, Modifier.testTag(ShellTags.TEMPLATE_MENU))
            Menu(open, { open = false }) {
                (listOf(template) + choices).forEach { choice ->
                    MenuItem(choice.name, {
                        open = false
                        if (choice != template) state.changeTemplate(part, choice)
                    }, checked = choice == template)
                }
            }
        }
    }
}
