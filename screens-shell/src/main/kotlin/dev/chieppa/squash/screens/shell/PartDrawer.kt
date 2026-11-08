package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.chieppa.squash.character.build.isGeneric
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.template
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_part_none
import dev.chieppa.squash.uidesign.generated.shell_part_this_side
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.EmptyState
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.stringResource
import dev.chieppa.squash.uidesign.generated.shell_part_template

private const val FLAG_ON = 1f
private const val FLAG_OFF = 0f
private const val PERCENT = 100f
private val READOUT_WIDTH = 52.dp

/**
 * The Shape inspector: Add part's section alone while Add part is armed, since it adds to the character rather than changing the
 * selected part; otherwise the armed tool's sections, then the selected part's role and template, a generic part's own shape, its
 * parameters and its anatomy.
 */
@Composable
fun ShapeInspector(state: AppState, toolSections: ToolSections) {
    val tool by state.tool.collectAsState()
    if (tool == BuildTool.ADD_PART) AddPartSection(state) else SelectedPartDetails(state, toolSections)
}

/** The inspector off the workspace, where it has nothing to show. */
@Composable
internal fun NoPart() {
    EmptyState(SquashIcons.Parts, stringResource(Res.string.shell_part_none), Modifier.testTag(ShellTags.PART_EMPTY))
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
        PartDrawerKind.MOVE -> MoveSection(state, open, part, template, base.params, !mirror, if (mirror) character.overridesOf(part).params else emptyMap())
        PartDrawerKind.PART -> Unit
    }
    val hidden = if (mirror) character.overridesOf(part).params else emptyMap()
    val rows = parameterRows(template, base.params)
    if (template.isGeneric) {
        DrawerSection(stringResource(Res.string.shell_part_template, template.name)) {
            RoleRow(state, open, character, part)
            TemplateRow(state, part.id, template)
        }
        GenericSection(open, part.id, template, base.params, !mirror)
        DrawerSection { rows.forEach { row -> key(part.id, row.name) { ParameterControl(open, part.id, row, !mirror, hidden[row.name]) } } }
    } else {
        DrawerSection(stringResource(Res.string.shell_part_template, template.name)) {
            RoleRow(state, open, character, part)
            TemplateRow(state, part.id, template)
            rows.forEach { row -> key(part.id, row.name) { ParameterControl(open, part.id, row, !mirror, hidden[row.name]) } }
        }
    }
    AnatomySection(state, open, part)
}

/**
 * A parameter's control: a toggle for a flag, a slider between limits, a number field otherwise. Every change is a command,
 * to this side alone with [thisSide]. [hidden] is the value this side's override keeps showing over the pair's.
 */
@Composable
internal fun ParameterControl(session: CharacterSession, part: PartId, row: ParameterRow, thisSide: Boolean, hidden: Float?, label: String = displayName(row.name)) {
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
