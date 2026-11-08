package dev.chieppa.squash.screens.shell

import androidx.compose.ui.graphics.vector.ImageVector

import dev.chieppa.squash.character.build.isGeneric

import dev.chieppa.squash.screens.addPart
import dev.chieppa.squash.screens.openSocketsNow
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.document.TemplateId
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import dev.chieppa.squash.uidesign.generated.add_part_on_socket
import dev.chieppa.squash.uidesign.generated.move_socket_name
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.add_part
import dev.chieppa.squash.uidesign.generated.add_part_add
import dev.chieppa.squash.uidesign.generated.add_part_one
import dev.chieppa.squash.uidesign.generated.add_part_new_socket
import dev.chieppa.squash.uidesign.generated.add_part_pair
import dev.chieppa.squash.uidesign.generated.add_part_title
import dev.chieppa.squash.uidesign.generated.shell_part_joints
import dev.chieppa.squash.uidesign.generated.shell_part_you_draw
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.Popover
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Card
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val TILE = 88.dp
private val GRID_TILE = 72.dp
private val GRID_GAP = 6.dp
private const val GRID_COLUMNS = 3
private val TILE_ICON = 32.dp
private val ROW_ICON = 14.dp
private val POPOVER_WIDTH = 320.dp
private val ENTER_KEYS = listOf("Enter")

/** How many parts an open socket takes, as the Parts tree and Add part's list name it: a pair or one. */
@Composable
fun openSocketCount(socket: OpenSocket): String = stringResource(if (socket.paired) Res.string.add_part_pair else Res.string.add_part_one)

/** Names for [sockets] as the panels read them: socket 1 for a hand-made one, else the socket's own name. */
@Composable
fun socketNames(sockets: List<OpenSocket>): (String) -> String {
    val numbered = sockets.mapNotNull { HandSockets.idOf(it.name) }.associateWith { stringResource(Res.string.move_socket_name, it) }
    return { name -> HandSockets.idOf(name)?.let(numbered::get) ?: displayName(name) }
}

/**
 * Add part's section in the Shape inspector: every open socket, the one its popover is open on lit, a row opening the popover there;
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

/**
 * The popover a socket's dot or row opens: the types that socket takes, what a person draws for the one chosen, and Add, which
 * Enter presses too. A hand-made socket, which takes every type, is titled by its part and number and lists the types as a grid.
 * Esc or a press outside closes it.
 */
@Composable
fun AddPartPopover(state: AppState) {
    val socket by state.addingSocket.collectAsState()
    val chosen by state.addingType.collectAsState()
    val open = socket ?: return
    val types = open.takes.mapNotNull { state.templates.template(it) }
    val template = types.firstOrNull { it.id == chosen } ?: types.firstOrNull() ?: return
    val focus = remember { FocusRequester() }
    val number = HandSockets.idOf(open.name)
    val parent = state.session.value?.document?.value?.character?.part(open.parent)
    val title = if (number != null && parent != null) {
        stringResource(Res.string.add_part_on_socket, partName(parent, state.templates), stringResource(Res.string.move_socket_name, number))
    } else {
        stringResource(Res.string.add_part_title, displayName(open.name))
    }
    Popover(true, state::closeAddPart, title, width = POPOVER_WIDTH) {
        Column(
            Modifier
                .testTag(ShellTags.ADD_PART)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val enter = event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)
                    if (enter) state.addPart()
                    enter
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (number != null) {
                TypeGrid(types, template) { state.addingType.value = it }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { type -> TypeTile(type, type.id == template.id, Modifier.size(TILE)) { state.addingType.value = type.id } }
                }
                BasicText(stringResource(Res.string.shell_part_you_draw), style = Stretch.type.eyebrow.merge(TextStyle(color = Stretch.colors.inkMuted)))
                YouDrawChips(template)
                FactRow(stringResource(Res.string.shell_part_joints), template.jointCount().toString())
            }
            val add = stringResource(Res.string.add_part_add, template.name.lowercase())
            Tooltip(add, ENTER_KEYS) {
                Button(add, state::addPart, Modifier.fillMaxWidth().testTag(ShellTags.ADD_PART_ADD), kind = ButtonKind.Primary, icon = SquashIcons.AddPart)
            }
        }
    }
    LaunchedEffect(open) { focus.requestFocus() }
}

/** Every type a hand-made socket takes, three to a row. */
@Composable
private fun TypeGrid(types: List<PartTemplate>, chosen: PartTemplate, choose: (TemplateId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        types.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                row.forEach { type -> TypeTile(type, type.id == chosen.id, Modifier.weight(1f).height(GRID_TILE)) { choose(type.id) } }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TypeTile(template: PartTemplate, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Stretch.colors
    Card(modifier.testTag(ShellTags.addTile(template.id.value)), onClick, selected) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val role = template.role
                if (role != null && hasTileArt(role)) PartTileArt(role, Modifier.fillMaxSize()) else Icon(tileIcon(template), c.inkControl, size = TILE_ICON)
            }
            BasicText(template.name, style = Stretch.type.bodySm.merge(TextStyle(color = c.ink)), maxLines = 1)
        }
    }
}

/** The icon a template with no art of its own shows: a generic sheet, blob or tube by its shape, else the general parts icon. */
private fun tileIcon(template: PartTemplate): ImageVector = when {
    !template.isGeneric -> SquashIcons.Parts
    template.isSheet -> SquashIcons.PartSheet
    template.openEnd == null -> SquashIcons.PartBlob
    else -> SquashIcons.PartTube
}
