package dev.chieppa.squash.screens.dress.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.dress.DressTags
import dev.chieppa.squash.screens.dress.duplicateOutfit
import dev.chieppa.squash.screens.dress.newOutfit
import dev.chieppa.squash.screens.dress.removeOutfit
import dev.chieppa.squash.screens.dress.renameOutfit
import dev.chieppa.squash.screens.dress.showOutfit
import dev.chieppa.squash.screens.library.Renaming
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_new
import dev.chieppa.squash.uidesign.generated.dress_new_outfit
import dev.chieppa.squash.uidesign.generated.dress_outfits
import dev.chieppa.squash.uidesign.generated.outfit_duplicate
import dev.chieppa.squash.uidesign.generated.outfit_name_field
import dev.chieppa.squash.uidesign.generated.outfit_remove
import dev.chieppa.squash.uidesign.generated.outfit_rename
import dev.chieppa.stretch.frame.ContextMenuArea
import dev.chieppa.stretch.frame.ContextMenuOpener
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.PickerGrid
import dev.chieppa.stretch.ui.opensContextMenu
import dev.chieppa.stretch.ui.Tooltip
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The narrowest an outfit or garment tile gets before its grid drops a column. */
internal val WARDROBE_CELL = 80.dp

/**
 * A cell of the Outfits row: an outfit, by its id so the cell stays the same one while the outfit changes, or New. A new cell
 * would scroll its outfit into view again whenever an outfit's picks change.
 */
private sealed interface OutfitCell {
    data class Of(val outfit: OutfitId) : OutfitCell
    data object New : OutfitCell
}

/**
 * The Outfits row: every outfit, the shown one ringed, a press or the arrows showing another, then New. F2 or a double-click renames
 * the shown outfit in place, Ctrl D duplicates it and Del removes it; its context menu holds New outfit, Duplicate, Rename and Remove.
 */
@Composable
internal fun OutfitsRow(state: AppState, wardrobe: Wardrobe) {
    val scope = rememberCoroutineScope()
    val renaming by state.library.renaming.collectAsState()
    val cells = wardrobe.outfits.map<Outfit, OutfitCell> { OutfitCell.Of(it.id) } + OutfitCell.New
    val shown = wardrobe.shown?.let(wardrobe::outfit)
    fun rename(outfit: Outfit) {
        state.library.renaming.value = Renaming.OfOutfit(outfit.id)
    }
    val openers = remember { mutableMapOf<OutfitId, ContextMenuOpener>() }
    DrawerSection(stringResource(Res.string.dress_outfits)) {
        PickerGrid(
            items = cells,
            cursor = shown?.let { OutfitCell.Of(it.id) },
            onCursorChange = { cell -> if (cell is OutfitCell.Of) state.showOutfit(cell.outfit) },
            pickOnClick = { it == OutfitCell.New },
            onPick = { cell ->
                when (cell) {
                    is OutfitCell.Of -> state.library.renaming.value = Renaming.OfOutfit(cell.outfit)
                    OutfitCell.New -> scope.launch { state.newOutfit() }
                }
            },
            contentDescription = stringResource(Res.string.dress_outfits),
            modifier = Modifier.fillMaxWidth().testTag(DressTags.OUTFITS).onPreviewKeyEvent { event ->
                val outfit = shown ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown || renaming != null) return@onPreviewKeyEvent false
                when {
                    event.opensContextMenu() -> openers[outfit.id]?.open() ?: return@onPreviewKeyEvent false
                    event.key == Key.F2 -> rename(outfit)
                    event.key == Key.Delete -> state.removeOutfit(outfit.id)
                    event.key == Key.D && event.isCtrlPressed -> scope.launch { state.duplicateOutfit(outfit.id) }
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
            minCell = WARDROBE_CELL,
        ) { cell, _ ->
            when (cell) {
                is OutfitCell.Of -> {
                    val outfit = wardrobe.outfit(cell.outfit) ?: return@PickerGrid
                    val newOutfit = stringResource(Res.string.dress_new_outfit)
                    ContextMenuArea(
                        menu = { close ->
                            Box(Modifier.testTag(DressTags.MENU_NEW_OUTFIT)) {
                                MenuItem(newOutfit, {
                                    close()
                                    scope.launch { state.newOutfit() }
                                }, icon = SquashIcons.NewOutfit)
                            }
                            Box(Modifier.testTag(DressTags.MENU_DUPLICATE)) {
                                MenuItem(stringResource(Res.string.outfit_duplicate), {
                                    close()
                                    scope.launch { state.duplicateOutfit(outfit.id) }
                                }, icon = StretchIcons.Duplicate, keys = DUPLICATE_KEYS)
                            }
                            Box(Modifier.testTag(DressTags.MENU_RENAME)) {
                                MenuItem(stringResource(Res.string.outfit_rename), {
                                    close()
                                    rename(outfit)
                                }, icon = StretchIcons.Pen, keys = RENAME_KEYS)
                            }
                            Box(Modifier.testTag(DressTags.MENU_REMOVE)) {
                                MenuItem(stringResource(Res.string.outfit_remove), {
                                    close()
                                    state.removeOutfit(outfit.id)
                                }, icon = StretchIcons.Trash, keys = REMOVE_KEYS)
                            }
                        },
                        modifier = Modifier.testTag(DressTags.outfit(outfit.id)),
                        opener = openers.getOrPut(outfit.id) { ContextMenuOpener() },
                    ) {
                        WardrobeTile(
                            SquashIcons.Garment,
                            if (outfit.id == wardrobe.shown) TileLook.ON else TileLook.PLAIN,
                        ) {
                            if (renaming == Renaming.OfOutfit(outfit.id)) {
                                RenameField(outfit.name, stringResource(Res.string.outfit_name_field), {
                                    state.renameOutfit(outfit.id, it)
                                    state.library.renaming.value = null
                                }, { state.library.renaming.value = null }, Modifier.testTag(DressTags.OUTFIT_NAME))
                            } else {
                                TileCaption(outfit.name)
                            }
                        }
                    }
                }
                OutfitCell.New -> Tooltip(stringResource(Res.string.dress_new_outfit)) {
                    Box(Modifier.testTag(DressTags.NEW_OUTFIT)) {
                        WardrobeTile(SquashIcons.AddPart, TileLook.NEW) {
                            TileCaption(stringResource(Res.string.dress_new))
                        }
                    }
                }
            }
        }
    }
}

/** The keys Library's menus name beside their items. */
internal const val RENAME_KEYS = "F2"
internal const val REMOVE_KEYS = "Del"
internal const val DUPLICATE_KEYS = "Ctrl D"
internal const val WEAR_KEYS = "Enter"
