package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.RemoveGarmentSlot
import dev.chieppa.squash.document.RenameGarmentSlot
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_none
import dev.chieppa.squash.uidesign.generated.dress_popover_title
import dev.chieppa.squash.uidesign.generated.dress_remove_slot
import dev.chieppa.squash.uidesign.generated.dress_rename_slot
import dev.chieppa.stretch.frame.Popover
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.TextField
import org.jetbrains.compose.resources.stringResource

private val POPOVER_WIDTH = 300.dp
private val PICK_TILE = 72.dp
private val GAP = 6.dp

/**
 * The popover of a slot of the shown outfit: Rename and Remove for the slot, then a tile for each garment in it and None, the one the
 * outfit wears ringed, a press wearing it. With no outfit shown, the tiles only show what the slot holds.
 */
@Composable
internal fun SlotPopover(state: AppState, session: CharacterSession, character: Character, slotId: GarmentSlotId) {
    val wardrobe = character.wardrobe
    val slot = wardrobe.slot(slotId) ?: return
    val outfit = wardrobe.shown?.let(wardrobe::outfit)
    val worn = outfit?.picks?.get(slotId)
    var renaming by remember(slotId) { mutableStateOf<String?>(null) }
    fun close() {
        renaming?.takeIf { it.isNotBlank() && it != slot.name }?.let { session.send(RenameGarmentSlot(slotId, it)) }
        state.dress.openSlot.value = null
    }
    val title = outfit?.let { stringResource(Res.string.dress_popover_title, slot.name, it.name) } ?: slot.name
    Popover(true, ::close, title, width = POPOVER_WIDTH) {
        Row(Modifier.fillMaxWidth().testTag(DressTags.POPOVER), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GAP)) {
            val text = renaming
            if (text != null) {
                TextField(
                    text,
                    { renaming = it },
                    Modifier.weight(1f).testTag(DressTags.SLOT_NAME).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            if (text.isNotBlank() && text != slot.name) session.send(RenameGarmentSlot(slotId, text))
                            renaming = null
                            true
                        } else {
                            false
                        }
                    },
                    contentDescription = stringResource(Res.string.dress_rename_slot),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(StretchIcons.Pen, stringResource(Res.string.dress_rename_slot), { renaming = if (renaming == null) slot.name else null }, Modifier.testTag(DressTags.RENAME_SLOT), on = renaming != null)
            IconButton(SquashIcons.RemoveSlot, stringResource(Res.string.dress_remove_slot), {
                state.dress.openSlot.value = null
                session.send(RemoveGarmentSlot(slotId))
            }, Modifier.testTag(DressTags.REMOVE_SLOT))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(GAP)) {
            wardrobe.garments.filter { it.slot == slotId }.forEach { garment ->
                Tile(garment.name, SquashIcons.Garment, garment.id == worn, DressTags.pick(garment.id), size = PICK_TILE) {
                    state.dress.garment.value = garment.id
                    if (outfit != null && garment.id != worn) session.send(PickGarment(outfit.id, slotId, garment.id))
                }
            }
            Tile(stringResource(Res.string.dress_none), null, outfit != null && worn == null, DressTags.PICK_NONE, dashed = true, size = PICK_TILE) {
                if (outfit != null && worn != null) session.send(PickGarment(outfit.id, slotId, null))
            }
        }
    }
}
