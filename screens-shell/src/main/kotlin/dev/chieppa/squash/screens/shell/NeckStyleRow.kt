package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.NeckStyle
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetNeckStyle
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_neck_between
import dev.chieppa.squash.uidesign.generated.shell_neck_blended
import dev.chieppa.squash.uidesign.generated.shell_neck_defined
import dev.chieppa.squash.uidesign.generated.shell_neck_style
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.stringResource

/** The character's neck style, on a head that sits on a socket: how the head meets its neck, one command a choice. */
@Composable
internal fun NeckStyleRow(session: CharacterSession, part: PartInstance) {
    if (part.role != Role.HEAD || part.socket == null) return
    val document by session.document.collectAsState()
    val style = document.character.neck
    val labels = mapOf(
        NeckStyle.BLENDED to stringResource(Res.string.shell_neck_blended),
        NeckStyle.BETWEEN to stringResource(Res.string.shell_neck_between),
        NeckStyle.DEFINED to stringResource(Res.string.shell_neck_defined),
    )
    SettingRow(stringResource(Res.string.shell_neck_style)) {
        Segmented(
            NeckStyle.entries.toList(), style,
            { if (it != style) session.send(SetNeckStyle(it)) },
            labels::getValue, Modifier.fillMaxWidth().testTag(ShellTags.NECK_STYLE), fill = true,
        )
    }
}
