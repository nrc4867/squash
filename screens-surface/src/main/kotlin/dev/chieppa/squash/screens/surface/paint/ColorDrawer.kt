package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.partColorInput
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.render.FarSideDarkening
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.FillReach
import dev.chieppa.squash.screens.edit.RegionRef
import dev.chieppa.squash.screens.sheet.chooseFarSideDarker
import dev.chieppa.squash.screens.sheet.chooseShading
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.sheet.rememberObjectShading
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_far_amount
import dev.chieppa.squash.uidesign.generated.color_far_parts
import dev.chieppa.squash.uidesign.generated.color_fill_area
import dev.chieppa.squash.uidesign.generated.color_fill_part
import dev.chieppa.squash.uidesign.generated.color_ink
import dev.chieppa.squash.uidesign.generated.color_no_ink
import dev.chieppa.squash.uidesign.generated.color_picker_eyedropper
import dev.chieppa.squash.uidesign.generated.color_picker_hex
import dev.chieppa.squash.uidesign.generated.color_picker_hue
import dev.chieppa.squash.uidesign.generated.color_picker_invalid_hex
import dev.chieppa.squash.uidesign.generated.color_picker_square
import dev.chieppa.squash.uidesign.generated.color_section_paint
import dev.chieppa.squash.uidesign.generated.color_section_regions
import dev.chieppa.squash.uidesign.generated.color_section_sections
import dev.chieppa.squash.uidesign.generated.color_section_shading
import dev.chieppa.squash.uidesign.generated.color_shading_flat
import dev.chieppa.squash.uidesign.generated.color_shading_two_tone
import dev.chieppa.squash.uidesign.generated.color_size
import dev.chieppa.squash.uidesign.generated.color_slot_add
import dev.chieppa.squash.uidesign.generated.color_slot_new
import dev.chieppa.squash.uidesign.generated.color_slot_remove
import dev.chieppa.squash.uidesign.generated.color_softness
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.ColorPicker
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Swatch
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f
private const val MAX_BRUSH_RADIUS = 0.3f
private const val MIN_BRUSH_RADIUS = 0.005f
private const val PATCH_SEPARATOR = ":"
private const val NEW_SLOT_GREY = 0.6f
private val READOUT_WIDTH = 44.dp

/** The Color drawer: the palette and the tool's controls, the selected part's sections, every border's ink, and shading. */
@Composable
fun ColorDrawerContent(state: AppState, session: CharacterSession) {
    val document by session.document.collectAsState()
    val tool by state.tool.collectAsState()
    PaintSection(state, session, document, tool)
    SectionsSection(state, session, document)
    RegionsSection(state, session, document.character)
    ShadingSection(state)
}

@Composable
private fun PaintSection(state: AppState, session: CharacterSession, document: SquashDocument, tool: BuildTool) {
    val palette = document.character.palette
    val current = state.paint.slotIn(palette)
    val newName = stringResource(Res.string.color_slot_new)
    DrawerSection(stringResource(Res.string.color_section_paint)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            palette.slots.forEach { slot ->
                Swatch(Color(slot.color.red, slot.color.green, slot.color.blue), displayName(slot.name.value), slot.name == current, { state.paint.slot = slot.name }, Modifier.testTag(PaintTags.slot(slot.name.value)))
            }
            IconButton(SquashIcons.AddSlot, stringResource(Res.string.color_slot_add), {
                val name = freshSlotName(document.character, newName)
                session.send(SetPaletteSlot(name, SlotColor(NEW_SLOT_GREY, NEW_SLOT_GREY, NEW_SLOT_GREY)))
                state.paint.slot = name
            }, Modifier.testTag(PaintTags.ADD_SLOT))
        }
        if (current != null) {
            SlotPicker(session, current, palette.colorOf(current) ?: Color.Transparent)
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                IconButton(SquashIcons.RemoveSlot, stringResource(Res.string.color_slot_remove), { session.send(SetPaletteSlot(current, null)) }, Modifier.testTag(PaintTags.REMOVE_SLOT))
            }
        }
        if (tool == BuildTool.BRUSH) BrushRows(state)
        if (tool == BuildTool.FILL) FillRow(state)
    }
}

/** A slot's name not yet in [character]'s palette, from [base] with a number when it must. */
private fun freshSlotName(character: Character, base: String): SlotName =
    generateSequence(1) { it + 1 }.map { SlotName(if (it == 1) base else "$base-$it") }.first { character.palette.color(it) == null }

/**
 * One drag of the picker is one undo step, however many colors it passes through. The picker reports a change finished as a press turns into a
 * drag as well as at the end, so a held pointer's step closes on its release, and only a change with no pointer down, as a typed hex, closes on the report.
 */
@Composable
private fun SlotPicker(session: CharacterSession, slot: SlotName, color: Color) {
    var gesture by remember { mutableStateOf<History<SquashDocument>.Transaction?>(null) }
    var held by remember { mutableStateOf(false) }
    fun close() {
        gesture?.commit()
        gesture = null
    }
    DisposableEffect(Unit) { onDispose { close() } }
    ColorPicker(
        color,
        { picked ->
            val open = gesture ?: session.history.begin().also { gesture = it }
            open.update(SetPaletteSlot(slot, SlotColor(picked.red, picked.green, picked.blue)))
        },
        Modifier.fillMaxWidth().testTag(PaintTags.PICKER).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                held = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                held = false
                close()
            }
        },
        onChangeFinished = { if (!held) close() },
        hexDescription = stringResource(Res.string.color_picker_hex),
        squareDescription = stringResource(Res.string.color_picker_square),
        hueDescription = stringResource(Res.string.color_picker_hue),
        eyedropperDescription = stringResource(Res.string.color_picker_eyedropper),
        invalidHex = stringResource(Res.string.color_picker_invalid_hex),
    )
}

@Composable
private fun BrushRows(state: AppState) {
    val paint = state.paint
    val size = stringResource(Res.string.color_size)
    SettingRow(size) {
        Slider(paint.brushRadius, { paint.brushRadius = it }, size, Modifier.weight(1f).testTag(PaintTags.BRUSH_SIZE), valueRange = MIN_BRUSH_RADIUS..MAX_BRUSH_RADIUS)
        Readout((paint.brushRadius * PERCENT).roundToInt().toString())
    }
    val softness = stringResource(Res.string.color_softness)
    SettingRow(softness) {
        Slider(paint.softness, { paint.softness = it }, softness, Modifier.weight(1f).testTag(PaintTags.SOFTNESS))
        Readout((paint.softness * PERCENT).roundToInt().toString())
    }
}

@Composable
private fun FillRow(state: AppState) {
    val part = stringResource(Res.string.color_fill_part)
    val area = stringResource(Res.string.color_fill_area)
    Segmented(FillReach.entries, state.paint.fillReach, { state.paint.fillReach = it }, { if (it == FillReach.PART) part else area }, Modifier.testTag(PaintTags.FILL_REACH), fill = true)
}

@Composable
private fun Readout(text: String) {
    BasicText(text, Modifier.width(READOUT_WIDTH), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
}

/**
 * What the selected part's template lets Fill color by name: its colored blocks, each showing the slot it names, and its areas. A swatch takes the
 * current slot; pressed again it goes back to what the template shows there.
 */
@Composable
private fun SectionsSection(state: AppState, session: CharacterSession, document: SquashDocument) {
    val selected by state.selectedPart.collectAsState()
    val character = document.character
    val part = selected?.let(character::part) ?: return
    val sections = partSections(session, part)
    if (sections.isEmpty()) return
    val thisSide = state.thisSide && part.pair != null
    val current = state.paint.slotIn(character.palette)
    val fills = character.resolved(part).surface.areaFills
    DrawerSection(stringResource(Res.string.color_section_sections)) {
        sections.forEach { section ->
            val filled = fills[section.name]
            val shows = filled ?: section.templateSlot
            SettingRow(displayName(section.name.substringAfterLast(PATCH_SEPARATOR))) {
                Spacer(Modifier.weight(1f))
                Swatch(
                    character.palette.colorOf(shows) ?: Color.Transparent,
                    displayName(shows?.value ?: section.name),
                    filled != null,
                    { session.send(SetAreaFill(part.id, section.name, if (filled == current) null else current, thisSide)) },
                    Modifier.testTag(PaintTags.section(section.name)),
                )
            }
        }
    }
}

private class NamedSection(val name: String, val templateSlot: SlotName?)

private fun partSections(session: CharacterSession, part: PartInstance): List<NamedSection> {
    val input = partColorInput(session.document.value.character, part.id, session.templates)
    return input.patchSlots.map { NamedSection(it.patch, SlotName(it.slot)) } + input.areas.map { area -> NamedSection(area.name, area.color?.let(::SlotName)) }
}

/** Every border on the character, a mirrored pair's once: picking one shows its points, and its toggle inks it. */
@Composable
private fun RegionsSection(state: AppState, session: CharacterSession, character: Character) {
    val rows = character.parts.flatMap { part ->
        if (part.pair != null && part.side != Side.LEFT) emptyList() else character.resolved(part).surface.regions.map { part to it }
    }
    if (rows.isEmpty()) return
    val ink = stringResource(Res.string.color_ink)
    val noInk = stringResource(Res.string.color_no_ink)
    DrawerSection(stringResource(Res.string.color_section_regions)) {
        rows.forEach { (part, region) -> RegionRow(state, session, part, region, ink, noInk) }
    }
}

@Composable
private fun RegionRow(state: AppState, session: CharacterSession, part: PartInstance, region: Region, ink: String, noInk: String) {
    val name = displayName(region.id.value)
    val ref = RegionRef(part.id, region.id)
    val thisSide = state.thisSide && part.pair != null
    SettingRow(name) {
        Swatch(session.document.value.character.palette.colorOf(region.slot) ?: Color.Transparent, displayName(region.slot.value), state.paint.selectedRegion == ref, { state.paint.selectedRegion = ref }, Modifier.testTag(PaintTags.region(region.id.value)))
        Spacer(Modifier.weight(1f))
        Readout(if (region.ink) ink else noInk)
        Toggle(
            region.ink,
            { on ->
                val base = session.document.value.character.let { c -> c.part(part.id)?.let { c.editBase(it, thisSide) } }?.surface?.regions?.firstOrNull { it.id == region.id } ?: region
                session.send(SetRegion(part.id, region.id, base.copy(ink = on), thisSide))
            },
            name,
            Modifier.testTag(PaintTags.regionInk(region.id.value)),
        )
    }
}

@Composable
private fun ShadingSection(state: AppState) {
    val shading = rememberObjectShading(state)
    val flat = stringResource(Res.string.color_shading_flat)
    val twoTone = stringResource(Res.string.color_shading_two_tone)
    val far = stringResource(Res.string.color_far_parts)
    DrawerSection(stringResource(Res.string.color_section_shading), divider = false) {
        Segmented(Shading.entries, shading.mode, state::chooseShading, { if (it == Shading.FLAT) flat else twoTone }, Modifier.testTag(PaintTags.SHADING), fill = true)
        SettingRow(far) {
            Spacer(Modifier.weight(1f))
            Readout(stringResource(Res.string.color_far_amount, ((1f - FarSideDarkening().share) * PERCENT).roundToInt()))
            Toggle(shading.farSide.enabled, state::chooseFarSideDarker, far, Modifier.testTag(PaintTags.FAR_PARTS))
        }
    }
}
