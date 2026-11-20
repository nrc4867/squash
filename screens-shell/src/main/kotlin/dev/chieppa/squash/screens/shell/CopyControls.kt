package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.move.CopyKnob
import dev.chieppa.squash.screens.move.SocketLayout
import dev.chieppa.squash.screens.pickedCopyValue
import dev.chieppa.squash.screens.pickedLayout
import dev.chieppa.squash.screens.setCopyValue
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.move_copy_falloff
import dev.chieppa.squash.uidesign.generated.move_copy_from_u
import dev.chieppa.squash.uidesign.generated.move_copy_from_v
import dev.chieppa.squash.uidesign.generated.move_copy_lean_jitter
import dev.chieppa.squash.uidesign.generated.move_copy_path_count
import dev.chieppa.squash.uidesign.generated.move_copy_pitch
import dev.chieppa.squash.uidesign.generated.move_copy_roll
import dev.chieppa.squash.uidesign.generated.move_copy_sag
import dev.chieppa.squash.uidesign.generated.move_copy_seed
import dev.chieppa.squash.uidesign.generated.move_copy_size
import dev.chieppa.squash.uidesign.generated.move_copy_size_jitter
import dev.chieppa.squash.uidesign.generated.move_copy_spacing
import dev.chieppa.squash.uidesign.generated.move_copy_spread_count
import dev.chieppa.squash.uidesign.generated.move_copy_to_u
import dev.chieppa.squash.uidesign.generated.move_copy_to_v
import dev.chieppa.squash.uidesign.generated.move_copy_turn_jitter
import dev.chieppa.squash.uidesign.generated.move_copy_droop
import dev.chieppa.squash.uidesign.generated.move_degrees
import dev.chieppa.squash.uidesign.generated.move_socket_layout_path
import dev.chieppa.squash.uidesign.generated.move_socket_layout_path_tip
import dev.chieppa.squash.uidesign.generated.move_socket_layout_sockets
import dev.chieppa.squash.uidesign.generated.move_socket_layout_sockets_tip
import dev.chieppa.squash.uidesign.generated.move_socket_layout_spread
import dev.chieppa.squash.uidesign.generated.move_socket_layout_spread_tip
import dev.chieppa.squash.uidesign.generated.place_percent
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f

/** A knob whose whole range is this small steps by [FINE_STEP]. */
private const val SMALL_RANGE = 2f
private const val FINE_STEP = 0.01f

private val LAYOUT_LABELS: Map<SocketLayout, StringResource> = mapOf(
    SocketLayout.SOCKETS to Res.string.move_socket_layout_sockets,
    SocketLayout.SPREAD to Res.string.move_socket_layout_spread,
    SocketLayout.PATH to Res.string.move_socket_layout_path,
)

private val LAYOUT_TIPS: Map<SocketLayout, StringResource> = mapOf(
    SocketLayout.SOCKETS to Res.string.move_socket_layout_sockets_tip,
    SocketLayout.SPREAD to Res.string.move_socket_layout_spread_tip,
    SocketLayout.PATH to Res.string.move_socket_layout_path_tip,
)

private val KNOB_LABELS: Map<CopyKnob, StringResource> = mapOf(
    CopyKnob.SPREAD_COUNT to Res.string.move_copy_spread_count,
    CopyKnob.PATH_COUNT to Res.string.move_copy_path_count,
    CopyKnob.SEED to Res.string.move_copy_seed,
    CopyKnob.FROM_U to Res.string.move_copy_from_u,
    CopyKnob.TO_U to Res.string.move_copy_to_u,
    CopyKnob.FROM_V to Res.string.move_copy_from_v,
    CopyKnob.TO_V to Res.string.move_copy_to_v,
    CopyKnob.SPACING to Res.string.move_copy_spacing,
    CopyKnob.SIZE to Res.string.move_copy_size,
    CopyKnob.FALLOFF to Res.string.move_copy_falloff,
    CopyKnob.SIZE_JITTER to Res.string.move_copy_size_jitter,
    CopyKnob.LEAN_JITTER to Res.string.move_copy_lean_jitter,
    CopyKnob.TURN_JITTER to Res.string.move_copy_turn_jitter,
    CopyKnob.DROOP to Res.string.move_copy_droop,
    CopyKnob.PITCH to Res.string.move_copy_pitch,
    CopyKnob.ROLL to Res.string.move_copy_roll,
    CopyKnob.SAG to Res.string.move_copy_sag,
)

/**
 * What Add socket's next press places, sockets or a socket showing copies over an area or along a path, the picked socket's layout while
 * one is picked; for copies, their count, seed, area and jitter, the picked socket's own while it shows copies.
 */
@Composable
internal fun SocketLayoutControls(state: AppState, session: CharacterSession) {
    session.document.collectAsState().value
    state.move.layout.collectAsState().value
    state.move.copyValues.collectAsState().value
    state.move.picked.collectAsState().value
    val shown = state.pickedLayout()
    val labels = LAYOUT_LABELS.mapValues { stringResource(it.value) }
    val tips = LAYOUT_TIPS.mapValues { stringResource(it.value) }
    Segmented(
        SocketLayout.entries.toList(), shown,
        { state.move.layout.value = it; state.move.picked.value = null },
        labels::getValue, Modifier.fillMaxWidth().testTag(ShellTags.SOCKET_LAYOUT), fill = true, tooltip = tips::getValue,
    )
    if (shown == SocketLayout.SOCKETS) return
    val percent = stringResource(Res.string.place_percent)
    val degrees = stringResource(Res.string.move_degrees)
    CopyKnob.entries.filter { shown in it.layouts }.forEach { knob ->
        val label = stringResource(KNOB_LABELS.getValue(knob))
        val scale = if (knob.percent) PERCENT else 1f
        SettingRow(label) {
            Spacer(Modifier.weight(1f))
            NumberField(
                state.pickedCopyValue(knob) * scale,
                { state.setCopyValue(knob, it / scale) },
                label,
                Modifier.testTag(ShellTags.copyKnob(knob.name)),
                unit = if (knob.percent) percent else if (knob.degrees) degrees else "",
                step = if (knob.max * scale <= SMALL_RANGE) FINE_STEP else 1f,
                range = knob.min * scale..knob.max * scale,
            )
        }
    }
}
