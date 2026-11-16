package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetSocketBlend
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_part_blend
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.stringResource

/** The widest blend the slider reaches, in body units. */
private const val BLEND_MAX = 0.2f
private val BLEND_READOUT_WIDTH = 52.dp

/**
 * The blend of the smooth union at [part]'s socket, for a part in the one surface's field: the value the join uses, its own or the
 * default, and a slider that sets it on the part and its pair, one drag one undo step. Nothing for a part outside the field or with no
 * socket.
 */
@Composable
internal fun SocketBlendRow(session: CharacterSession, part: PartInstance, template: PartTemplate) {
    if (template.surface != SurfaceMethod.FIELD || part.socket == null) return
    val held by session.characterSurface.output.collectAsState()
    val field = held?.surface?.field
    val first = field?.runs?.indexOfFirst { it.first && it.partId == part.id }?.takeIf { it >= 0 }
    val value = part.blend ?: first?.let { field.field.members[it].blend } ?: 0f
    val label = stringResource(Res.string.shell_part_blend)
    var gesture by remember { mutableStateOf<History<SquashDocument>.Transaction?>(null) }
    DisposableEffect(Unit) { onDispose { gesture?.commit() } }
    SettingRow(label) {
        Slider(
            value.coerceIn(0f, BLEND_MAX),
            { blend ->
                val open = gesture ?: session.history.begin().also { gesture = it }
                open.update(SetSocketBlend(part.id, blend))
            },
            label,
            Modifier.testTag(ShellTags.SOCKET_BLEND).weight(1f),
            valueRange = 0f..BLEND_MAX,
            onValueChangeFinished = {
                gesture?.commit()
                gesture = null
            },
        )
        BasicText(
            formatNumber(value),
            Modifier.width(BLEND_READOUT_WIDTH),
            style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
            maxLines = 1,
        )
    }
}
