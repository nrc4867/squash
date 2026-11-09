package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.screens.edit.SliderGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.SetBranchEase
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_bones
import dev.chieppa.squash.uidesign.generated.dress_from_hem
import dev.chieppa.squash.uidesign.generated.dress_offset
import dev.chieppa.squash.uidesign.generated.dress_smooth
import dev.chieppa.squash.uidesign.generated.dress_stabilizer
import dev.chieppa.squash.uidesign.generated.dress_tool_cover
import dev.chieppa.squash.uidesign.generated.dress_tool_draw
import dev.chieppa.squash.uidesign.generated.dress_tool_loose_end
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.squash.uidesign.generated.smooth_tool
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** The largest offset the slider reaches, in body units. */
private const val MAX_OFFSET = 0.2f

/** The most bones the Loose end tool gives a new loose end. */
private const val MAX_LOOSE_BONES = 10

private val READOUT_WIDTH = 40.dp

/**
 * The Dress inspector: the armed tool's options, then the picked garment, its joins and edges, its color and the body under it, then
 * the worn garments' layers.
 */
@Composable
fun DressInspector(state: AppState, session: CharacterSession) {
    val tool by state.tool.collectAsState()
    val document by session.document.collectAsState()
    val picked by state.dress.garment.collectAsState()
    val character = document.character
    val garment = picked?.let(character.wardrobe::garment)
    if (tool.dresses) ToolOptions(state, session, tool, character, garment)
    if (garment != null) {
        GarmentSection(state, session, character, garment)
        JoinsSection(state, session, character, garment)
        LooseEndsSection(state, session, character, garment)
        GarmentColorSection(session, character, garment)
        BodyUnderSection(state, session, character, garment)
    }
    LayersSection(state, session, character)
}

/** The armed garment tool's options. */
@Composable
private fun ToolOptions(state: AppState, session: CharacterSession, tool: BuildTool, character: Character, garment: Garment?) {
    DrawerSection(stringResource(toolName(tool))) {
        when (tool) {
            BuildTool.DRAW_GARMENT -> {
                OffsetRow(state, session, character, garment)
                PercentRow(stringResource(Res.string.dress_smooth), state.curves.smooth, DressTags.SMOOTH) { state.curves.smooth = it }
                PercentRow(stringResource(Res.string.dress_stabilizer), state.curves.stabilizer, DressTags.STABILIZER) { state.curves.stabilizer = it }
            }
            BuildTool.LOOSE_END -> LooseEndOptions(state)
            else -> Unit
        }
    }
}

/** Offset: how far the picked garment's branch over the selected part stands off it, absent while it has none there. */
@Composable
private fun OffsetRow(state: AppState, session: CharacterSession, character: Character, garment: Garment?) {
    val selected by state.selectedPart.collectAsState()
    val part = selected?.let(character::part) ?: return
    val branch = garment?.let { character.covers(it, part) } ?: return
    val label = stringResource(Res.string.dress_offset)
    val gesture = remember(session) { SliderGesture(session) }
    SettingRow(label) {
        Slider(
            branch.ease,
            { gesture.change(SetBranchEase(garment.id, branch.part, it)) },
            label,
            Modifier.weight(1f).testTag(DressTags.OFFSET),
            0f..MAX_OFFSET,
            gesture::done,
        )
        Readout(formatNumber(branch.ease))
    }
}

@Composable
private fun LooseEndOptions(state: AppState) {
    val bones by state.dress.looseBones.collectAsState()
    val fromHem by state.dress.looseFromHem.collectAsState()
    val label = stringResource(Res.string.dress_bones)
    SettingRow(label) {
        Slider(bones.toFloat(), { state.dress.looseBones.value = it.roundToInt().coerceIn(1, MAX_LOOSE_BONES) }, label, Modifier.weight(1f), 1f..MAX_LOOSE_BONES.toFloat())
        Readout(bones.toString())
    }
    SwitchRow(stringResource(Res.string.dress_from_hem), fromHem) { state.dress.looseFromHem.value = it }
}

@Composable
internal fun PercentRow(label: String, value: Float, tag: String, onChange: (Float) -> Unit) {
    SettingRow(label) {
        Slider(value, onChange, label, Modifier.weight(1f).testTag(tag))
        Readout(stringResource(Res.string.edit_percent, (value * PERCENT).roundToInt()))
    }
}

/** A row with a [label] and a switch at its end. */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    SettingRow(label) {
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Toggle(checked, onChange, label, modifier)
    }
}

/** A number or short value at a row's end. */
@Composable
internal fun Readout(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.width(READOUT_WIDTH), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
}

private fun toolName(tool: BuildTool): StringResource = when (tool) {
    BuildTool.EDIT_GARMENT -> Res.string.shell_action_edit_curves
    BuildTool.ERASE_GARMENT -> Res.string.edit_tool_erase
    BuildTool.SMOOTH_GARMENT -> Res.string.smooth_tool
    BuildTool.COVER -> Res.string.dress_tool_cover
    BuildTool.LOOSE_END -> Res.string.dress_tool_loose_end
    else -> Res.string.dress_tool_draw
}
