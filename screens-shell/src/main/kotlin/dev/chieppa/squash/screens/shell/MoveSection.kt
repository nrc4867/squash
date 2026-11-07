package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.deletePickedSocket
import dev.chieppa.squash.screens.move.MoveMode
import dev.chieppa.squash.screens.pickedOutward
import dev.chieppa.squash.screens.pickedSocketHolds
import dev.chieppa.squash.screens.setOutward
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.move_degrees
import dev.chieppa.squash.uidesign.generated.move_lean
import dev.chieppa.squash.uidesign.generated.move_mode_add_socket
import dev.chieppa.squash.uidesign.generated.move_mode_place
import dev.chieppa.squash.uidesign.generated.move_outward
import dev.chieppa.squash.uidesign.generated.move_section
import dev.chieppa.squash.uidesign.generated.move_socket_delete
import dev.chieppa.squash.uidesign.generated.move_socket_outward
import dev.chieppa.squash.uidesign.generated.move_socket_pair
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.stringResource

/** How faint Delete socket shows while the picked socket holds a part or none is picked, as the board draws it. */
private const val DISABLED_ALPHA = 0.5f

/**
 * The Move tool's section of the Shape inspector: Place part or Add socket. Place part holds the selected part's own angles when it
 * sits on a socket; Add socket holds the outward angle and pair flag the next socket takes, the angle editing the picked hand-made
 * socket instead, and Delete socket for a picked socket that holds nothing.
 */
@Composable
internal fun MoveSection(state: AppState, session: CharacterSession, part: PartInstance, params: Map<String, Float>, thisSide: Boolean, hidden: Map<String, Float>) {
    val mode by state.move.mode.collectAsState()
    val labels = mapOf(MoveMode.PLACE_PART to stringResource(Res.string.move_mode_place), MoveMode.ADD_SOCKET to stringResource(Res.string.move_mode_add_socket))
    DrawerSection(stringResource(Res.string.move_section)) {
        Segmented(MoveMode.entries, mode, { state.move.mode.value = it }, labels::getValue, Modifier.fillMaxWidth().testTag(ShellTags.MOVE_MODE), fill = true)
        when (mode) {
            MoveMode.PLACE_PART -> if (part.socket != null) {
                AngleControl(session, part.id, PlacementParams.OUTWARD, stringResource(Res.string.move_outward), params, thisSide, hidden)
                AngleControl(session, part.id, PlacementParams.LEAN, stringResource(Res.string.move_lean), params, thisSide, hidden)
            }
            MoveMode.ADD_SOCKET -> SocketControls(state, session)
        }
    }
}

@Composable
private fun SocketControls(state: AppState, session: CharacterSession) {
    session.document.collectAsState().value
    state.move.outward.collectAsState().value
    val picked by state.move.picked.collectAsState()
    val pair by state.move.pair.collectAsState()
    val outward = stringResource(Res.string.move_socket_outward)
    SettingRow(outward) {
        Spacer(Modifier.weight(1f))
        NumberField(
            state.pickedOutward(),
            state::setOutward,
            outward,
            Modifier.testTag(ShellTags.SOCKET_OUTWARD),
            unit = stringResource(Res.string.move_degrees),
            range = -HandSockets.MAX_OUTWARD..HandSockets.MAX_OUTWARD,
        )
    }
    val pairLabel = stringResource(Res.string.move_socket_pair)
    SettingRow(pairLabel) {
        Spacer(Modifier.weight(1f))
        Toggle(pair, { state.move.pair.value = it }, pairLabel, Modifier.testTag(ShellTags.SOCKET_PAIR))
    }
    val deletable = picked != null && !state.pickedSocketHolds()
    val enabled = if (deletable) Modifier else Modifier.alpha(DISABLED_ALPHA).semantics { disabled() }
    SettingRow("") {
        Button(
            stringResource(Res.string.move_socket_delete),
            { if (deletable) state.deletePickedSocket() },
            enabled.testTag(ShellTags.SOCKET_DELETE),
            kind = ButtonKind.Quiet,
            icon = SquashIcons.DeleteSocket,
        )
    }
}

/** One of the Move tool's angles as a slider, from straight at 0. */
@Composable
private fun AngleControl(session: CharacterSession, part: PartId, name: String, label: String, params: Map<String, Float>, thisSide: Boolean, hidden: Map<String, Float>) {
    val row = ParameterRow(name, params[name] ?: 0f, false, -PlacementParams.MAX_ANGLE, PlacementParams.MAX_ANGLE, null, stringResource(Res.string.move_degrees))
    key(part, name) { ParameterControl(session, part, row, thisSide, hidden[name], label) }
}
