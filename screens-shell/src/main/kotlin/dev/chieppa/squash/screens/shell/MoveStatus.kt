package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.move_status_add_socket
import dev.chieppa.squash.uidesign.generated.move_status_tool
import dev.chieppa.squash.uidesign.generated.move_tool
import dev.chieppa.stretch.frame.StatusItem
import org.jetbrains.compose.resources.stringResource

/** The Shape stage's start of the status bar while Move is armed: Shape · Move. */
@Composable
internal fun MoveStatusTool() {
    Box(Modifier.testTag(ShellTags.STATUS_STAGE)) { StatusItem(stringResource(Res.string.move_status_tool, stringResource(Res.string.move_tool))) }
}

/** The end of the status bar while Move adds sockets: the selected part, and how many parts the character has. */
@Composable
internal fun AddSocketStatus(state: AppState, document: SquashDocument, part: PartInstance) {
    StatusItem(stringResource(Res.string.move_status_add_socket, partName(part, state.templates), document.character.parts.size))
}
