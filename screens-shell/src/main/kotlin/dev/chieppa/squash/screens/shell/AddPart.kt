package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.block.DigitTips
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.openSocketsNow
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.add_part
import dev.chieppa.squash.uidesign.generated.add_part_new_socket
import dev.chieppa.squash.uidesign.generated.add_part_one
import dev.chieppa.squash.uidesign.generated.add_part_pair
import dev.chieppa.squash.uidesign.generated.add_part_tip
import dev.chieppa.squash.uidesign.generated.move_socket_name
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import org.jetbrains.compose.resources.stringResource

private val ROW_ICON = 14.dp

/** How many parts an open socket takes, as the Parts tree and Add part's list name it: a pair or one. */
@Composable
fun openSocketCount(socket: OpenSocket): String = stringResource(if (socket.paired) Res.string.add_part_pair else Res.string.add_part_one)

/**
 * Names for [sockets] as the panels read them: socket 1 for a hand-made one or any socket of hand-made row 1, tip 1 for the first
 * digit's tip socket, else the socket's own name.
 */
@Composable
fun socketNames(sockets: List<OpenSocket>): (String) -> String {
    val numbered = sockets.mapNotNull { handNumber(it.name) }.associateWith { stringResource(Res.string.move_socket_name, it) }
    val tips = sockets.mapNotNull { DigitTips.indexOf(it.name) }.associateWith { stringResource(Res.string.add_part_tip, it + 1) }
    return { name -> handNumber(name)?.let(numbered::get) ?: DigitTips.indexOf(name)?.let(tips::get) ?: displayName(name) }
}

/** The number of the hand-made socket or row [socket] is, or null for any other socket. */
private fun handNumber(socket: String): Int? = HandSockets.idOf(socket) ?: HandSockets.rowOf(socket)

/**
 * Add part's section in the Shape inspector: every open socket, the one Library targets lit, a row targeting it in Library;
 * then the outward angle and pair flag a socket placed by a press on a part takes.
 */
@Composable
fun AddPartSection(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document = open.document.collectAsState().value
    val adding by state.addingSocket.collectAsState()
    val sockets = remember(document) { state.openSocketsNow() }
    val names = socketNames(sockets)
    if (sockets.isNotEmpty()) DrawerSection(stringResource(Res.string.add_part)) {
        for (socket in sockets) {
            ListRow(socket == adding, { state.offerSocket(socket) }, Modifier.testTag(ShellTags.addSocket(socket.name))) {
                Icon(SquashIcons.AddPart, Stretch.colors.inkControl, size = ROW_ICON)
                BasicText(names(socket.name), Modifier.weight(1f), style = Stretch.type.body.merge(TextStyle(color = Stretch.colors.ink)), maxLines = 1)
                BasicText(openSocketCount(socket), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
            }
        }
    }
    DrawerSection(stringResource(Res.string.add_part_new_socket)) { SocketControls(state, open) }
}
