package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.shownName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_slot_add
import dev.chieppa.squash.uidesign.generated.color_slot_new
import dev.chieppa.stretch.frame.Popover
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Swatch
import org.jetbrains.compose.resources.stringResource

private val SLOT_GAP = 6.dp

/**
 * A swatch showing [slot] of [palette], or none, which opens the palette under [title] to pick another. [tagOf] names each choice for tests, and [footer] adds to
 * the popover, given what closes it. With an [editor], the popover is the reduced palette: a pick keeps it open, New color adds a slot and picks it, and the
 * picked slot's color is edited in place under the swatches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SlotChoice(
    palette: Palette,
    slot: SlotName?,
    title: String,
    tag: String,
    tagOf: (SlotName) -> String,
    onPick: (SlotName) -> Unit,
    editor: CharacterSession? = null,
    footer: @Composable ColumnScope.(close: () -> Unit) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Swatch(slot?.let(palette::colorOf) ?: Color.Transparent, slot?.let(palette::shownName) ?: title, false, { open = true }, Modifier.testTag(tag))
        Popover(open, { open = false }, title) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SLOT_GAP), verticalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                palette.slots.forEach { choice ->
                    Swatch(
                        Color(choice.color.red, choice.color.green, choice.color.blue),
                        choice.shownName,
                        choice.name == slot,
                        {
                            onPick(choice.name)
                            if (editor == null) open = false
                        },
                        Modifier.testTag(tagOf(choice.name)),
                    )
                }
            }
            if (editor != null) SlotEditor(editor, slot, onPick)
            footer { open = false }
        }
    }
}

/** New color, and the picker over [slot] once there is one. */
@Composable
private fun SlotEditor(session: CharacterSession, slot: SlotName?, onPick: (SlotName) -> Unit) {
    val document by session.document.collectAsState()
    val character = document.character
    val newName = stringResource(Res.string.color_slot_new)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Button(stringResource(Res.string.color_slot_add), {
            val name = freshSlotName(character, newName)
            session.send(SetPaletteSlot(name, SlotColor(NEW_SLOT_GREY, NEW_SLOT_GREY, NEW_SLOT_GREY)))
            onPick(name)
        }, Modifier.testTag(PaintTags.CHOICE_ADD_SLOT), kind = ButtonKind.Quiet, icon = SquashIcons.AddSlot)
    }
    val color = slot?.let(character.palette::colorOf) ?: return
    SlotPicker(session, slot, color, Modifier.fillMaxWidth().testTag(PaintTags.CHOICE_PICKER))
}
