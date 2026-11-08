package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.character.build.isGeneric
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.deletePickedSocket
import dev.chieppa.squash.screens.move.MoveMode
import dev.chieppa.squash.screens.pickedOutward
import dev.chieppa.squash.screens.pickedSocketHolds
import dev.chieppa.squash.screens.setOutward
import dev.chieppa.squash.screens.setRest
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.move_degrees
import dev.chieppa.squash.uidesign.generated.move_lean
import dev.chieppa.squash.uidesign.generated.move_mode_add_joint
import dev.chieppa.squash.uidesign.generated.move_mode_add_landmark
import dev.chieppa.squash.uidesign.generated.move_mode_add_landmark_tip
import dev.chieppa.squash.uidesign.generated.move_mode_add_joint_tip
import dev.chieppa.squash.uidesign.generated.move_mode_add_socket_tip
import dev.chieppa.squash.uidesign.generated.move_mode_place_tip
import dev.chieppa.squash.uidesign.generated.move_mode_add_socket
import dev.chieppa.squash.uidesign.generated.move_mode_place
import dev.chieppa.squash.uidesign.generated.move_outward
import dev.chieppa.squash.uidesign.generated.move_rest
import dev.chieppa.squash.uidesign.generated.move_rest_back
import dev.chieppa.squash.uidesign.generated.move_rest_down
import dev.chieppa.squash.uidesign.generated.move_rest_forward
import dev.chieppa.squash.uidesign.generated.move_rest_out
import dev.chieppa.squash.uidesign.generated.move_rest_up
import dev.chieppa.squash.uidesign.generated.move_section
import dev.chieppa.squash.uidesign.generated.move_socket_delete
import dev.chieppa.squash.uidesign.generated.move_socket_outward
import dev.chieppa.squash.uidesign.generated.move_socket_pair
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.Dropdown
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val REST_WIDTH = 120.dp

/** The Move modes as the board's two-by-two grid draws them, a row at a time. */
private val MODE_ROWS = listOf(listOf(MoveMode.PLACE_PART, MoveMode.ADD_SOCKET), listOf(MoveMode.ADD_JOINT, MoveMode.ADD_LANDMARK))

private val MODE_LABELS: Map<MoveMode, StringResource> = mapOf(
    MoveMode.PLACE_PART to Res.string.move_mode_place,
    MoveMode.ADD_SOCKET to Res.string.move_mode_add_socket,
    MoveMode.ADD_JOINT to Res.string.move_mode_add_joint,
    MoveMode.ADD_LANDMARK to Res.string.move_mode_add_landmark,
)

private val MODE_TIPS: Map<MoveMode, StringResource> = mapOf(
    MoveMode.PLACE_PART to Res.string.move_mode_place_tip,
    MoveMode.ADD_SOCKET to Res.string.move_mode_add_socket_tip,
    MoveMode.ADD_JOINT to Res.string.move_mode_add_joint_tip,
    MoveMode.ADD_LANDMARK to Res.string.move_mode_add_landmark_tip,
)

private val REST_LABELS: Map<RestDirection, StringResource> = mapOf(
    RestDirection.FORWARD to Res.string.move_rest_forward,
    RestDirection.BACK to Res.string.move_rest_back,
    RestDirection.UP to Res.string.move_rest_up,
    RestDirection.DOWN to Res.string.move_rest_down,
    RestDirection.OUT to Res.string.move_rest_out,
)

/**
 * The Move tool's section of the Shape inspector: its four modes, Place part, Add socket, Add joint and Add landmark. Place part holds
 * the selected part's own angles when it sits on a socket, and a generic part's rest direction; Add socket holds the outward angle
 * and pair flag the next socket takes, the angle editing the picked hand-made socket instead, and Delete socket for a picked socket
 * that holds nothing; Add joint holds the hinge, limits and stretch, of the picked joint or the next one, and Delete joint.
 */
@Composable
internal fun MoveSection(state: AppState, session: CharacterSession, part: PartInstance, template: PartTemplate, params: Map<String, Float>, thisSide: Boolean, hidden: Map<String, Float>) {
    val mode by state.move.mode.collectAsState()
    val labels = MODE_LABELS.mapValues { stringResource(it.value) }
    val tips = MODE_TIPS.mapValues { stringResource(it.value) }
    DrawerSection(stringResource(Res.string.move_section)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MODE_ROWS.forEachIndexed { i, row ->
                val tag = if (i == 0) ShellTags.MOVE_MODE else ShellTags.MOVE_MODE_MORE
                Segmented(row, mode, { state.move.mode.value = it }, labels::getValue, Modifier.fillMaxWidth().testTag(tag), fill = true, tooltip = tips::getValue)
            }
        }
        when (mode) {
            MoveMode.PLACE_PART -> if (part.socket != null) {
                AngleControl(session, part.id, PlacementParams.OUTWARD, stringResource(Res.string.move_outward), params, thisSide, hidden)
                AngleControl(session, part.id, PlacementParams.LEAN, stringResource(Res.string.move_lean), params, thisSide, hidden)
                if (template.isGeneric) RestControl(state, part.id, template, params)
            }
            MoveMode.ADD_SOCKET -> SocketControls(state, session)
            MoveMode.ADD_JOINT -> JointControls(state, session)
            MoveMode.ADD_LANDMARK -> Unit
        }
    }
}

/** Which way a generic part rests, its template's way until one is chosen; a choice is one undo step. */
@Composable
private fun RestControl(state: AppState, part: PartId, template: PartTemplate, params: Map<String, Float>) {
    val labels = REST_LABELS.mapValues { stringResource(it.value) }
    val shown = RestDirection.of(params) ?: RestDirection.entries.firstOrNull { it.direction == template.axis.rest } ?: RestDirection.FORWARD
    val label = stringResource(Res.string.move_rest)
    SettingRow(label) {
        Spacer(Modifier.weight(1f))
        Dropdown(RestDirection.entries, shown, { state.setRest(part, it) }, labels::getValue, label, Modifier.width(REST_WIDTH).testTag(ShellTags.MOVE_REST))
    }
}

/** The outward angle and pair flag the next hand-made socket takes, the angle editing the picked socket instead, and Delete socket. */
@Composable
internal fun SocketControls(state: AppState, session: CharacterSession) {
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
    SettingRow("") {
        Button(
            stringResource(Res.string.move_socket_delete),
            { state.deletePickedSocket() },
            Modifier.testTag(ShellTags.SOCKET_DELETE),
            kind = ButtonKind.Quiet,
            icon = SquashIcons.DeleteSocket,
            enabled = deletable,
        )
    }
}

/** One of the Move tool's angles as a slider, from straight at 0. */
@Composable
private fun AngleControl(session: CharacterSession, part: PartId, name: String, label: String, params: Map<String, Float>, thisSide: Boolean, hidden: Map<String, Float>) {
    val row = ParameterRow(name, params[name] ?: 0f, false, -PlacementParams.MAX_ANGLE, PlacementParams.MAX_ANGLE, null, stringResource(Res.string.move_degrees))
    key(part, name) { ParameterControl(session, part, row, thisSide, hidden[name], label) }
}
