package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.partColorInput
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.RegionRef
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_ink
import dev.chieppa.squash.uidesign.generated.color_region_delete
import dev.chieppa.squash.uidesign.generated.color_row_area
import dev.chieppa.squash.uidesign.generated.color_row_base
import dev.chieppa.squash.uidesign.generated.color_row_brush
import dev.chieppa.squash.uidesign.generated.color_row_region
import dev.chieppa.squash.uidesign.generated.color_row_stroke
import dev.chieppa.squash.uidesign.generated.color_stroke_delete
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.stringResource

private const val PATCH_SEPARATOR = ":"
private val ROW_GAP = 8.dp

/**
 * Everything painted on the selected part, under its name: its base, the template's colored blocks and areas, its regions and its brush strokes,
 * in the order they overdraw. Each row's swatch opens the palette for that item alone. A region's row picks it, for Region to edit, and holds its
 * ink; a region's and a stroke's palette can delete it.
 */
@Composable
internal fun PartPaintSection(state: AppState, session: CharacterSession, document: SquashDocument) {
    val selected by state.selectedPart.collectAsState()
    val character = document.character
    val part = selected?.let(character::part) ?: return
    val thisSide = state.thisSide && part.pair != null
    val surface = character.resolved(part).surface
    val held = session.heldColor(part.id).output.collectAsState().value
    DrawerSection(partName(part, state.templates)) {
        PaintRow(character, surface.baseSlot ?: held?.baseSlot, stringResource(Res.string.color_row_base), null, PaintTags.BASE, { PaintTags.baseSlot(it.value) }) { slot ->
            session.send(SetBaseSlot(part.id, slot, thisSide))
        }
        partSections(session, part).forEach { section ->
            val shows = surface.areaFills[section.name] ?: section.templateSlot
            PaintRow(character, shows, displayName(section.name.substringAfterLast(PATCH_SEPARATOR)), stringResource(Res.string.color_row_area), PaintTags.section(section.name), { PaintTags.sectionSlot(section.name, it.value) }) { slot ->
                session.send(SetAreaFill(part.id, section.name, slot, thisSide))
            }
        }
        surface.regions.forEach { region -> RegionRow(state, session, part, region, thisSide) }
        surface.paint.strokes.forEachIndexed { index, stroke -> StrokeRow(session, part, stroke, index, thisSide) }
    }
}

/** A row naming an item with [name] and its [kind], whose swatch shows [slot] and opens the palette to pick another for it alone. */
@Composable
private fun PaintRow(
    character: Character,
    slot: SlotName?,
    name: String,
    kind: String?,
    tag: String,
    tagOf: (SlotName) -> String,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    footer: @Composable (close: () -> Unit) -> Unit = {},
    onPick: (SlotName) -> Unit,
) {
    ListRow(selected, onClick, Modifier.testTag(PaintTags.row(tag))) {
        SlotChoice(character.palette, slot, name, tag, tagOf, onPick) { close -> footer(close) }
        Spacer(Modifier.width(ROW_GAP))
        BasicText(name, Modifier.weight(1f), style = Stretch.type.body.merge(TextStyle(color = Stretch.colors.ink)), maxLines = 1)
        trailing()
        if (kind != null) BasicText(kind, style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
    }
}

@Composable
private fun RegionRow(state: AppState, session: CharacterSession, part: PartInstance, region: Region, thisSide: Boolean) {
    val ref = RegionRef(part.id, region.id)
    val name = displayName(region.id.value)
    fun edited(change: (Region) -> Region) {
        val base = session.document.value.character.let { c -> c.part(part.id)?.let { c.editBase(it, thisSide) } }?.surface?.regions?.firstOrNull { it.id == region.id } ?: region
        session.send(SetRegion(part.id, region.id, change(base), thisSide))
    }
    PaintRow(
        session.document.value.character,
        region.slot,
        name,
        stringResource(Res.string.color_row_region),
        PaintTags.region(region.id.value),
        { PaintTags.regionSlot(region.id.value, it.value) },
        selected = state.paint.selectedRegion == ref,
        onClick = { state.paint.selectedRegion = if (state.paint.selectedRegion == ref) null else ref },
        trailing = {
            BasicText(stringResource(Res.string.color_ink), Modifier.testTag(PaintTags.regionInkLabel(region.id.value)), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
            Spacer(Modifier.width(ROW_GAP))
            Toggle(region.ink, { on -> edited { it.copy(ink = on) } }, name, Modifier.testTag(PaintTags.regionInk(region.id.value)))
            Spacer(Modifier.width(ROW_GAP))
        },
        footer = { close ->
            DeleteButton(stringResource(Res.string.color_region_delete), PaintTags.regionDelete(region.id.value)) {
                session.send(SetRegion(part.id, region.id, null, thisSide))
                if (state.paint.selectedRegion == ref) state.paint.selectedRegion = null
                close()
            }
        },
    ) { slot -> edited { it.copy(slot = slot) } }
}

@Composable
private fun StrokeRow(session: CharacterSession, part: PartInstance, stroke: BrushStroke, index: Int, thisSide: Boolean) {
    val id = stroke.id.value
    fun edited(change: (BrushStroke) -> BrushStroke?) {
        val base = session.document.value.character.let { c -> c.part(part.id)?.let { c.editBase(it, thisSide) } }?.surface?.paint?.strokes?.firstOrNull { it.id == stroke.id } ?: stroke
        session.send(SetBrushStroke(part.id, stroke.id, change(base), thisSide))
    }
    PaintRow(
        session.document.value.character,
        stroke.slot,
        stringResource(Res.string.color_row_stroke, index + 1),
        stringResource(Res.string.color_row_brush),
        PaintTags.stroke(id),
        { PaintTags.strokeSlot(id, it.value) },
        footer = { close ->
            DeleteButton(stringResource(Res.string.color_stroke_delete), PaintTags.strokeDelete(id)) {
                edited { null }
                close()
            }
        },
    ) { slot -> edited { it.copy(slot = slot) } }
}

@Composable
private fun DeleteButton(label: String, tag: String, onClick: () -> Unit) {
    Button(label, onClick, Modifier.fillMaxWidth().testTag(tag), kind = ButtonKind.Quiet, icon = SquashIcons.RemoveSlot)
}

private class NamedSection(val name: String, val templateSlot: SlotName?)

/** The selected part's template's colored blocks and areas, by the name a fill holds each under. */
private fun partSections(session: CharacterSession, part: PartInstance): List<NamedSection> {
    val input = partColorInput(session.document.value.character, part.id, session.templates)
    return input.patchSlots.map { NamedSection(it.patch, SlotName(it.slot)) } + input.areas.map { area -> NamedSection(area.name, area.color?.let(::SlotName)) }
}
