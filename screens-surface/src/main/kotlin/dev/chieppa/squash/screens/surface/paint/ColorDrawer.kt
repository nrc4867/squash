package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.partColorInput
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.shownName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetAreaFill
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
import dev.chieppa.squash.uidesign.generated.color_fill_under
import dev.chieppa.squash.uidesign.generated.color_fill_whole
import dev.chieppa.squash.uidesign.generated.color_section_paint
import dev.chieppa.squash.uidesign.generated.color_section_shading
import dev.chieppa.squash.uidesign.generated.color_shading_flat
import dev.chieppa.squash.uidesign.generated.color_shading_two_tone
import dev.chieppa.squash.uidesign.generated.color_size
import dev.chieppa.squash.uidesign.generated.color_softness
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
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

/** The Color inspector: the palette and the tool's controls, what is painted on the selected part, and shading. */
@Composable
fun ColorDrawerContent(state: AppState, session: CharacterSession) {
    val document by session.document.collectAsState()
    val tool by state.tool.collectAsState()
    PaintSection(state, session, document, tool)
    PartPaintSection(state, session, document)
    ShadingSection(state)
}

@Composable
private fun PaintSection(state: AppState, session: CharacterSession, document: SquashDocument, tool: BuildTool) {
    val palette = document.character.palette
    val current = state.paint.slotIn(palette)
    DrawerSection(stringResource(Res.string.color_section_paint)) {
        SlotSwatches(state, palette, current)
        SlotActions(state, session, document.character, current)
        if (current != null) SlotPicker(session, current, palette.colorOf(current) ?: Color.Transparent, Modifier.fillMaxWidth().testTag(PaintTags.PICKER))
        if (tool == BuildTool.BRUSH) BrushRows(state)
        if (tool == BuildTool.FILL) FillRow(state)
    }
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
    val under = stringResource(Res.string.color_fill_under)
    val whole = stringResource(Res.string.color_fill_whole)
    Segmented(FillReach.entries, state.paint.fillReach, { state.paint.fillReach = it }, { if (it == FillReach.UNDER_POINTER) under else whole }, Modifier.testTag(PaintTags.FILL_REACH), fill = true)
}

@Composable
private fun Readout(text: String) {
    BasicText(text, Modifier.width(READOUT_WIDTH), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
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
