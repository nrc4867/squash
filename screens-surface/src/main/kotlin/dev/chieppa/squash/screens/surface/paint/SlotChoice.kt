package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.shownName
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.stretch.frame.Popover
import dev.chieppa.stretch.ui.Swatch

private val SLOT_GAP = 6.dp

/**
 * A swatch showing [slot] of [palette], which opens the palette under [title] to pick another. [tagOf] names each choice for tests, and [footer] adds to
 * the popover, given what closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SlotChoice(
    palette: Palette,
    slot: SlotName,
    title: String,
    tag: String,
    tagOf: (SlotName) -> String,
    onPick: (SlotName) -> Unit,
    footer: @Composable ColumnScope.(close: () -> Unit) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Swatch(palette.colorOf(slot) ?: Color.Transparent, palette.shownName(slot), false, { open = true }, Modifier.testTag(tag))
        Popover(open, { open = false }, title) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SLOT_GAP), verticalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                palette.slots.forEach { choice ->
                    Swatch(
                        Color(choice.color.red, choice.color.green, choice.color.blue),
                        choice.shownName,
                        choice.name == slot,
                        {
                            onPick(choice.name)
                            open = false
                        },
                        Modifier.testTag(tagOf(choice.name)),
                    )
                }
            }
            footer { open = false }
        }
    }
}
