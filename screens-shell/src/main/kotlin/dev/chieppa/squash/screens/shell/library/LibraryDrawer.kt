package dev.chieppa.squash.screens.shell.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.newest
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.library.LibraryMode
import dev.chieppa.squash.screens.library.LibraryTab
import dev.chieppa.squash.screens.library.bestMatch
import dev.chieppa.squash.screens.library.libraryMode
import dev.chieppa.squash.screens.library.pickFromLibrary
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.library_clear_search
import dev.chieppa.squash.uidesign.generated.library_garments
import dev.chieppa.squash.uidesign.generated.library_parts
import dev.chieppa.squash.uidesign.generated.library_search
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.FieldIcon
import dev.chieppa.stretch.ui.ScrollColumn
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.TextField
import org.jetbrains.compose.resources.stringResource

private val HEADER_PAD = 8.dp

/**
 * The Library drawer: Parts or Garments, a search, and under them the half shown, which scrolls between the header and the footer.
 * Esc clears the search, then drops the socket target, then leaves the drawer; Enter with no tile under the cursor presses the
 * footer's button. [garments] is the Garments half, which the Dress stage's screens supply.
 */
@Composable
fun LibraryDrawerContent(state: AppState, garments: @Composable () -> Unit) {
    val tab by state.library.tab.collectAsState()
    val query by state.library.query.collectAsState()
    val socket = state.addingSocket.collectAsState().value
    val session by state.session.collectAsState()
    session?.document?.collectAsState()?.value
    state.selectedPart.collectAsState().value
    val requests by state.library.searchRequests.collectAsState()
    val search = remember { FocusRequester() }
    val focus = remember { GridFocus() }
    val focusManager = LocalFocusManager.current
    val mode = state.libraryMode()
    LaunchedEffect(requests) { if (requests > 0) search.requestFocus() }
    val replacing = (mode as? LibraryMode.Replace)?.part?.id
    LaunchedEffect(replacing) { if (mode is LibraryMode.Replace && state.library.cursor.value?.let(mode::lit) != true) state.library.cursor.value = mode.own }
    // A targeted socket brings the focus to the grid holding the cursor, so Enter adds and the stage keys still answer.
    LaunchedEffect(socket) { if (socket != null) focus.focus(state.library.cursor.value, skip = RECENT_KEY) }
    Column(
        Modifier
            .fillMaxSize()
            .testTag(ShellTags.LIBRARY)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        when {
                            query.isNotEmpty() -> state.library.query.value = ""
                            socket != null -> state.closeAddPart()
                            else -> focusManager.clearFocus()
                        }
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> tab == LibraryTab.PARTS && pressFooter(state, mode)
                    else -> false
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(HEADER_PAD),
            horizontalArrangement = Arrangement.spacedBy(HEADER_PAD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val parts = stringResource(Res.string.library_parts)
            val garmentsLabel = stringResource(Res.string.library_garments)
            Segmented(
                LibraryTab.entries,
                tab,
                { state.library.tab.value = it },
                { if (it == LibraryTab.PARTS) parts else garmentsLabel },
                Modifier.testTag(ShellTags.libraryTab(tab.name)),
            )
            TextField(
                query,
                { typed ->
                    state.library.query.value = typed
                    if (tab == LibraryTab.PARTS) bestMatch(state.templates.newest, typed)?.let { state.library.cursor.value = it.id }
                },
                Modifier.weight(1f).focusRequester(search).testTag(ShellTags.LIBRARY_SEARCH).onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || tab != LibraryTab.PARTS) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> focus.focus(state.library.cursor.value, skip = RECENT_KEY)
                        Key.Enter, Key.NumPadEnter -> {
                            val cursor = state.library.cursor.value
                            if (cursor != null && mode.lit(cursor)) state.pickFromLibrary(cursor)
                            cursor != null
                        }
                        else -> false
                    }
                },
                icon = StretchIcons.Search,
                contentDescription = stringResource(Res.string.library_search),
                trailing = if (query.isEmpty()) {
                    null
                } else {
                    {
                        FieldIcon(StretchIcons.Close, Modifier.testTag(ShellTags.LIBRARY_CLEAR), stringResource(Res.string.library_clear_search)) {
                            state.library.query.value = ""
                        }
                    }
                },
            )
        }
        Divider()
        ScrollColumn(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                LibraryTab.PARTS -> PartsLibrary(state, mode, focus) { search.requestFocus() }
                LibraryTab.GARMENTS -> garments()
            }
        }
        // The focus stays in Library after a press, on the grid holding the cursor, so Enter and the stage keys keep answering.
        if (tab == LibraryTab.PARTS) LibraryFooter(state, mode) { focus.focus(state.library.cursor.value, skip = RECENT_KEY) }
    }
}
