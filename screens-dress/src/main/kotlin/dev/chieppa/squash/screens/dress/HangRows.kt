package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.ClothHang
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.MOST_FOLD
import dev.chieppa.squash.document.MOST_DROP
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetAreaFold
import dev.chieppa.squash.document.SetAreaGather
import dev.chieppa.squash.document.SetGarmentDrop
import dev.chieppa.squash.document.SetGarmentHang
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_fold_area
import dev.chieppa.squash.uidesign.generated.dress_fold_of
import dev.chieppa.squash.uidesign.generated.dress_fold_remove
import dev.chieppa.squash.uidesign.generated.dress_drop_of
import dev.chieppa.squash.uidesign.generated.dress_drop_part
import dev.chieppa.squash.uidesign.generated.dress_drop_remove
import dev.chieppa.squash.uidesign.generated.dress_gather_area
import dev.chieppa.squash.uidesign.generated.dress_gather_of
import dev.chieppa.squash.uidesign.generated.dress_gather_remove
import dev.chieppa.squash.uidesign.generated.dress_hang
import dev.chieppa.squash.uidesign.generated.dress_hang_flare
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The furthest a hang's slider lets the cloth stand off past its ease, in body units. */
private const val MOST_HANG = 0.08f

/** The loosest a gather's slider reaches, in body units. */
private const val MOST_GATHER = 0.03f

/** Where a newly dropped seam sits along its part, in u. */
private const val NEW_DROP = 0.15f

/** A new gather's ease, snug, in body units. */
private const val NEW_GATHER = 0.004f

/** A new fold's thickness, in body units: a rolled cuff's. */
private const val NEW_FOLD = 0.006f

private const val PERCENT = 100f

/** Thousandths of a body unit, as a hang or gather reads out. */
private const val THOUSANDTHS = 1000f

/**
 * Hang: how far the cloth may stand off below the highest place it covers on an upright part, 0 to follow the body, with Flare once it
 * hangs; a row for each dropped seam, its place along its part, with its cross, and Drop a seam; and a row for each area that gathers the
 * cloth in, its ease, with its cross, and Gather an area; a row for each area folded back over the cloth, and Fold an area; and the
 * sheer and net areas ([SeeThroughRows]).
 */
@Composable
internal fun HangRows(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val gesture = remember(session) { SliderGesture(session) }
    val hangLabel = stringResource(Res.string.dress_hang)
    val limit = garment.hang?.limit ?: 0f
    SettingRow(hangLabel) {
        Slider(limit, { gesture.change(SetGarmentHang(garment.id, if (it <= 0f) null else ClothHang(it, garment.hang?.flare ?: 0f))) }, hangLabel, Modifier.weight(1f).testTag(DressTags.HANG), 0f..MOST_HANG, gesture::done)
        Readout((limit * THOUSANDTHS).roundToInt().toString())
    }
    garment.hang?.let { hang ->
        val flare = stringResource(Res.string.dress_hang_flare)
        SettingRow(flare) {
            Slider(hang.flare, { gesture.change(SetGarmentHang(garment.id, hang.copy(flare = it))) }, flare, Modifier.weight(1f).testTag(DressTags.HANG_FLARE), 0f..ClothHang.MOST_FLARE, gesture::done)
            Readout(stringResource(Res.string.edit_percent, (hang.flare * PERCENT).roundToInt()))
        }
    }
    for ((part, drop) in garment.drops) {
        val name = character.part(part)?.let { dressPartName(state, it) } ?: part.value
        val label = stringResource(Res.string.dress_drop_of, name)
        SettingRow(label) {
            Slider(drop, { gesture.change(SetGarmentDrop(garment.id, part, maxOf(it, 0.01f))) }, label, Modifier.weight(1f).testTag(DressTags.drop(part)), 0f..MOST_DROP, gesture::done)
            Readout(stringResource(Res.string.edit_percent, (drop * PERCENT).roundToInt()))
            IconButton(StretchIcons.Close, stringResource(Res.string.dress_drop_remove, name), { session.send(SetGarmentDrop(garment.id, part, null)) }, Modifier.testTag(DressTags.removeDrop(part)))
        }
    }
    val undropped = garment.branches.map { it.part }.filter { part -> part !in garment.drops && character.part(part)?.socket?.parent?.let { garment.branch(it) } != null }
    if (undropped.isNotEmpty()) DropAPart(state, session, character, garment, undropped)
    for (area in garment.areas) {
        val gather = area.gather ?: continue
        val name = area.name.ifBlank { area.id.value }
        val label = stringResource(Res.string.dress_gather_of, name)
        SettingRow(label) {
            Slider(gather, { gesture.change(SetAreaGather(garment.id, area.id, it)) }, label, Modifier.weight(1f).testTag(DressTags.gather(area.id)), 0f..MOST_GATHER, gesture::done)
            Readout((gather * THOUSANDTHS).roundToInt().toString())
            IconButton(StretchIcons.Close, stringResource(Res.string.dress_gather_remove, name), { session.send(SetAreaGather(garment.id, area.id, null)) }, Modifier.testTag(DressTags.removeGather(area.id)))
        }
    }
    val ungathered = garment.areas.filter { it.gather == null }
    if (ungathered.isNotEmpty()) AreaMenu(stringResource(Res.string.dress_gather_area), StretchIcons.Plus, DressTags.GATHER_AREA, ungathered) { session.send(SetAreaGather(garment.id, it.id, NEW_GATHER)) }
    FoldRows(session, garment, gesture)
    SeeThroughRows(session, garment, gesture)
}

/** A row for each area folded back over the cloth, its thickness, with its cross, and Fold an area. */
@Composable
private fun FoldRows(session: CharacterSession, garment: Garment, gesture: SliderGesture) {
    for (area in garment.areas) {
        val fold = area.fold ?: continue
        val name = area.name.ifBlank { area.id.value }
        val label = stringResource(Res.string.dress_fold_of, name)
        SettingRow(label) {
            Slider(fold, { gesture.change(SetAreaFold(garment.id, area.id, it)) }, label, Modifier.weight(1f).testTag(DressTags.fold(area.id)), 0f..MOST_FOLD, gesture::done)
            Readout((fold * THOUSANDTHS).roundToInt().toString())
            IconButton(StretchIcons.Close, stringResource(Res.string.dress_fold_remove, name), { session.send(SetAreaFold(garment.id, area.id, null)) }, Modifier.testTag(DressTags.removeFold(area.id)))
        }
    }
    val flat = garment.areas.filter { it.fold == null }
    if (flat.isNotEmpty()) AreaMenu(stringResource(Res.string.dress_fold_area), SquashIcons.Fold, DressTags.FOLD_AREA, flat) { session.send(SetAreaFold(garment.id, it.id, NEW_FOLD)) }
}

/** A quiet [label] button with [icon] opening a menu of [areas], [pick] sent for the one pressed. */
@Composable
internal fun AreaMenu(label: String, icon: ImageVector, tag: String, areas: List<GarmentArea>, pick: (GarmentArea) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(label, { open = true }, Modifier.testTag(tag), kind = ButtonKind.Quiet, icon = icon)
        Menu(open, { open = false }) {
            for (area in areas) {
                MenuItem(area.name.ifBlank { area.id.value }, {
                    open = false
                    pick(area)
                })
            }
        }
    }
}

@Composable
private fun DropAPart(state: AppState, session: CharacterSession, character: Character, garment: Garment, parts: List<PartId>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(stringResource(Res.string.dress_drop_part), { open = true }, Modifier.testTag(DressTags.DROP_PART), kind = ButtonKind.Quiet, icon = StretchIcons.Plus)
        Menu(open, { open = false }) {
            for (part in parts) {
                val name = character.part(part)?.let { dressPartName(state, it) } ?: part.value
                MenuItem(name, {
                    open = false
                    session.send(SetGarmentDrop(garment.id, part, NEW_DROP))
                })
            }
        }
    }
}
