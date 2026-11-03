package dev.chieppa.squash.screens.shell

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
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.pick_draw
import dev.chieppa.squash.uidesign.generated.shell_part_none
import dev.chieppa.squash.uidesign.generated.shell_part_parameters
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.EmptyState
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.stringResource

private const val FLAG_ON = 1f
private const val FLAG_OFF = 0f
private val READOUT_WIDTH = 52.dp

/** The Part drawer: the picked type on Pick a part type, the selected part's views and parameters elsewhere. */
@Composable
fun PartDrawerContent(state: AppState) {
    val screen by state.screen.collectAsState()
    if (screen == Screen.PICK_PART) PickedTypeDetails(state) else SelectedPartDetails(state)
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
                icon = StretchIcons.Pen,
            )
        }
    }
}

@Composable
private fun SelectedPartDetails(state: AppState) {
    val session by state.session.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val open = session
    val document = open?.document?.collectAsState()?.value
    val part = selected?.let { document?.character?.part(it) }
    val template = part?.let { state.templates.template(it.template) }
    if (open == null || part == null || template == null) {
        EmptyState(SquashIcons.Parts, stringResource(Res.string.shell_part_none), Modifier.testTag(ShellTags.PART_EMPTY))
        return
    }
    YouDrawSection(template, part)
    val rows = parameterRows(template, part)
    if (rows.isNotEmpty()) {
        DrawerSection(stringResource(Res.string.shell_part_parameters), divider = false) {
            rows.forEach { row -> key(part.id, row.name) { ParameterControl(open, part.id, row) } }
        }
    }
}

/** A parameter's control: a toggle for a flag, a slider between limits, a number field otherwise. Every change is a command. */
@Composable
private fun ParameterControl(session: CharacterSession, part: PartId, row: ParameterRow) {
    val label = displayName(row.name)
    val range = row.range
    val tagged = Modifier.testTag(ShellTags.parameter(row.name))
    SettingRow(label) {
        when {
            row.isFlag -> {
                Spacer(Modifier.weight(1f))
                Toggle(row.value != 0f, { session.send(SetParameter(part, row.name, if (it) FLAG_ON else FLAG_OFF)) }, label, tagged)
            }
            range != null -> {
                ParameterSlider(session, part, row, label, range, tagged.weight(1f))
                BasicText(
                    formatNumber(row.value) + row.unit?.let { " $it" }.orEmpty(),
                    Modifier.width(READOUT_WIDTH),
                    style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
                    maxLines = 1,
                )
            }
            else -> {
                Spacer(Modifier.weight(1f))
                NumberField(
                    row.value,
                    { session.send(SetParameter(part, row.name, row.constrain(it))) },
                    label,
                    tagged,
                    unit = row.unit.orEmpty(),
                    step = row.step ?: 1f,
                )
            }
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
    modifier: Modifier,
) {
    var gesture by remember { mutableStateOf<History<SquashDocument>.Transaction?>(null) }
    DisposableEffect(Unit) { onDispose { gesture?.commit() } }
    Slider(
        row.value,
        { value ->
            val open = gesture ?: session.history.begin().also { gesture = it }
            open.update(SetParameter(part, row.name, row.constrain(value)))
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
