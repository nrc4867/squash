package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentEdge
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_edge_mirrored
import dev.chieppa.squash.uidesign.generated.dress_edge_on
import dev.chieppa.squash.uidesign.generated.dress_edge_open
import dev.chieppa.squash.uidesign.generated.dress_edge_remove
import dev.chieppa.squash.uidesign.generated.dress_hole_on
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.IconButton
import org.jetbrains.compose.resources.stringResource

/**
 * A row for each edge drawn for [garment]: Edge or Hole on the part it starts on, mirrored or open where it is; a press picks it, so
 * the edge tools show its points, and its cross takes it away, the garment's shape written again without it.
 */
@Composable
internal fun EdgeRows(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val picked by state.dress.edge.collectAsState()
    val body by remember(session) { session.assembly.output }.collectAsState()
    garment.edges.forEachIndexed { i, edge ->
        val part = edge.curve.points.firstOrNull()?.part?.let(character::part)?.let(character::leaderOf)
        val name = part?.let { dressPartName(state, it) }.orEmpty()
        val label = stringResource(if (edge.hole) Res.string.dress_hole_on else Res.string.dress_edge_on, name)
        ListRow(picked == i, { state.dress.edge.value = i }, Modifier.testTag(DressTags.edge(i))) {
            Icon(if (edge.hole) SquashIcons.Hole else SquashIcons.Edge, Stretch.colors.inkMuted, size = ICON)
            RowText(label, Modifier.weight(1f))
            note(edge)?.let { RowText(stringResource(it), subtle = true) }
            IconButton(StretchIcons.Close, stringResource(Res.string.dress_edge_remove, label), {
                val held = body ?: return@IconButton
                state.dress.edge.value = null
                session.send(character.removeEdgeCommand(held, garment, i))
            }, Modifier.testTag(DressTags.removeEdge(i)))
        }
    }
}

private val ICON = 12.dp

private fun note(edge: GarmentEdge) = when {
    edge.curve.mirrored -> Res.string.dress_edge_mirrored
    !edge.curve.closed -> Res.string.dress_edge_open
    else -> null
}
