package dev.chieppa.squash.screens.dress.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.dress.DressTags
import dev.chieppa.squash.screens.dress.WardrobeShot
import dev.chieppa.squash.screens.dress.newGarment
import dev.chieppa.squash.screens.dress.newGarmentIn
import dev.chieppa.squash.screens.dress.newSlot
import dev.chieppa.squash.screens.dress.removeGarment
import dev.chieppa.squash.screens.dress.removeSlot
import dev.chieppa.squash.screens.dress.renameGarment
import dev.chieppa.squash.screens.dress.renameSlot
import dev.chieppa.squash.screens.dress.wear
import dev.chieppa.squash.screens.library.Renaming
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_new
import dev.chieppa.squash.uidesign.generated.dress_new_slot
import dev.chieppa.squash.uidesign.generated.dress_remove_slot
import dev.chieppa.squash.uidesign.generated.dress_rename_slot
import dev.chieppa.squash.uidesign.generated.garment_name_field
import dev.chieppa.squash.uidesign.generated.garment_tile_tip
import dev.chieppa.squash.uidesign.generated.library_new_garment
import dev.chieppa.squash.uidesign.generated.library_no_garments
import dev.chieppa.squash.uidesign.generated.library_no_slot
import dev.chieppa.squash.uidesign.generated.slot_name_field
import dev.chieppa.stretch.frame.ContextMenuArea
import dev.chieppa.stretch.frame.ContextMenuOpener
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.fuzzySearch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.EmptyState
import dev.chieppa.stretch.ui.PickerGrid
import dev.chieppa.stretch.ui.Tooltip
import dev.chieppa.stretch.ui.opensContextMenu
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val NEW_SLOT_PAD = 14.dp

/** A cell of a slot's grid: a garment, by its id so the cell stays the same one while the garment changes, or New in that slot. */
private sealed interface GarmentCell {
    data class Of(val garment: GarmentId) : GarmentCell
    data class New(val slot: GarmentSlotId) : GarmentCell
}

/**
 * Library's Garments half: the Outfits row, then every garment grouped by slot in the slots' order, worn ones ringed with a check, a
 * press wearing one in its slot for the shown outfit or taking a worn one off; each slot ends with New, warn-dashed while the slot is
 * empty. Garments in no slot keep a warn-edged group, and New slot ends the list. F2 renames the garment under the cursor in place
 * and Del removes it; a garment's and a slot's menus hold the rest. A search keeps the garments whose names match.
 */
@Composable
fun GarmentsLibrary(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by open.document.collectAsState()
    val query by state.library.query.collectAsState()
    val scope = rememberCoroutineScope()
    val wardrobe = document.character.wardrobe
    val thumbs = rememberWardrobeThumbs(state, open)
    Column {
        OutfitsRow(state, wardrobe, thumbs)
        if (wardrobe.garments.isEmpty()) {
            EmptyState(SquashIcons.Garment, stringResource(Res.string.library_no_garments), Modifier.testTag(DressTags.NO_GARMENTS), stringResource(Res.string.library_new_garment)) {
                scope.launch { state.newGarment()?.let { state.library.renaming.value = Renaming.OfGarment(it, new = true) } }
            }
            return@Column
        }
        val shown = if (query.isBlank()) wardrobe.garments else fuzzySearch(query.trim(), wardrobe.garments) { listOf(it.name) }
        wardrobe.slots.forEach { slot -> SlotGroup(state, wardrobe, slot, shown.filter { it.slot == slot.id }, thumbs) }
        val loose = shown.filter { it.slot == null }
        if (loose.isNotEmpty()) SlotGroup(state, wardrobe, null, loose, thumbs)
        Box(Modifier.padding(NEW_SLOT_PAD)) {
            Button(stringResource(Res.string.dress_new_slot), { scope.launch { state.newNamedSlot() } }, Modifier.testTag(DressTags.NEW_SLOT), kind = ButtonKind.Quiet, icon = SquashIcons.AddSlot)
        }
    }
}

/** One slot's group, or with no [slot] the garments in none: its garments and New, under a header whose menu holds the slot's own items. */
@Composable
private fun SlotGroup(state: AppState, wardrobe: Wardrobe, slot: GarmentSlot?, garments: List<Garment>, thumbs: WardrobeThumbs) {
    val scope = rememberCoroutineScope()
    val renaming by state.library.renaming.collectAsState()
    val cursor by state.library.garmentCursor.collectAsState()
    val outfit = wardrobe.shown?.let(wardrobe::outfit)
    val cells = garments.map<Garment, GarmentCell> { GarmentCell.Of(it.id) } + listOfNotNull(slot?.let { GarmentCell.New(it.id) })
    val title = slot?.name ?: stringResource(Res.string.library_no_slot)
    val openers = remember { mutableMapOf<GarmentId, ContextMenuOpener>() }
    val slotMenu: (@Composable ColumnScope.(() -> Unit) -> Unit)? = if (slot == null) null else { close ->
        Box(Modifier.testTag(DressTags.MENU_RENAME_SLOT)) {
            MenuItem(stringResource(Res.string.dress_rename_slot), {
                close()
                state.library.renaming.value = Renaming.OfSlot(slot.id)
            }, icon = StretchIcons.Pen, keys = RENAME_KEYS)
        }
        Box(Modifier.testTag(DressTags.MENU_NEW_SLOT)) {
            MenuItem(stringResource(Res.string.dress_new_slot), {
                close()
                scope.launch { state.newNamedSlot() }
            }, icon = SquashIcons.AddSlot)
        }
        Box(Modifier.testTag(DressTags.MENU_REMOVE_SLOT)) {
            MenuItem(stringResource(Res.string.dress_remove_slot), {
                close()
                state.removeSlot(slot.id)
            }, icon = SquashIcons.RemoveSlot, keys = REMOVE_KEYS)
        }
    }
    Box(Modifier.testTag(slot?.let { DressTags.slot(it.id) } ?: DressTags.NO_SLOT)) {
        DrawerSection(title, count = garments.size.toString(), menu = slotMenu) {
            val slotRenaming = renaming as? Renaming.OfSlot
            if (slot != null && slotRenaming?.slot == slot.id) {
                RenameField(slot.name, slotRenaming.new, stringResource(Res.string.slot_name_field), {
                    state.renameSlot(slot.id, it)
                    state.library.doneRenaming(slotRenaming)
                }, { state.library.doneRenaming(slotRenaming) }, Modifier.fillMaxWidth().testTag(DressTags.SLOT_NAME))
            }
            PickerGrid(
                items = cells,
                cursor = cells.firstOrNull { it is GarmentCell.Of && it.garment == cursor },
                onCursorChange = { cell -> if (cell is GarmentCell.Of) state.library.garmentCursor.value = cell.garment },
                pickOnClick = { true },
                onPick = { cell ->
                    when (cell) {
                        is GarmentCell.Of -> state.wear(cell.garment)
                        is GarmentCell.New -> scope.launch { state.newGarmentIn(cell.slot) }
                    }
                },
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || renaming != null) return@onPreviewKeyEvent false
                    val garment = garments.firstOrNull { it.id == cursor }
                    if (event.opensContextMenu()) {
                        val opener = garment?.let { openers[it.id] } ?: return@onPreviewKeyEvent false
                        opener.open()
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.F2 -> if (garment != null) state.library.renaming.value = Renaming.OfGarment(garment.id) else if (slot != null) state.library.renaming.value = Renaming.OfSlot(slot.id) else return@onPreviewKeyEvent false
                        Key.Delete -> if (garment != null) state.removeGarment(garment.id) else return@onPreviewKeyEvent false
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
                minCell = WARDROBE_CELL,
            ) { cell, _ ->
                when (cell) {
                    is GarmentCell.Of -> {
                        val garment = garments.firstOrNull { it.id == cell.garment } ?: return@PickerGrid
                        GarmentTile(
                            state,
                            wardrobe,
                            garment,
                            worn = outfit?.picks?.get(garment.slot) == garment.id && garment.slot != null,
                            opener = openers.getOrPut(garment.id) { ContextMenuOpener() },
                            thumbs = thumbs,
                        )
                    }
                    is GarmentCell.New -> {
                        val empty = outfit != null && outfit.picks[cell.slot] == null
                        Tooltip(stringResource(Res.string.library_new_garment)) {
                            Box(Modifier.testTag(DressTags.newIn(cell.slot))) {
                                WardrobeTile(SquashIcons.AddPart, if (empty) TileLook.NEW_EMPTY else TileLook.NEW) {
                                    TileCaption(stringResource(Res.string.dress_new), warn = empty)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Adds a new slot from the Library and opens its name for typing. */
private suspend fun AppState.newNamedSlot() {
    newSlot()?.let { library.renaming.value = Renaming.OfSlot(it, new = true) }
}

/** A garment's tile with its menu, which [opener] opens from the grid's keyboard cursor. */
@Composable
private fun GarmentTile(state: AppState, wardrobe: Wardrobe, garment: Garment, worn: Boolean, opener: ContextMenuOpener, thumbs: WardrobeThumbs) {
    val renaming by state.library.renaming.collectAsState()
    val slotName = garment.slot?.let(wardrobe::slot)?.name ?: stringResource(Res.string.library_no_slot)
    ContextMenuArea({ close -> GarmentMenu(state, wardrobe, garment, worn, close) }, Modifier.testTag(DressTags.garmentTile(garment.id)), opener) {
        Tooltip(stringResource(Res.string.garment_tile_tip, garment.name, slotName, garment.branches.size)) {
            val loose = garment.slot == null
            WardrobeTile(
                if (loose) SquashIcons.Unslotted else SquashIcons.Garment,
                when {
                    worn -> TileLook.ON
                    loose -> TileLook.LOOSE
                    else -> TileLook.PLAIN
                },
                art = if (loose) null else thumbs.art(WardrobeShot.OfGarment(garment.id)),
                artDescription = garment.name,
                artPlace = if (loose) Modifier else thumbs.place(WardrobeShot.OfGarment(garment.id)),
            ) {
                val named = renaming as? Renaming.OfGarment
                if (named?.garment == garment.id) {
                    RenameField(garment.name, named.new, stringResource(Res.string.garment_name_field), {
                        state.renameGarment(garment.id, it)
                        state.library.doneRenaming(named)
                    }, { state.library.doneRenaming(named) }, Modifier.testTag(DressTags.GARMENT_NAME))
                } else {
                    TileCaption(garment.name, warn = loose)
                }
            }
        }
    }
}
