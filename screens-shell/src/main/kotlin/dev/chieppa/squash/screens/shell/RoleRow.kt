package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.role_title
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.theme.StretchSizes
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

/**
 * The selected part's role as a chip, dashed while it is a guess, that opens the closed list of roles; choosing one sends [SetRole],
 * which confirms it, on both sides of a pair.
 */
@Composable
internal fun RoleRow(state: AppState, session: CharacterSession, character: Character, part: PartInstance) {
    val roles = state.templates.roles(character)
    val labels = roleLabels(roles.fourLegged)
    val role = roles.resolved(part)
    val title = stringResource(Res.string.role_title)
    var open by remember(part.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = StretchSizes.ctl), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Tooltip(title) {
                Chip(labels(role), Modifier.testTag(ShellTags.ROLE_CHIP), dashed = roles.isGuess(part), description = title, onClick = { open = true })
            }
            Menu(open, { open = false }) {
                Role.entries.forEach { choice ->
                    MenuItem(labels(choice), {
                        open = false
                        session.send(SetRole(part.id, choice))
                    }, checked = choice == role)
                }
            }
        }
    }
}
