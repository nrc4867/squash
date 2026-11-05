package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.partColorInput
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.shownName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.render.FarSideDarkening
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.FillReach
import dev.chieppa.squash.screens.sheet.chooseFarSideDarker
import dev.chieppa.squash.screens.sheet.chooseShading
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.sheet.rememberObjectShading
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_far_amount
import dev.chieppa.squash.uidesign.generated.color_far_parts
import dev.chieppa.squash.uidesign.generated.color_fill_area
import dev.chieppa.squash.uidesign.generated.color_fill_part
import dev.chieppa.squash.uidesign.generated.color_picker_eyedropper
import dev.chieppa.squash.uidesign.generated.color_picker_hex
import dev.chieppa.squash.uidesign.generated.color_picker_hue
import dev.chieppa.squash.uidesign.generated.color_picker_invalid_hex
import dev.chieppa.squash.uidesign.generated.color_picker_square
import dev.chieppa.squash.uidesign.generated.color_section_paint
import dev.chieppa.squash.uidesign.generated.color_section_sections
import dev.chieppa.squash.uidesign.generated.color_section_shading
import dev.chieppa.squash.uidesign.generated.color_shading_flat
import dev.chieppa.squash.uidesign.generated.color_shading_two_tone
import dev.chieppa.squash.uidesign.generated.color_size
import dev.chieppa.squash.uidesign.generated.color_softness
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.ColorPicker
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
    DrawerSection(stringResource(Res.string.color_section_paint)) {
        SlotSwatches(state, palette, current)
        SlotActions(state, session, document.character, current)
        if (current != null) SlotPicker(session, current, palette.colorOf(current) ?: Color.Transparent)
        if (tool == BuildTool.BRUSH) BrushRows(state)
        if (tool == BuildTool.FILL) FillRow(state)
    }
}

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
                    shows?.let(character.palette::shownName) ?: displayName(section.name),
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
