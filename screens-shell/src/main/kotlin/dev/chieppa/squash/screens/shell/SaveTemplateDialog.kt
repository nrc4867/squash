package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.savedTemplateId
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.saveAsTemplate
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.anatomy_save
import dev.chieppa.squash.uidesign.generated.anatomy_save_cancel
import dev.chieppa.squash.uidesign.generated.anatomy_save_name
import dev.chieppa.squash.uidesign.generated.anatomy_save_ok
import dev.chieppa.stretch.frame.StretchDialog
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.TextField
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 320.dp

/**
 * Save as template's dialog: a name for the template, then Save, which writes [part] as a template file into the saved templates'
 * folder and closes; Save is faint while the name gives no id or there is no folder.
 */
@Composable
internal fun SaveTemplateDialog(state: AppState, part: PartId, onClose: () -> Unit) {
    val instance = state.session.value?.document?.value?.character?.part(part)
    var name by remember(part) { mutableStateOf(instance?.let { partName(it, state.templates) }.orEmpty()) }
    val savable = state.templates.saved != null && savedTemplateId(name) != null && instance?.let { state.templates.template(it.template) } != null
    val label = stringResource(Res.string.anatomy_save_name)
    StretchDialog(
        title = stringResource(Res.string.anatomy_save),
        onDismiss = onClose,
        icon = SquashIcons.SaveTemplate,
        width = DIALOG_WIDTH,
        actions = {
            Button(stringResource(Res.string.anatomy_save_cancel), onClose)
            Button(
                stringResource(Res.string.anatomy_save_ok),
                { if (savable && state.saveAsTemplate(part, name) != null) onClose() },
                Modifier.enabledOrFaint(savable).testTag(ShellTags.SAVE_OK),
                kind = ButtonKind.Primary,
            )
        },
    ) {
        TextField(name, { name = it }, Modifier.fillMaxWidth().padding(14.dp).testTag(ShellTags.SAVE_NAME), placeholder = label, contentDescription = label)
    }
}
