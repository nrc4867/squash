package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
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
import dev.chieppa.squash.character.build.membraneBetween
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.LEAST_MEMBRANE_THICKNESS
import dev.chieppa.squash.document.MOST_MEMBRANE_BILLOW
import dev.chieppa.squash.document.MOST_MEMBRANE_EDGE
import dev.chieppa.squash.document.MOST_MEMBRANE_THICKNESS
import dev.chieppa.squash.document.Membrane
import dev.chieppa.squash.document.MembraneId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetMembrane
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.Ulid
import dev.chieppa.squash.document.membraneOf
import dev.chieppa.squash.document.membranePartId
import dev.chieppa.squash.document.membraneShownAs
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.choosePart
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_membrane_add
import dev.chieppa.squash.uidesign.generated.shell_membrane_between
import dev.chieppa.squash.uidesign.generated.shell_membrane_billow
import dev.chieppa.squash.uidesign.generated.shell_membrane_edge
import dev.chieppa.squash.uidesign.generated.shell_membrane_mirrored
import dev.chieppa.squash.uidesign.generated.shell_membrane_remove
import dev.chieppa.squash.uidesign.generated.shell_membrane_sink
import dev.chieppa.squash.uidesign.generated.shell_membrane_thickness
import dev.chieppa.squash.uidesign.generated.shell_membrane_to
import dev.chieppa.squash.uidesign.generated.shell_membranes
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.stringResource

private val MEMBRANE_READOUT_WIDTH = 52.dp

/**
 * The membranes [part] is a rail of, one row each that selects it, and Add membrane, whose menu offers every other part to span to: one
 * step that adds a membrane along the sides of the two parts facing each other, built on both sides for a part of a pair.
 */
@Composable
internal fun MembranesOfPart(state: AppState, session: CharacterSession, character: Character, part: PartInstance) {
    val assembled by session.assembly.output.collectAsState()
    val touching = character.membranes.filter { part.id in it.parts }
    var open by remember(part.id) { mutableStateOf(false) }
    DrawerSection(stringResource(Res.string.shell_membranes)) {
        for (membrane in touching) {
            Box(Modifier.testTag(ShellTags.membraneRow(membrane.id.value))) {
                Button(membraneName(membrane, character, state), { state.choosePart(membranePartId(membrane.id)) }, kind = ButtonKind.Quiet, icon = SquashIcons.Membrane)
            }
        }
        Box {
            Button(stringResource(Res.string.shell_membrane_add), { open = true }, Modifier.testTag(ShellTags.MEMBRANE_ADD), kind = ButtonKind.Quiet, icon = SquashIcons.Membrane, enabled = assembled != null)
            Menu(open, { open = false }) {
                for (other in character.parts.filter { it.id != part.id && it.garment == null }) {
                    Box(Modifier.testTag(ShellTags.membraneTo(other.id.value))) {
                        MenuItem(stringResource(Res.string.shell_membrane_to, partName(other, state.templates)), {
                            open = false
                            val id = MembraneId(Ulid.next())
                            assembled?.let { membraneBetween(id, part.id, other.id, it, mirrored = part.side != null) }?.let { made ->
                                session.send(SetMembrane(id, made))
                                state.choosePart(membranePartId(id))
                            }
                        })
                    }
                }
            }
        }
    }
}

/** What a membrane is called in the inspector: the parts its rails start on. */
@Composable
private fun membraneName(membrane: Membrane, character: Character, state: AppState): String {
    fun name(id: PartId) = character.part(id)?.let { partName(it, state.templates) } ?: id.value
    return stringResource(Res.string.shell_membrane_between, name(membrane.first.first().part), name(membrane.second.first().part))
}

/** The selected membrane, [selected] its part's id: its edge curve, billow, thickness and sink, both sides or one, and Remove. */
@Composable
internal fun MembraneDetails(state: AppState, session: CharacterSession, character: Character, selected: PartId): Boolean {
    val membrane = character.membraneShownAs(selected) ?: return false
    val id = membrane.id
    key(id) {
        DrawerSection(membraneName(membrane, character, state)) {
            MembraneSlider(session, id, "edge", stringResource(Res.string.shell_membrane_edge), membrane.edge, 0f..MOST_MEMBRANE_EDGE) { m, v -> m.copy(edge = v) }
            MembraneSlider(session, id, "billow", stringResource(Res.string.shell_membrane_billow), membrane.billow, -MOST_MEMBRANE_BILLOW..MOST_MEMBRANE_BILLOW) { m, v -> m.copy(billow = v) }
            MembraneSlider(session, id, "thickness", stringResource(Res.string.shell_membrane_thickness), membrane.thickness, LEAST_MEMBRANE_THICKNESS..MOST_MEMBRANE_THICKNESS) { m, v -> m.copy(thickness = v) }
            MembraneSlider(session, id, "sink", stringResource(Res.string.shell_membrane_sink), membrane.sink, 0f..1f) { m, v -> m.copy(sink = v) }
            val both = stringResource(Res.string.shell_membrane_mirrored)
            SettingRow(both) {
                Spacer(Modifier.weight(1f))
                Toggle(membrane.mirrored, { session.send(SetMembrane(id, membrane.copy(mirrored = it))) }, both, Modifier.testTag(ShellTags.MEMBRANE_MIRRORED))
            }
            Button(stringResource(Res.string.shell_membrane_remove), {
                session.send(SetMembrane(id, null))
                state.choosePart(membrane.first.first().part)
            }, Modifier.testTag(ShellTags.MEMBRANE_REMOVE), kind = ButtonKind.Quiet)
        }
    }
    return true
}

/** One of a membrane's numbers on a slider; one drag is one undo step, the values it passes through previews of a single [SetMembrane]. */
@Composable
private fun MembraneSlider(
    session: CharacterSession,
    id: MembraneId,
    name: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    change: (Membrane, Float) -> Membrane,
) {
    var gesture by remember { mutableStateOf<History<SquashDocument>.Transaction?>(null) }
    DisposableEffect(Unit) { onDispose { gesture?.commit() } }
    SettingRow(label) {
        Slider(
            value.coerceIn(range),
            { next ->
                val current = session.document.value.character.membranes.firstOrNull { it.id == id } ?: return@Slider
                val open = gesture ?: session.history.begin().also { gesture = it }
                open.update(SetMembrane(id, change(current, next.coerceIn(range))))
            },
            label,
            Modifier.testTag(ShellTags.membraneSetting(name)).weight(1f),
            valueRange = range,
            onValueChangeFinished = {
                gesture?.commit()
                gesture = null
            },
        )
        BasicText(
            formatNumber(value),
            Modifier.width(MEMBRANE_READOUT_WIDTH),
            style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
            maxLines = 1,
        )
    }
}

/** Whether [part] names a membrane rather than a part of the document. */
internal fun isMembranePart(part: PartId?): Boolean = part?.let(::membraneOf) != null
