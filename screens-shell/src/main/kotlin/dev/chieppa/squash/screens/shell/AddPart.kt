package dev.chieppa.squash.screens.shell

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
import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.add_part
import dev.chieppa.squash.uidesign.generated.add_part_add
import dev.chieppa.squash.uidesign.generated.add_part_one
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
private val TILE_ICON = 32.dp
private val ROW_ICON = 14.dp
private val POPOVER_WIDTH = 320.dp
private val ENTER_KEYS = listOf("Enter")

/** How many parts an open socket takes, as the Parts tree and Add part's list name it: a pair or one. */
@Composable
fun openSocketCount(socket: OpenSocket): String = stringResource(if (socket.paired) Res.string.add_part_pair else Res.string.add_part_one)

/** Add part's section in the Shape inspector: every open socket, the one its popover is open on lit; a row opens the popover there. */
@Composable
fun AddPartSection(state: AppState) {
    val session by state.session.collectAsState()
    val document = session?.document?.collectAsState()?.value ?: return
    val adding by state.addingSocket.collectAsState()
    val sockets = remember(document) { state.openSocketsNow() }
    DrawerSection(stringResource(Res.string.add_part)) {
        for (socket in sockets) {
            ListRow(socket == adding, { state.offerSocket(socket) }, Modifier.testTag(ShellTags.addSocket(socket.name))) {
                Icon(SquashIcons.AddPart, Stretch.colors.inkControl, size = ROW_ICON)
                BasicText(displayName(socket.name), Modifier.weight(1f), style = Stretch.type.body.merge(TextStyle(color = Stretch.colors.ink)), maxLines = 1)
                BasicText(openSocketCount(socket), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
            }
        }
    }
}

/**
 * The popover a socket's dot or row opens: the types that socket takes, what a person draws for the one chosen, and Add, which
 * Enter presses too. Esc or a press outside closes it.
 */
@Composable
fun AddPartPopover(state: AppState) {
    val socket by state.addingSocket.collectAsState()
    val chosen by state.addingType.collectAsState()
    val open = socket ?: return
    val types = open.takes.mapNotNull { state.templates.template(it) }
    val template = types.firstOrNull { it.id == chosen } ?: types.firstOrNull() ?: return
    val focus = remember { FocusRequester() }
    Popover(true, state::closeAddPart, stringResource(Res.string.add_part_title, displayName(open.name)), width = POPOVER_WIDTH) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { type -> TypeTile(type, type.id == template.id) { state.addingType.value = type.id } }
            }
            BasicText(stringResource(Res.string.shell_part_you_draw), style = Stretch.type.eyebrow.merge(TextStyle(color = Stretch.colors.inkMuted)))
            YouDrawChips(template)
            FactRow(stringResource(Res.string.shell_part_joints), template.jointCount().toString())
            val add = stringResource(Res.string.add_part_add, template.name.lowercase())
            Tooltip(add, ENTER_KEYS) {
                Button(add, state::addPart, Modifier.fillMaxWidth().testTag(ShellTags.ADD_PART_ADD), kind = ButtonKind.Primary, icon = SquashIcons.AddPart)
            }
        }
    }
    LaunchedEffect(open) { focus.requestFocus() }
}

@Composable
private fun TypeTile(template: PartTemplate, selected: Boolean, onClick: () -> Unit) {
    val c = Stretch.colors
    Card(Modifier.size(TILE).testTag(ShellTags.addTile(template.id.value)), onClick, selected) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (hasTileArt(template.id.value)) PartTileArt(template.id.value, Modifier.fillMaxSize()) else Icon(SquashIcons.Parts, c.inkControl, size = TILE_ICON)
            }
            BasicText(template.name, style = Stretch.type.bodySm.merge(TextStyle(color = c.ink)), maxLines = 1)
        }
    }
}
