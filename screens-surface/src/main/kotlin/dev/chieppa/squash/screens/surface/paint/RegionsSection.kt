package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.shownName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.RegionRef
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_ink
import dev.chieppa.squash.uidesign.generated.color_region_add
import dev.chieppa.squash.uidesign.generated.color_region_delete
import dev.chieppa.squash.uidesign.generated.color_region_edit
import dev.chieppa.squash.uidesign.generated.color_section_regions
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.Popover
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Swatch
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val SLOT_GAP = 6.dp
private val INK_LABEL_GAP = 8.dp
private val EDIT_BUTTON_SIZE = 28.dp

/** Every border on the character, a mirrored pair's once, and the way to draw a new one. A row's swatch recolors its region, and its pen edits its points. */
@Composable
internal fun RegionsSection(state: AppState, session: CharacterSession, character: Character) {
    val tool by state.tool.collectAsState()
    val rows = character.parts.flatMap { part ->
        if (part.pair != null && part.side != Side.LEFT) emptyList() else character.resolved(part).surface.regions.map { part to it }
    }
    DrawerSection(stringResource(Res.string.color_section_regions)) {
        rows.forEach { (part, region) -> RegionRow(state, session, part, region) }
        val drawingNew = tool == BuildTool.REGION_BORDER && state.paint.selectedRegion == null
        Button(
            stringResource(Res.string.color_region_add),
            {
                state.paint.selectedRegion = null
                if (drawingNew) state.armTool(BuildTool.REGION_BORDER) else state.tool.value = BuildTool.REGION_BORDER
            },
            Modifier.fillMaxWidth().testTag(PaintTags.NEW_REGION),
            kind = if (drawingNew) ButtonKind.Primary else ButtonKind.Default,
            icon = SquashIcons.RegionBorder,
        )
    }
}

@Composable
private fun RegionRow(state: AppState, session: CharacterSession, part: PartInstance, region: Region) {
    val name = displayName(region.id.value)
    val ref = RegionRef(part.id, region.id)
    val thisSide = state.thisSide && part.pair != null
    fun edited(change: (Region) -> Region) {
        val base = session.document.value.character.let { c -> c.part(part.id)?.let { c.editBase(it, thisSide) } }?.surface?.regions?.firstOrNull { it.id == region.id } ?: region
        session.send(SetRegion(part.id, region.id, change(base), thisSide))
    }
    SettingRow(name) {
        RegionSwatch(state, session, ref, region, name, thisSide) { slot -> edited { it.copy(slot = slot) } }
        Spacer(Modifier.weight(1f))
        EditRegionButton(state, ref)
        Spacer(Modifier.width(INK_LABEL_GAP))
        BasicText(stringResource(Res.string.color_ink), Modifier.testTag(PaintTags.regionInkLabel(region.id.value)), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
        Spacer(Modifier.width(INK_LABEL_GAP))
        Toggle(region.ink, { on -> edited { it.copy(ink = on) } }, name, Modifier.testTag(PaintTags.regionInk(region.id.value)))
    }
}

/** Edits the points of the region [ref], or stops if it is being edited. */
@Composable
private fun EditRegionButton(state: AppState, ref: RegionRef) {
    val tool by state.tool.collectAsState()
    val editing = tool == BuildTool.REGION_BORDER && state.paint.selectedRegion == ref
    val label = stringResource(Res.string.color_region_edit)
    Tooltip(label, emptyList()) {
        ToolButton(
            SquashIcons.DrawOutline,
            label,
            editing,
            {
                if (editing) {
                    state.armTool(BuildTool.REGION_BORDER)
                } else {
                    state.tool.value = BuildTool.REGION_BORDER
                    state.paint.selectedRegion = ref
                }
            },
            Modifier.testTag(PaintTags.regionEdit(ref.id.value)),
            size = EDIT_BUTTON_SIZE,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionSwatch(
    state: AppState,
    session: CharacterSession,
    ref: RegionRef,
    region: Region,
    name: String,
    thisSide: Boolean,
    onSlot: (dev.chieppa.squash.document.SlotName) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val palette = session.document.value.character.palette
    Box {
        Swatch(
            palette.colorOf(region.slot) ?: Color.Transparent,
            palette.shownName(region.slot),
            false,
            { open = true },
            Modifier.testTag(PaintTags.region(region.id.value)),
        )
        Popover(open, { open = false }, name) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SLOT_GAP), verticalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                palette.slots.forEach { slot ->
                    Swatch(
                        Color(slot.color.red, slot.color.green, slot.color.blue),
                        slot.shownName,
                        slot.name == region.slot,
                        {
                            onSlot(slot.name)
                            open = false
                        },
                        Modifier.testTag(PaintTags.regionSlot(region.id.value, slot.name.value)),
                    )
                }
            }
            Button(
                stringResource(Res.string.color_region_delete),
                {
                    session.send(SetRegion(ref.part, region.id, null, thisSide))
                    if (state.paint.selectedRegion == ref) state.paint.selectedRegion = null
                    open = false
                },
                Modifier.fillMaxWidth().testTag(PaintTags.regionDelete(region.id.value)),
                kind = ButtonKind.Quiet,
                icon = SquashIcons.RemoveSlot,
            )
        }
    }
}
