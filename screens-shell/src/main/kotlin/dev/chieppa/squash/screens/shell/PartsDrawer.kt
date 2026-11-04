package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import dev.chieppa.squash.screens.Screen
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
import dev.chieppa.stretch.ui.Tree
import dev.chieppa.stretch.ui.flattenTree
import org.jetbrains.compose.resources.stringResource

private val SWATCH_SHAPE = RoundedCornerShape(3.dp)

/** The open character's parts and empty sockets: a part selects, a socket goes to Pick a part type. */
@Composable
fun PartsDrawerContent(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by open.document.collectAsState()
    val plan = state.templates.plan(document.character.bodyPlan) ?: return
    val selected by state.selectedPart.collectAsState()
    val socket by state.pickedSocket.collectAsState()
    val screen by state.screen.collectAsState()
    var collapsed by remember { mutableStateOf(emptySet<Any>()) }

    val mirror by state.mirror.collectAsState()
    val left = stringResource(Res.string.shell_tree_left)
    val right = stringResource(Res.string.shell_tree_right)
    val roots = partTree(document, plan, state.templates, mirror) { if (it == Side.LEFT) left else right }
    val rows = flattenTree(roots) { true }.map { it.node.item }
    val current = rows.firstOrNull { it is PartRow.SideOf && screen != Screen.PICK_PART && it.part.id == selected } ?: rows.firstOrNull {
        when (it) {
            is PartRow.Placed -> screen != Screen.PICK_PART && (it.part.id == selected || document.twin(it.part)?.id == selected)
            is PartRow.Open -> screen == Screen.PICK_PART && it.socket == socket
            is PartRow.SideOf -> false
        }
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
                    is PartRow.Open -> state.chooseSocket(row.socket)
                    null -> Unit
                }
            },
            leading = { RowSwatch(it) },
            trailing = { RowState(it) },
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
    Box(
        Modifier
            .testTag(if (row is PartRow.SideOf) ShellTags.sideRow(row.tagName) else ShellTags.partRow(row.tagName))
            .size(12.dp)
            .background((row as? PartRow.Placed)?.base?.let { Color(it.red, it.green, it.blue) } ?: if (filled) c.accent else Color.Transparent, shape)
            .border(Stretch.stroke, c.lineStrong, shape),
    )
}

@Composable
private fun RowState(row: PartRow) {
    val text = when {
        row is PartRow.SideOf -> if (row.differs) stringResource(Res.string.shell_tree_differs) else null
        row is PartRow.Placed && row.drawn -> if (row.part.pair != null) stringResource(Res.string.shell_tree_pair, PAIR_SIZE) else null
        else -> stringResource(Res.string.shell_tree_to_draw)
    } ?: return
    BasicText(
        text,
        Modifier.testTag(ShellTags.partRowState(row.tagName)),
        style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
        maxLines = 1,
    )
}
