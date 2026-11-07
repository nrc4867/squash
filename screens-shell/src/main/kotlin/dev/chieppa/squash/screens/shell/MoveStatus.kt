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
import dev.chieppa.squash.screens.move.MoveMode
import dev.chieppa.squash.uidesign.generated.move_status_add_joint
import dev.chieppa.squash.uidesign.generated.move_status_add_landmark
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

/** The end of the status bar while Move adds sockets, joints or landmarks: the selected part, what a press adds, and how many parts the character has. */
@Composable
internal fun AddingStatus(state: AppState, document: SquashDocument, part: PartInstance, mode: MoveMode) {
    val text = when (mode) {
        MoveMode.ADD_JOINT -> Res.string.move_status_add_joint
        MoveMode.ADD_LANDMARK -> Res.string.move_status_add_landmark
        else -> Res.string.move_status_add_socket
    }
    StatusItem(stringResource(text, partName(part, state.templates), document.character.parts.size))
}
