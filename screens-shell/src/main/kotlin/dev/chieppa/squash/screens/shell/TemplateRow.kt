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
import dev.chieppa.squash.screens.libraryUpdate
import dev.chieppa.squash.screens.templateChoices
import dev.chieppa.squash.screens.updateFromLibrary
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.anatomy_template
import dev.chieppa.squash.uidesign.generated.anatomy_update
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuDivider
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.IconButton
import org.jetbrains.compose.resources.stringResource

/**
 * Template: a small menu led by Update from library while the library holds a newer version of the part's template, then the templates
 * the part can change to keeping its drawing, its own checked, each choice one undo step; shown only where it offers something. See
 * HLD: parts, Re-templating keeps the drawing, and data-model, Update from library.
 */
@Composable
internal fun TemplateRow(state: AppState, part: PartId, template: PartTemplate) {
    val choices = state.templateChoices(part)
    val update = state.libraryUpdate(part)
    if (choices.isEmpty() && update == null) return
    val label = stringResource(Res.string.anatomy_template)
    var open by remember(part) { mutableStateOf(false) }
    SettingRow(label) {
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(SquashIcons.PartTemplate, label, { open = true }, Modifier.testTag(ShellTags.TEMPLATE_MENU))
            Menu(open, { open = false }) {
                if (update != null) {
                    Box(Modifier.testTag(ShellTags.TEMPLATE_UPDATE)) {
                        MenuItem(stringResource(Res.string.anatomy_update), {
                            open = false
                            state.updateFromLibrary(part)
                        })
                    }
                    MenuDivider()
                }
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
