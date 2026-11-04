package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.catalog.FitStages
import dev.chieppa.squash.character.catalog.blockLabel
import dev.chieppa.squash.character.catalog.count
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.uidesign.generated.move_section
import dev.chieppa.squash.uidesign.generated.move_outward
import dev.chieppa.squash.uidesign.generated.move_lean
import dev.chieppa.squash.uidesign.generated.move_degrees
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.graph.StageCount
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.pick_draw
import dev.chieppa.squash.uidesign.generated.shell_part_built_from
import dev.chieppa.squash.uidesign.generated.shell_part_fit
import dev.chieppa.squash.uidesign.generated.shell_part_fit_done
import dev.chieppa.squash.uidesign.generated.shell_part_fit_from_template
import dev.chieppa.squash.uidesign.generated.shell_part_fit_skeleton
import dev.chieppa.squash.uidesign.generated.shell_part_fit_surface
import dev.chieppa.squash.uidesign.generated.shell_part_fit_unseen
import dev.chieppa.squash.uidesign.generated.shell_part_none
import dev.chieppa.squash.uidesign.generated.shell_part_parameters
import dev.chieppa.squash.uidesign.generated.shell_part_this_side
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.EmptyState
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.formatNumber
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private const val FLAG_ON = 1f
private const val FLAG_OFF = 0f
private const val PERCENT = 100f
private val READOUT_WIDTH = 52.dp

/** The Part drawer: the picked type on Pick a part type, the selected part's views and parameters elsewhere. */
@Composable
fun PartDrawerContent(state: AppState, toolSections: ToolSections) {
    val screen by state.screen.collectAsState()
    if (screen == Screen.PICK_PART) PickedTypeDetails(state) else SelectedPartDetails(state, toolSections)
}

@Composable
private fun PickedTypeDetails(state: AppState) {
    val type by state.pickedType.collectAsState()
    val socket by state.pickedSocket.collectAsState()
    val template = type?.let { state.templates.template(it) } ?: return
    YouDrawSection(template, null)
    ToolKnowsSection(template, socket?.paired == true)
    AreasSection(template)
    if (socket != null) {
        DrawerSection(divider = false) {
            Button(
                stringResource(Res.string.pick_draw, template.name.lowercase()),
                { state.placePicked() },
                Modifier.testTag(ShellTags.PICK_DRAW),
                kind = ButtonKind.Primary,
                icon = SquashIcons.DrawOutline,
            )
        }
    }
}

@Composable
private fun SelectedPartDetails(state: AppState, toolSections: ToolSections) {
    val session by state.session.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val open = session
    val document = open?.document?.collectAsState()?.value
    val part = selected?.let { document?.character?.part(it) }
    val data = part?.let { document?.character?.resolved(it) }
    val template = part?.let { state.templates.template(it.template) }
    if (open == null || part == null || data == null || template == null) {
        EmptyState(SquashIcons.Parts, stringResource(Res.string.shell_part_none), Modifier.testTag(ShellTags.PART_EMPTY))
        return
    }
    val tool by state.tool.collectAsState()
    val mirror by state.mirror.collectAsState()
    val character = document?.character ?: return
    val base = character.editBase(part, !mirror)
    when (val kind = partDrawerKind(tool, hasPart = true, drawn = data.outlines.isNotEmpty())) {
        PartDrawerKind.OUTLINE_CURVES, PartDrawerKind.OUTLINE, PartDrawerKind.SPINE_WIDTH, PartDrawerKind.SMOOTH -> toolSections(kind, state, open, part, base.outlines)
        PartDrawerKind.MOVE -> {
            if (part.socket != null) {
                val hidden = if (mirror) character.overridesOf(part).params else emptyMap()
                DrawerSection(stringResource(Res.string.move_section)) {
                    AngleControl(open, part.id, PlacementParams.OUTWARD, stringResource(Res.string.move_outward), base.params, !mirror, hidden)
                    AngleControl(open, part.id, PlacementParams.LEAN, stringResource(Res.string.move_lean), base.params, !mirror, hidden)
                }
            }
            BuiltFromSection(template)
        }
        PartDrawerKind.PART -> {
            FitSection(open)
            val hidden = if (mirror) character.overridesOf(part).params else emptyMap()
            val rows = parameterRows(template, base.params)
            if (rows.isNotEmpty()) {
                DrawerSection(stringResource(Res.string.shell_part_parameters)) {
                    rows.forEach { row -> key(part.id, row.name) { ParameterControl(open, part.id, row, !mirror, hidden[row.name]) } }
                }
            }
            BuiltFromSection(template)
        }
    }
}

/** One of the Move tool's angles as a slider, from straight at 0. */
@Composable
private fun AngleControl(session: CharacterSession, part: PartId, name: String, label: String, params: Map<String, Float>, thisSide: Boolean, hidden: Map<String, Float>) {
    val row = ParameterRow(name, params[name] ?: 0f, false, -PlacementParams.MAX_ANGLE, PlacementParams.MAX_ANGLE, null, stringResource(Res.string.move_degrees))
    key(part, name) { ParameterControl(session, part, row, thisSide, hidden[name], label) }
}

/**
 * A parameter's control: a toggle for a flag, a slider between limits, a number field otherwise. Every change is a command,
 * to this side alone with [thisSide]. [hidden] is the value this side's override keeps showing over the pair's.
 */
@Composable
private fun ParameterControl(session: CharacterSession, part: PartId, row: ParameterRow, thisSide: Boolean, hidden: Float?, label: String = displayName(row.name)) {
    val range = row.range
    val tagged = Modifier.testTag(ShellTags.parameter(row.name))
    SettingRow(label) {
        when {
            row.isFlag -> {
                Spacer(Modifier.weight(1f))
                HiddenValue(hidden)
                Toggle(row.value != 0f, { session.send(SetParameter(part, row.name, if (it) FLAG_ON else FLAG_OFF, thisSide)) }, label, tagged)
            }
            range != null -> {
                ParameterSlider(session, part, row, label, range, thisSide, tagged.weight(1f))
                HiddenValue(hidden)
                BasicText(
                    formatNumber(row.value) + row.unit?.let { " $it" }.orEmpty(),
                    Modifier.width(READOUT_WIDTH),
                    style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
                    maxLines = 1,
                )
            }
            else -> {
                Spacer(Modifier.weight(1f))
                HiddenValue(hidden)
                NumberField(
                    row.value,
                    { session.send(SetParameter(part, row.name, row.constrain(it), thisSide)) },
                    label,
                    tagged,
                    unit = row.unit.orEmpty(),
                    step = row.step ?: 1f,
                )
            }
        }
    }
}

/** What this side keeps showing where its override hides a change to the pair. */
@Composable
private fun HiddenValue(value: Float?) {
    if (value == null) return
    Chip(stringResource(Res.string.shell_part_this_side, formatNumber(value)), kind = ChipKind.Warn, mono = true)
}

/** The stages of the part's fit, with the graph's own count of the one under way. */
@Composable
private fun FitSection(session: CharacterSession) {
    val progress by session.graph.progress.collectAsState()
    val done = stringResource(Res.string.shell_part_fit_done)
    DrawerSection(stringResource(Res.string.shell_part_fit)) {
        FactRow(stringResource(Res.string.shell_part_fit_skeleton), fitValue(progress.count(FitStages.SKELETON), done))
        FactRow(stringResource(Res.string.shell_part_fit_surface), fitValue(progress.count(FitStages.SURFACE), done))
        FactRow(stringResource(Res.string.shell_part_fit_unseen), stringResource(Res.string.shell_part_fit_from_template))
    }
}

@Composable
private fun fitValue(count: StageCount, done: String): String =
    if (count.finished) done else stringResource(Res.string.edit_percent, (count.fraction * PERCENT).roundToInt())

/** What the template builds the part from. */
@Composable
private fun BuiltFromSection(template: PartTemplate) {
    DrawerSection(stringResource(Res.string.shell_part_built_from), divider = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            template.blocks.map(::blockLabel).distinct().forEach { Chip(it, mono = true) }
        }
    }
}

/** One drag is one undo step: the values it passes through are previews of a single [SetParameter]. */
@Composable
private fun ParameterSlider(
    session: CharacterSession,
    part: PartId,
    row: ParameterRow,
    label: String,
    range: ClosedFloatingPointRange<Float>,
    thisSide: Boolean,
    modifier: Modifier,
) {
    var gesture by remember { mutableStateOf<History<SquashDocument>.Transaction?>(null) }
    DisposableEffect(Unit) { onDispose { gesture?.commit() } }
    Slider(
        row.value,
        { value ->
            val open = gesture ?: session.history.begin().also { gesture = it }
            open.update(SetParameter(part, row.name, row.constrain(value), thisSide))
        },
        label,
        modifier,
        valueRange = range,
        onValueChangeFinished = {
            gesture?.commit()
            gesture = null
        },
    )
}
