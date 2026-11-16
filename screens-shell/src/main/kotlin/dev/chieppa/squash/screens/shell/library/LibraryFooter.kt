package dev.chieppa.squash.screens.shell.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.addPart
import dev.chieppa.squash.screens.library.LibraryMode
import dev.chieppa.squash.screens.library.pickFromLibrary
import dev.chieppa.squash.screens.openSocketsNow
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.openSocketCount
import dev.chieppa.squash.screens.shell.socketNames
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.add_part_add
import dev.chieppa.squash.uidesign.generated.library_replace
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.Label
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val FOOTER_PAD = 10.dp
private val ENTER_KEYS = listOf("Enter")

/** The type Add adds: the one under the cursor when the socket takes it, else the first the socket takes. */
private fun AppState.addType(mode: LibraryMode.Add): TemplateId = library.cursor.value?.takeIf(mode::lit) ?: mode.socket.takes.first()

/** Presses the footer's button for [mode], as Enter with no tile under the cursor does; false when there is none. */
internal fun pressFooter(state: AppState, mode: LibraryMode): Boolean = when (mode) {
    is LibraryMode.Add -> {
        state.addPart(state.addType(mode))
        true
    }
    is LibraryMode.Replace -> {
        state.library.cursor.value?.takeIf { it in mode.choices }?.let(state::pickFromLibrary)
        true
    }
    LibraryMode.Browse -> false
}

/** Library's footer: the socket targeted and Add, or the part selected and Replace; nothing at rest. [afterPress] follows a press. */
@Composable
internal fun LibraryFooter(state: AppState, mode: LibraryMode, afterPress: () -> Unit) {
    if (mode == LibraryMode.Browse) return
    state.library.cursor.collectAsState().value
    val c = Stretch.colors
    val (name, count, text, icon, enabled) = when (mode) {
        is LibraryMode.Add -> {
            val type = state.templates.template(state.addType(mode))?.name.orEmpty()
            FooterLook(socketNames(state.openSocketsNow())(mode.socket.name), openSocketCount(mode.socket), stringResource(Res.string.add_part_add, type.lowercase()), SquashIcons.AddPart, true)
        }
        is LibraryMode.Replace -> {
            val part = partName(mode.part, state.templates)
            val cursor = state.library.cursor.value
            FooterLook(part, null, stringResource(Res.string.library_replace, part.lowercase()), SquashIcons.ReplacePart, cursor != null && cursor in mode.choices)
        }
        LibraryMode.Browse -> return
    }
    Divider()
    Row(
        Modifier.fillMaxWidth().padding(FOOTER_PAD),
        horizontalArrangement = Arrangement.spacedBy(FOOTER_PAD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(name, Stretch.type.body, c.ink, Modifier.weight(1f))
        if (count != null) Label(count, Stretch.type.value, c.inkMuted)
        Tooltip(text, ENTER_KEYS) {
            Button(text, {
                pressFooter(state, mode)
                afterPress()
            }, Modifier.testTag(ShellTags.LIBRARY_PICK), kind = ButtonKind.Primary, icon = icon, enabled = enabled)
        }
    }
}

private data class FooterLook(
    val name: String,
    val count: String?,
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean,
)
