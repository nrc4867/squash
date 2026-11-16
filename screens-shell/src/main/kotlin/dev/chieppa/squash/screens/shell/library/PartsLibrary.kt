package dev.chieppa.squash.screens.shell.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.newest
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.library.LibraryMode
import dev.chieppa.squash.screens.library.TemplateDrag
import dev.chieppa.squash.screens.library.libraryShelves
import dev.chieppa.squash.screens.library.pickFromLibrary
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.library_clear_search
import dev.chieppa.squash.uidesign.generated.library_no_matches
import dev.chieppa.squash.uidesign.generated.library_recent
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.dragSource
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.EmptyState
import dev.chieppa.stretch.ui.PickerGrid
import dev.chieppa.stretch.ui.PickerGridGap
import org.jetbrains.compose.resources.stringResource

/** The narrowest a Library tile gets before the grid drops a column. */
private val MIN_CELL = 80.dp

/** The inset of Library's grids from the drawer's sides, as a drawer section lays out its content. */
private val GRID_INSET = 14.dp

/** The key of the Recent grid in [GridFocus]. */
internal const val RECENT_KEY = "recent"

/** One grid of Library's Parts half: Recent, or a group. */
private class PartsGrid(val key: String, val items: List<PartTemplate>)

/**
 * Library's Parts half: Recent, then the groups by body area, each a grid with a keyboard cursor. A press puts the cursor on a tile,
 * and Enter or a double-click picks it, which adds it on the socket targeted or gives it to the part selected; what that pick cannot
 * take is faint and skipped. A lit tile drags onto a socket's dot. Up from the first grid goes to [onLeaveTop], the search field.
 */
@Composable
internal fun PartsLibrary(state: AppState, mode: LibraryMode, focus: GridFocus, onLeaveTop: () -> Unit) {
    val session by state.session.collectAsState()
    val document = session?.document?.collectAsState()?.value
    val query by state.library.query.collectAsState()
    val cursor by state.library.cursor.collectAsState()
    val recentIds by state.library.recent.collectAsState()
    val folds by state.library.folds.collectAsState()
    val character = document?.character?.id
    val folded = character?.let { folds[it] }.orEmpty()
    val all = state.templates.newest
    val first: (PartTemplate) -> Boolean = when (mode) {
        is LibraryMode.Add -> { template -> mode.lit(template.id) }
        is LibraryMode.Replace -> { template -> template.id == mode.own }
        LibraryMode.Browse -> { _ -> false }
    }
    val shelves = libraryShelves(all, query, first)
    if (shelves.isEmpty()) {
        focus.shown = emptyList()
        EmptyState(
            StretchIcons.Search,
            stringResource(Res.string.library_no_matches),
            Modifier.testTag(ShellTags.LIBRARY_NO_MATCHES),
            stringResource(Res.string.library_clear_search),
        ) { state.library.query.value = "" }
        return
    }
    val recent = if (query.isBlank()) recentIds.mapNotNull { id -> all.firstOrNull { it.id == id } } else emptyList()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = maxOf(1, ((maxWidth - GRID_INSET * 2 + PickerGridGap) / (MIN_CELL + PickerGridGap)).toInt())
        val grids = buildList {
            if (recent.isNotEmpty()) add(PartsGrid(RECENT_KEY, recent.take(columns)))
            shelves.filter { it.group !in folded }.forEach { add(PartsGrid(it.group.name, it.templates)) }
        }
        focus.shown = grids.map { grid -> grid.key to grid.items.map { it.id } }
        val active = focus.holding(cursor, skip = RECENT_KEY)

        fun leave(from: Int, down: Boolean) {
            val next = grids.getOrNull(if (down) from + 1 else from - 1)
            if (next == null) {
                if (!down) onLeaveTop()
                return
            }
            val target = (if (down) next.items else next.items.asReversed()).firstOrNull { mode.lit(it.id) } ?: return
            focus.held = next.key
            state.library.cursor.value = target.id
            focus.of(next.key).requestFocus()
        }

        @Composable
        fun Grid(index: Int) {
            val grid = grids[index]
            PickerGrid(
                items = grid.items,
                cursor = if (grid.key == active) grid.items.firstOrNull { it.id == cursor } else null,
                onCursorChange = { picked ->
                    focus.held = grid.key
                    state.library.cursor.value = picked?.id
                },
                onPick = { state.pickFromLibrary(it.id) },
                contentDescription = grid.key,
                modifier = Modifier.fillMaxWidth().focusRequester(focus.of(grid.key)).testTag(ShellTags.libraryGroup(grid.key)),
                minCell = MIN_CELL,
                enabled = { mode.lit(it.id) },
                onLeaveTop = { leave(index, down = false) },
                onLeaveBottom = { leave(index, down = true) },
            ) { template, _ ->
                val lit = mode.lit(template.id)
                Box(
                    Modifier.testTag(if (grid.key == RECENT_KEY) ShellTags.recentTile(template.id.value) else ShellTags.libraryTile(template.id.value)).dragSource(
                        item = {
                            if (lit) {
                                if (state.tool.value != BuildTool.ADD_PART) state.tool.value = BuildTool.ADD_PART
                                TemplateDrag(template.id)
                            } else {
                                null
                            }
                        },
                        preview = { LiftedPartTile(template) },
                    ),
                ) { PartTile(template, lit) }
            }
        }

        Column {
            if (grids.firstOrNull()?.key == RECENT_KEY) {
                DrawerSection(stringResource(Res.string.library_recent)) {
                    Box(Modifier.testTag(ShellTags.LIBRARY_RECENT)) { Grid(0) }
                }
            }
            shelves.forEach { shelf ->
                key(shelf.group) {
                val index = grids.indexOfFirst { it.key == shelf.group.name }
                DrawerSection(
                    stringResource(shelf.group.label),
                    collapsed = shelf.group in folded,
                    count = shelf.templates.size.toString(),
                    onCollapsedChange = { character?.let { state.library.toggleFold(it, shelf.group) } },
                ) {
                    if (index >= 0) Grid(index)
                }
                }
            }
        }
    }
}
