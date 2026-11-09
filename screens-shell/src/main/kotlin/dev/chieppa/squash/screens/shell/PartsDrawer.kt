package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.screens.dress.DressNote
import dev.chieppa.squash.screens.dress.dressNote
import dev.chieppa.squash.uidesign.generated.dress_hidden
import dev.chieppa.squash.uidesign.generated.dress_through
import dev.chieppa.squash.uidesign.generated.dress_bare
import androidx.compose.foundation.background
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

/** The open character's parts and empty sockets: a part selects, and a socket, shown as a row to add, opens Add part's popover on it. */
@Composable
fun PartsDrawerContent(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by open.document.collectAsState()
    val plan = state.templates.plan(document.character.bodyPlan) ?: return
    val selected by state.selectedPart.collectAsState()
    val socket by state.addingSocket.collectAsState()
    var collapsed by remember { mutableStateOf(emptySet<Any>()) }

    val mirror by state.mirror.collectAsState()
    val left = stringResource(Res.string.shell_tree_left)
    val right = stringResource(Res.string.shell_tree_right)
    val names = socketNames(remember(document) { state.openSocketsNow() })
    val roots = partTree(document, plan, state.templates, mirror, { if (it == Side.LEFT) left else right }, names)
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
    DrawerSection(plan.name, divider = false) {
        Tree(
            roots = roots,
            key = { it.key },
            label = { it.label },
            expanded = rows.map<PartRow, Any> { it.key }.toSet() - collapsed,
            onExpandedChange = { key, expanded -> collapsed = if (expanded) collapsed - key else collapsed + key },
            selection = setOfNotNull(current?.key),
            onSelectionChange = { keys ->
                val key = keys.lastOrNull { it != current?.key } ?: keys.lastOrNull()
                when (val row = rows.firstOrNull { it.key == key }) {
                    is PartRow.Placed -> state.selectPart(row.part.id)
                    is PartRow.SideOf -> state.selectPart(row.part.id)
                    is PartRow.Open -> state.offerSocket(row.socket)
                    null -> Unit
                }
            },
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
    }
}

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
        else -> stringResource(Res.string.shell_tree_to_draw)
    } ?: return
    BasicText(
        text,
        Modifier.testTag(ShellTags.partRowState(row.tagName)),
        style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
        maxLines = 1,
    )
}
