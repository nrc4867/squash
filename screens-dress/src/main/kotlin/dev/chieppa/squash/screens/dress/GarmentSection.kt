package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetHemInk
import dev.chieppa.squash.document.SetGarmentSlot
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_color
import dev.chieppa.squash.uidesign.generated.dress_ink_hem
import dev.chieppa.squash.uidesign.generated.dress_layer
import dev.chieppa.squash.uidesign.generated.dress_layer_number
import dev.chieppa.squash.uidesign.generated.dress_no_slot
import dev.chieppa.squash.uidesign.generated.dress_slot
import dev.chieppa.squash.uidesign.generated.shell_tree_pair
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.ui.Chip
import org.jetbrains.compose.resources.stringResource

private const val PAIR = 2

internal val CHIP_GAP = 6.dp

/** A part's name as the Dress inspector shows it, with ×2 on a pair. */
@Composable
internal fun dressPartName(state: AppState, part: PartInstance): String {
    val name = partName(part, state.templates)
    return if (part.pair == null) name else "$name ${stringResource(Res.string.shell_tree_pair, PAIR)}"
}

/**
 * The picked garment: its slot, which a press changes, and its layer; its fit and the parts fitted apart from it; its painted areas,
 * their edges and its holes; its hem ink, which inks every edge, seams too; and its ink weight and hem roll.
 */
@Composable
internal fun GarmentSection(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    DrawerSection(garment.name) {
        Row(Modifier.fillMaxWidth().testTag(DressTags.GARMENT), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP), verticalAlignment = Alignment.CenterVertically) {
            SlotChip(session, character, garment)
            Chip(stringResource(Res.string.dress_layer_number, garment.layer), Modifier.testTag(DressTags.LAYER_CHIP), description = stringResource(Res.string.dress_layer))
        }
        FitRows(state, session, character, garment)
        AreaRows(state, session, character, garment)
        SwitchRow(stringResource(Res.string.dress_ink_hem), garment.inkHem, Modifier.testTag(DressTags.INK_HEM)) {
            session.send(SetHemInk(garment.id, it))
        }
        ClothInkRows(session, garment)
    }
}

/** The garment's slot as a chip, which opens the wardrobe's slots to move it to another, or to none. */
@Composable
private fun SlotChip(session: CharacterSession, character: Character, garment: Garment) {
    var open by remember { mutableStateOf(false) }
    val wardrobe = character.wardrobe
    val none = stringResource(Res.string.dress_no_slot)
    Box {
        Chip(
            garment.slot?.let(wardrobe::slot)?.name ?: none,
            Modifier.testTag(DressTags.SLOT_CHIP),
            dashed = garment.slot == null,
            description = stringResource(Res.string.dress_slot),
            onClick = { open = true },
        )
        Menu(open, { open = false }) {
            wardrobe.slots.forEach { slot ->
                MenuItem(slot.name, {
                    open = false
                    if (slot.id != garment.slot) session.send(SetGarmentSlot(garment.id, slot.id))
                }, checked = slot.id == garment.slot)
            }
            MenuItem(none, {
                open = false
                if (garment.slot != null) session.send(SetGarmentSlot(garment.id, null))
            }, checked = garment.slot == null)
        }
    }
}

/** The garment's color: its palette slot's swatch, which opens the reduced palette. */
@Composable
internal fun GarmentColorSection(session: CharacterSession, character: Character, garment: Garment) {
    val title = stringResource(Res.string.dress_color)
    DrawerSection(title) {
        SlotChoice(
            character.palette,
            garment.color,
            title,
            DressTags.COLOR,
            { DressTags.COLOR + "." + it.value },
            { session.send(SetGarmentColor(garment.id, it)) },
            editor = session,
        )
    }
}

