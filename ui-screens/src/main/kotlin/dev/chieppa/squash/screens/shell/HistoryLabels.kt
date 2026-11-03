package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.RemovePart
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_step_add
import dev.chieppa.squash.uidesign.generated.shell_step_outline
import dev.chieppa.squash.uidesign.generated.shell_step_parameter
import dev.chieppa.squash.uidesign.generated.shell_step_remove
import dev.chieppa.stretch.core.Command
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.stringResource

private const val FIRST = "\u0001"
private const val SECOND = "\u0002"

/** How the History drawer names each command, in the app's language. */
@Composable
fun historyLabels(): (Command<SquashDocument>) -> String {
    val add = stringResource(Res.string.shell_step_add, FIRST)
    val remove = stringResource(Res.string.shell_step_remove, FIRST)
    val outline = stringResource(Res.string.shell_step_outline, FIRST)
    val parameter = stringResource(Res.string.shell_step_parameter, FIRST, SECOND)
    val views = VIEW_ORDER.associateWith { viewLabel(it) }
    return { command ->
        when (command) {
            is AddPart -> add.replace(FIRST, partName(command.part))
            is AddParts -> add.replace(FIRST, command.parts.firstOrNull()?.let(::partName).orEmpty())
            is RemovePart -> remove.replace(FIRST, "")
            is SetOutline -> outline.replace(FIRST, views[command.view].orEmpty())
            is SetParameter -> parameter.replace(FIRST, displayName(command.name)).replace(SECOND, command.value?.let(::formatNumber).orEmpty())
            else -> ""
        }
    }
}

private fun partName(part: PartInstance): String =
    part.socket?.let { displayName(it.socket) } ?: displayName(part.template.id.value)
