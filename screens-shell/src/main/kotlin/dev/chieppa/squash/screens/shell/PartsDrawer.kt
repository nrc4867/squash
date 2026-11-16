package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.screens.dress.DressNote
import dev.chieppa.squash.screens.dress.dressNote
import dev.chieppa.squash.uidesign.generated.dress_hidden
import dev.chieppa.squash.uidesign.generated.dress_through
import dev.chieppa.squash.uidesign.generated.dress_bare
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import dev.chieppa.squash.screens.library.LibraryTab
import dev.chieppa.squash.screens.library.removePart
import dev.chieppa.squash.screens.library.replaceable
import dev.chieppa.squash.uidesign.generated.parts_menu_add
import dev.chieppa.squash.uidesign.generated.parts_menu_add_tips
import dev.chieppa.squash.uidesign.generated.parts_menu_remove
import dev.chieppa.squash.uidesign.generated.parts_menu_replace
import dev.chieppa.squash.uidesign.generated.parts_tips
import dev.chieppa.squash.uidesign.generated.parts_tips_count
import dev.chieppa.stretch.frame.ContextMenuPopup
import dev.chieppa.stretch.frame.MenuItem
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.uidesign.generated.role_guessed
import dev.chieppa.squash.uidesign.generated.role_title
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.PAIR_SIZE
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.character.catalog.twin
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.openSocketsNow
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_tree_differs
import dev.chieppa.squash.uidesign.generated.shell_tree_left
import dev.chieppa.squash.uidesign.generated.shell_tree_pair
import dev.chieppa.squash.uidesign.generated.shell_tree_right
import dev.chieppa.squash.uidesign.generated.shell_tree_to_draw
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.Tree
import dev.chieppa.stretch.ui.flattenTree
import org.jetbrains.compose.resources.stringResource

private val SWATCH_SHAPE = RoundedCornerShape(3.dp)
private val TRAILING_GAP = 6.dp
private const val NOTE_JOIN = " · "

/**
 * The open character's parts and empty sockets: a part selects, and a socket, shown as a row to add, targets it in Library. A part's
 * empty digit tips fold into one row, closed at first, which targets them all. A row's context menu, or Shift F10, holds Replace… and
 * Remove (Del) for a part no garment owns, and Add… for a socket; [openLibrary] shows Library for Replace…
 */
@Composable
fun PartsDrawerContent(state: AppState, openLibrary: () -> Unit = {}) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by open.document.collectAsState()
    val plan = state.templates.plan(document.character.bodyPlan) ?: return
    val selected by state.selectedPart.collectAsState()
    val socket by state.addingSocket.collectAsState()
    var collapsed by remember { mutableStateOf(emptySet<Any>()) }
    var openedTips by remember { mutableStateOf(emptySet<Any>()) }
    var menu by remember { mutableStateOf<Pair<PartRow, Offset>?>(null) }

    val mirror by state.mirror.collectAsState()
    val left = stringResource(Res.string.shell_tree_left)
    val right = stringResource(Res.string.shell_tree_right)
    val names = socketNames(remember(document) { state.openSocketsNow() })
    val roots = partTree(document, plan, state.templates, mirror, { if (it == Side.LEFT) left else right }, names, stringResource(Res.string.parts_tips))
    val guesses = roleLabels(state.templates.roles(document.character).fourLegged)
    val rows = flattenTree(roots) { true }.map { it.node.item }
    val stage by state.stage.collectAsState()
    val assembled by remember(open) { open.assembly.output }.collectAsState()
    val notes: (PartRow) -> DressNote? = if (stage == Stage.DRESS) {
        { row -> (row as? PartRow.Placed)?.let { document.character.dressNote(it.part, assembled) } }
    } else {
        { null }
    }
    val current = rows.firstOrNull { it is PartRow.Open && it.socket == socket } ?: rows.firstOrNull { it is PartRow.SideOf && it.part.id == selected } ?: rows.firstOrNull {
        it is PartRow.Placed && (it.part.id == selected || document.twin(it.part)?.id == selected)
    }
    val tips = rows.filterIsInstance<PartRow.Tips>().map<PartRow, Any> { it.key }.toSet()
    DrawerSection(plan.name, divider = false) {
        Box(
            Modifier.onKeyEvent { event ->
                val delete = event.type == KeyEventType.KeyDown && (event.key == Key.Delete || event.key == Key.Backspace)
                val part = (current as? PartRow.Placed)?.part ?: (current as? PartRow.SideOf)?.part
                if (delete && part != null && replaceable(part)) state.removePart(part.id)
                delete && part != null
            },
        ) {
            Tree(
                roots = roots,
                key = { it.key },
                label = { it.label },
                expanded = rows.map<PartRow, Any> { it.key }.toSet() - tips - collapsed + openedTips,
                onExpandedChange = { key, expanded ->
                    if (key in tips) {
                        openedTips = if (expanded) openedTips + key else openedTips - key
                    } else {
                        collapsed = if (expanded) collapsed - key else collapsed + key
                    }
                },
                selection = setOfNotNull(current?.key),
                onSelectionChange = { keys ->
                    val key = keys.lastOrNull { it != current?.key } ?: keys.lastOrNull()
                    rows.firstOrNull { it.key == key }?.let { pressRow(state, it) }
                },
                onContextMenu = { row, at -> menu = row to at },
                leading = { RowSwatch(it) },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(TRAILING_GAP), verticalAlignment = Alignment.CenterVertically) {
                        RowUnseated(it)
                        RowRole(it, guesses)
                        RowState(it)
                        notes(it)?.let { note -> RowDressNote(it, note) }
                    }
                },
            )
            ContextMenuPopup(menu?.second, { menu = null }) { close ->
                menu?.first?.let { row -> PartRowMenu(state, row, openLibrary, close) }
            }
        }
    }
}

/** What a press on [row] does: a part selects, a socket or a folded tips row is targeted in Library. */
private fun pressRow(state: AppState, row: PartRow) {
    when (row) {
        is PartRow.Placed -> state.selectPart(row.part.id)
        is PartRow.SideOf -> state.selectPart(row.part.id)
        is PartRow.Open -> state.offerSocket(row.socket)
        is PartRow.Tips -> state.offerSocket(row.sockets.first())
    }
}

/** The menu of a row in Parts: Replace… and Remove for a part no garment owns, Add… for a socket, Add to all tips for a tips row. */
@Composable
private fun PartRowMenu(state: AppState, row: PartRow, openLibrary: () -> Unit, close: () -> Unit) {
    when (row) {
        is PartRow.Placed, is PartRow.SideOf -> {
            val part = (row as? PartRow.Placed)?.part ?: (row as PartRow.SideOf).part
            if (!replaceable(part)) return
            Box(Modifier.testTag(ShellTags.PART_MENU_REPLACE)) {
                MenuItem(stringResource(Res.string.parts_menu_replace), {
                    close()
                    state.closeAddPart()
                    state.selectPart(part.id)
                    state.library.tab.value = LibraryTab.PARTS
                    state.library.cursor.value = part.template.id
                    openLibrary()
                }, icon = SquashIcons.ReplacePart)
            }
            Box(Modifier.testTag(ShellTags.PART_MENU_REMOVE)) {
                MenuItem(stringResource(Res.string.parts_menu_remove), {
                    close()
                    state.removePart(part.id)
                }, icon = SquashIcons.RemovePart, keys = DELETE_KEY)
            }
        }
        is PartRow.Open -> Box(Modifier.testTag(ShellTags.PART_MENU_ADD)) {
            MenuItem(stringResource(Res.string.parts_menu_add), {
                close()
                state.offerSocket(row.socket)
            }, icon = SquashIcons.AddPart, keys = ENTER_KEY)
        }
        is PartRow.Tips -> Box(Modifier.testTag(ShellTags.PART_MENU_ADD)) {
            MenuItem(stringResource(Res.string.parts_menu_add_tips), {
                close()
                state.offerSocket(row.sockets.first())
            }, icon = SquashIcons.AddPart, keys = ENTER_KEY)
        }
    }
}

/** The keys the Parts menu names beside its items. */
private const val DELETE_KEY = "Del"
private const val ENTER_KEY = "Enter"

/** A part's base color; with none, filled once it has an outline and hollow until then. */
@Composable
private fun RowSwatch(row: PartRow) {
    val c = Stretch.colors
    val shape = SWATCH_SHAPE
    val filled = row is PartRow.Placed && row.drawn
    if (row is PartRow.SideOf) {
        Icon(if (row.differs) SquashIcons.PairOverride else SquashIcons.Mirror, c.inkMuted, Modifier.testTag(ShellTags.sideRow(row.tagName)), size = 12.dp)
        return
    }
    if (row is PartRow.Open) {
        Icon(SquashIcons.AddPart, c.inkControl, Modifier.testTag(ShellTags.partRow(row.tagName)), size = 12.dp)
        return
    }
    if (row is PartRow.Tips) {
        Icon(SquashIcons.AddPart, c.inkControl, Modifier.testTag(ShellTags.tipsRow(row.tagName)), size = 12.dp)
        return
    }
    Box(
        Modifier
            .testTag(if (row is PartRow.SideOf) ShellTags.sideRow(row.tagName) else ShellTags.partRow(row.tagName))
            .size(12.dp)
            .background((row as? PartRow.Placed)?.base?.let { Color(it.red, it.green, it.blue) } ?: if (filled) c.accent else Color.Transparent, shape)
            .border(Stretch.stroke, c.lineStrong, shape),
    )
}

/** A part's role while it is a guess, as a dashed chip; nothing once it is confirmed. */
@Composable
private fun RowRole(row: PartRow, labels: (Role) -> String) {
    val guessed = (row as? PartRow.Placed)?.guessed ?: return
    Chip(stringResource(Res.string.role_guessed, labels(guessed)), Modifier.testTag(ShellTags.partRowRole(row.tagName)), dashed = true, description = stringResource(Res.string.role_title))
}

/** The socket a part sits on, highlighted, while its parent does not offer it; nothing otherwise. */
@Composable
private fun RowUnseated(row: PartRow) {
    val socket = (row as? PartRow.Placed)?.unseated ?: return
    Chip(socket, Modifier.testTag(ShellTags.partRowUnseated(row.tagName)), kind = ChipKind.Warn)
}

/** What a part is under the worn garments, in Dress: the garments covering it, outermost first, through, hidden or bare. */
@Composable
private fun RowDressNote(row: PartRow, note: DressNote) {
    val text = when (note) {
        DressNote.Hidden -> stringResource(Res.string.dress_hidden)
        DressNote.Through -> stringResource(Res.string.dress_through)
        DressNote.Bare -> stringResource(Res.string.dress_bare)
        is DressNote.Under -> note.garments.joinToString(NOTE_JOIN)
    }
    BasicText(
        text,
        Modifier.testTag(ShellTags.partRowDress(row.tagName)),
        style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
        maxLines = 1,
    )
}

@Composable
private fun RowState(row: PartRow) {
    val text = when {
        row is PartRow.SideOf -> if (row.differs) stringResource(Res.string.shell_tree_differs) else null
        row is PartRow.Placed && row.drawn -> if (row.part.pair != null) stringResource(Res.string.shell_tree_pair, PAIR_SIZE) else null
        row is PartRow.Open -> openSocketCount(row.socket)
        row is PartRow.Tips -> stringResource(Res.string.parts_tips_count, row.sockets.size, openSocketCount(row.sockets.first()))
        else -> stringResource(Res.string.shell_tree_to_draw)
    } ?: return
    BasicText(
        text,
        Modifier.testTag(ShellTags.partRowState(row.tagName)),
        style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
        maxLines = 1,
    )
}
