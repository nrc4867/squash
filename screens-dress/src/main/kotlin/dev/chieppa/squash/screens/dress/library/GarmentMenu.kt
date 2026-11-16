package dev.chieppa.squash.screens.dress.library

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.dress.DressTags
import dev.chieppa.squash.screens.dress.moveGarment
import dev.chieppa.squash.screens.dress.removeGarment
import dev.chieppa.squash.screens.dress.wear
import dev.chieppa.squash.screens.library.Renaming
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.garment_move_to_slot
import dev.chieppa.squash.uidesign.generated.garment_remove
import dev.chieppa.squash.uidesign.generated.garment_rename
import dev.chieppa.squash.uidesign.generated.garment_take_off
import dev.chieppa.squash.uidesign.generated.garment_wear
import dev.chieppa.squash.uidesign.generated.library_no_slot
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.SubMenuItem
import dev.chieppa.stretch.theme.StretchIcons
import org.jetbrains.compose.resources.stringResource

/**
 * A garment's menu: Wear, or Take off when worn, faint while it has no slot or no outfit shows; Rename in place; Move to slot, its
 * slot checked; Remove, which takes it out of every outfit with the parts it owns.
 */
@Composable
internal fun GarmentMenu(state: AppState, wardrobe: Wardrobe, garment: Garment, worn: Boolean, close: () -> Unit) {
    Box(Modifier.testTag(DressTags.MENU_WEAR)) {
        MenuItem(
            stringResource(if (worn) Res.string.garment_take_off else Res.string.garment_wear),
            {
                close()
                state.wear(garment.id)
            },
            icon = SquashIcons.Wear,
            keys = WEAR_KEYS,
            enabled = garment.slot != null && wardrobe.shown != null,
        )
    }
    Box(Modifier.testTag(DressTags.MENU_RENAME)) {
        MenuItem(stringResource(Res.string.garment_rename), {
            close()
            state.library.renaming.value = Renaming.OfGarment(garment.id)
        }, icon = StretchIcons.Pen, keys = RENAME_KEYS)
    }
    Box(Modifier.testTag(DressTags.MENU_MOVE)) {
        SubMenuItem(stringResource(Res.string.garment_move_to_slot), icon = SquashIcons.Slots) {
            wardrobe.slots.forEach { slot ->
                Box(Modifier.testTag(DressTags.moveTo(slot.id))) {
                    MenuItem(slot.name, {
                        close()
                        state.moveGarment(garment.id, slot.id)
                    }, checked = garment.slot == slot.id)
                }
            }
            Box(Modifier.testTag(DressTags.moveTo(null))) {
                MenuItem(stringResource(Res.string.library_no_slot), {
                    close()
                    state.moveGarment(garment.id, null)
                }, checked = garment.slot == null)
            }
        }
    }
    Box(Modifier.testTag(DressTags.MENU_REMOVE)) {
        MenuItem(stringResource(Res.string.garment_remove), {
            close()
            state.removeGarment(garment.id)
        }, icon = StretchIcons.Trash, keys = REMOVE_KEYS)
    }
}
