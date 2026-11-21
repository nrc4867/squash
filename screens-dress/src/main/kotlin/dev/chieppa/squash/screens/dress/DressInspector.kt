package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_bones
import dev.chieppa.squash.uidesign.generated.dress_from_hem
import dev.chieppa.squash.uidesign.generated.dress_tool_hole
import dev.chieppa.squash.uidesign.generated.dress_tool_paint
import dev.chieppa.squash.uidesign.generated.dress_tool_loose_end
import dev.chieppa.squash.uidesign.generated.dress_tool_print_line
import dev.chieppa.squash.uidesign.generated.dress_tool_print_region
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.squash.uidesign.generated.smooth_tool
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** The most bones the Loose end tool gives a new loose end. */
private const val MAX_LOOSE_BONES = 10

private val READOUT_WIDTH = 40.dp

/**
 * The Dress inspector: the worn garments' layers, then the armed tool's options, then the picked garment, with its fit, its painted
 * areas, edges and holes, and its hem ink, the small parts tucked inside it, what its cloth prints, its loose ends, the parts it owns and its color. The layers come first because the rest changes with
 * the pick, so picking one leaves its row where it was.
 */
@Composable
fun DressInspector(state: AppState, session: CharacterSession) {
    val tool by state.tool.collectAsState()
    val document by session.document.collectAsState()
    val picked by state.dress.garment.collectAsState()
    val character = document.character
    val garment = picked?.let(character.wardrobe::garment)
    LayersSection(state, session, character)
    if (tool.dresses) ToolOptions(state, session, tool)
    if (garment != null) {
        GarmentSection(state, session, character, garment)
        TuckSection(state, session, character, garment)
        PrintsSection(session, garment)
        LooseEndsSection(state, session, character, garment)
        OwnedPartsSection(state, session, character, garment)
        GarmentColorSection(session, character, garment)
    }
}

/** The armed garment tool's options, or no section for a tool with none. */
@Composable
private fun ToolOptions(state: AppState, session: CharacterSession, tool: BuildTool) {
    val options: (@Composable () -> Unit) = when (tool) {
        BuildTool.PAINT -> { { PaintOptions(state) } }
        BuildTool.EDIT_GARMENT -> { { EditCurvesOptions(state) } }
        BuildTool.HOLE -> { { HoleOptions(state) } }
        BuildTool.LOOSE_END -> { { LooseEndOptions(state) } }
        BuildTool.PRINT_REGION -> { { PrintOptions(state, session, region = true) } }
        BuildTool.PRINT_MARK -> { { PrintOptions(state, session, region = false) } }
        else -> return
    }
    DrawerSection(stringResource(toolName(tool))) { options() }
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
    BuildTool.HOLE -> Res.string.dress_tool_hole
    BuildTool.LOOSE_END -> Res.string.dress_tool_loose_end
    BuildTool.PRINT_REGION -> Res.string.dress_tool_print_region
    BuildTool.PRINT_MARK -> Res.string.dress_tool_print_line
    else -> Res.string.dress_tool_paint
}
