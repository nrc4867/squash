package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.formatNumber

private val HAIR_READOUT_WIDTH = 52.dp

/**
 * One of the hair's numbers on a slider with its readout, tagged [tag]; one drag is one undo step, the values it passes through previews
 * of a single command, the one [change] makes from the document as it stands and the value, or none.
 */
@Composable
internal fun HairSlider(
    session: CharacterSession,
    tag: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    change: (SquashDocument, Float) -> DocumentCommand?,
) {
    var gesture by remember { mutableStateOf<History<SquashDocument>.Transaction?>(null) }
    var range0 by remember { mutableStateOf(range) }
    DisposableEffect(Unit) { onDispose { gesture?.commit() } }
    if (gesture == null) range0 = range
    SettingRow(label) {
        Slider(
            value.coerceIn(range0),
            { next ->
                val command = change(session.document.value, next.coerceIn(range0)) ?: return@Slider
                val open = gesture ?: session.history.begin().also { gesture = it }
                open.update(command)
            },
            label,
            Modifier.testTag(tag).weight(1f),
            valueRange = range0,
            onValueChangeFinished = {
                gesture?.commit()
                gesture = null
            },
        )
        BasicText(
            formatNumber(value),
            Modifier.width(HAIR_READOUT_WIDTH),
            style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
            maxLines = 1,
        )
    }
}
