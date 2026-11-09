package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetEdgeInk
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetGarmentFillet
import dev.chieppa.squash.document.SetGarmentSlot
import dev.chieppa.squash.document.SetJoinInk
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.viewLabel
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_branch_drawing
import dev.chieppa.squash.uidesign.generated.dress_branch_offset
import dev.chieppa.squash.uidesign.generated.dress_branch_points
import dev.chieppa.squash.uidesign.generated.dress_branch_view
import dev.chieppa.squash.uidesign.generated.dress_color
import dev.chieppa.squash.uidesign.generated.dress_cover_part
import dev.chieppa.squash.uidesign.generated.dress_covers
import dev.chieppa.squash.uidesign.generated.dress_fillet
import dev.chieppa.squash.uidesign.generated.dress_ink_cuffs
import dev.chieppa.squash.uidesign.generated.dress_ink_hem
import dev.chieppa.squash.uidesign.generated.dress_ink_join
import dev.chieppa.squash.uidesign.generated.dress_join
import dev.chieppa.squash.uidesign.generated.dress_joins
import dev.chieppa.squash.uidesign.generated.dress_layer
import dev.chieppa.squash.uidesign.generated.dress_layer_number
import dev.chieppa.squash.uidesign.generated.dress_no_slot
import dev.chieppa.squash.uidesign.generated.dress_slot
import dev.chieppa.squash.uidesign.generated.shell_tree_pair
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.formatNumber
import dev.chieppa.stretch.theme.Stretch
import org.jetbrains.compose.resources.stringResource

private const val PAIR = 2

/** The views a branch is drawn in. */
private val BRANCH_VIEWS = listOf(View.SIDE, View.TOP)

private val CHIP_GAP = 6.dp

/** A part's name as the Dress inspector shows it, with ×2 on a pair. */
@Composable
internal fun dressPartName(state: AppState, part: PartInstance): String {
    val name = partName(part, state.templates)
    return if (part.pair == null) name else "$name ${stringResource(Res.string.shell_tree_pair, PAIR)}"
}

/**
 * The picked garment: its name with its slot, which a press changes, and its layer; the parts it covers, with Cover; and a row for each
 * branch in each view it is drawn in, saying how many points its outline holds, that it takes the offset alone, or that it is being drawn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GarmentSection(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val selected by state.selectedPart.collectAsState()
    val active by state.activeView.collectAsState()
    val drawing = state.curves.drawing
    DrawerSection(garment.name) {
        Row(Modifier.fillMaxWidth().testTag(DressTags.GARMENT), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP), verticalAlignment = Alignment.CenterVertically) {
            SlotChip(session, character, garment)
            Chip(stringResource(Res.string.dress_layer_number, garment.layer), Modifier.testTag(DressTags.LAYER_CHIP), description = stringResource(Res.string.dress_layer))
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP), verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            val covers = stringResource(Res.string.dress_covers)
            garment.branches.forEach { branch ->
                val part = character.part(branch.part) ?: return@forEach
                Chip(dressPartName(state, part), Modifier.testTag(DressTags.covers(part.id)), kind = ChipKind.On, description = covers, onClick = { state.selectedPart.value = part.id })
            }
            val cover = stringResource(Res.string.dress_cover_part)
            IconButton(SquashIcons.Cover, cover, { state.armTool(BuildTool.COVER) }, Modifier.testTag(DressTags.COVER), on = state.tool.collectAsState().value == BuildTool.COVER)
        }
        garment.branches.forEach { branch ->
            val part = character.part(branch.part) ?: return@forEach
            BRANCH_VIEWS.forEach { view ->
                val here = selected?.let(character::part)?.let { character.leaderOf(it).id } == part.id && active == view
                BranchRow(state, part, branch, view, here, here && drawing == view)
            }
        }
    }
}

@Composable
private fun BranchRow(state: AppState, part: PartInstance, branch: GarmentBranch, view: View, here: Boolean, drawing: Boolean) {
    val outline = branch.outlines[view]
    ListRow(here, {
        state.selectedPart.value = part.id
        state.activeView.value = view
    }, Modifier.testTag(DressTags.branch(part.id, view))) {
        Icon(if (outline != null) SquashIcons.Outline else SquashIcons.Mirror, Stretch.colors.inkMuted, size = 12.dp)
        RowText(stringResource(Res.string.dress_branch_view, dressPartName(state, part), viewLabel(view)), Modifier.weight(1f))
        val note = when {
            drawing -> stringResource(Res.string.dress_branch_drawing)
            outline != null -> stringResource(Res.string.dress_branch_points, outline.points.size)
            else -> stringResource(Res.string.dress_branch_offset)
        }
        RowText(note, subtle = true)
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

/** Joins and edges: each join's ink switch, the fillet's width and whether the hem and cuffs are inked. */
@Composable
internal fun JoinsSection(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val gesture = remember(session) { SliderGesture(session) }
    DrawerSection(stringResource(Res.string.dress_joins)) {
        val inkJoin = stringResource(Res.string.dress_ink_join)
        character.joinsOf(garment).forEach { (branch, parent) ->
            val child = character.part(branch.part) ?: return@forEach
            val over = character.part(parent) ?: return@forEach
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(SquashIcons.Join, Stretch.colors.inkMuted, size = 12.dp)
                Spacer(Modifier.width(CHIP_GAP))
                RowText(stringResource(Res.string.dress_join, partName(over, state.templates), dressPartName(state, child)), Modifier.weight(1f))
                Toggle(branch.part in garment.joins, { session.send(SetJoinInk(garment.id, branch.part, it)) }, inkJoin, Modifier.testTag(DressTags.join(branch.part)))
            }
        }
        val fillet = stringResource(Res.string.dress_fillet)
        SettingRow(fillet) {
            Slider(garment.fillet, { gesture.change(SetGarmentFillet(garment.id, it)) }, fillet, Modifier.weight(1f).testTag(DressTags.FILLET), onValueChangeFinished = gesture::done)
            Readout(formatNumber(garment.fillet))
        }
        SwitchRow(stringResource(Res.string.dress_ink_hem), garment.inkHem, Modifier.testTag(DressTags.INK_HEM)) {
            session.send(SetEdgeInk(garment.id, it, garment.inkCuffs))
        }
        SwitchRow(stringResource(Res.string.dress_ink_cuffs), garment.inkCuffs, Modifier.testTag(DressTags.INK_CUFFS)) {
            session.send(SetEdgeInk(garment.id, garment.inkHem, it))
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

