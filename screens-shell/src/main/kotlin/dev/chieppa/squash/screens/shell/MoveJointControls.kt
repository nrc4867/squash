package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.deletePickedJoint
import dev.chieppa.squash.screens.pickedJointIsHandMade
import dev.chieppa.squash.screens.setJoint
import dev.chieppa.squash.screens.shownJoint
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.move_degrees
import dev.chieppa.squash.uidesign.generated.move_hinge_x
import dev.chieppa.squash.uidesign.generated.move_hinge_y
import dev.chieppa.squash.uidesign.generated.move_hinge_z
import dev.chieppa.squash.uidesign.generated.move_joint_delete
import dev.chieppa.squash.uidesign.generated.move_joint_high
import dev.chieppa.squash.uidesign.generated.move_joint_hinge
import dev.chieppa.squash.uidesign.generated.move_joint_limits
import dev.chieppa.squash.uidesign.generated.move_joint_low
import dev.chieppa.squash.uidesign.generated.move_joint_stretch
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.stringResource

private val LIMIT_RANGE = -HandJoints.MAX_LIMIT..HandJoints.MAX_LIMIT

/**
 * Add joint's fields: Hinge as X, Y or Z, the two Limits in degrees and Stretch, for the picked hand-made joint, each change one undo
 * step, or for the next joint placed while none is picked; and Delete joint, faint unless a hand-made joint is picked.
 */
@Composable
internal fun JointControls(state: AppState, session: CharacterSession) {
    session.document.collectAsState().value
    state.move.pickedJoint.collectAsState().value
    state.move.hinge.collectAsState().value
    state.move.limits.collectAsState().value
    state.move.stretch.collectAsState().value
    val shown = state.shownJoint()
    val hinges = mapOf(Hinge.X to stringResource(Res.string.move_hinge_x), Hinge.Y to stringResource(Res.string.move_hinge_y), Hinge.Z to stringResource(Res.string.move_hinge_z))
    SettingRow(stringResource(Res.string.move_joint_hinge)) {
        Spacer(Modifier.weight(1f))
        Segmented(HandJoints.HINGES, shown.hinge, { state.setJoint(it, shown.limits, shown.stretch) }, hinges::getValue, Modifier.testTag(ShellTags.JOINT_HINGE))
    }
    val degrees = stringResource(Res.string.move_degrees)
    SettingRow(stringResource(Res.string.move_joint_limits)) {
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberField(
                shown.limits.min,
                { state.setJoint(shown.hinge, Limits(it, shown.limits.max), shown.stretch) },
                stringResource(Res.string.move_joint_low),
                Modifier.testTag(ShellTags.JOINT_LOW),
                unit = degrees,
                range = LIMIT_RANGE,
            )
            NumberField(
                shown.limits.max,
                { state.setJoint(shown.hinge, Limits(shown.limits.min, it), shown.stretch) },
                stringResource(Res.string.move_joint_high),
                Modifier.testTag(ShellTags.JOINT_HIGH),
                unit = degrees,
                range = LIMIT_RANGE,
            )
        }
    }
    val stretch = stringResource(Res.string.move_joint_stretch)
    SettingRow(stretch) {
        Spacer(Modifier.weight(1f))
        Toggle(shown.stretch, { state.setJoint(shown.hinge, shown.limits, it) }, stretch, Modifier.testTag(ShellTags.JOINT_STRETCH))
    }
    val deletable = state.pickedJointIsHandMade()
    SettingRow("") {
        Button(
            stringResource(Res.string.move_joint_delete),
            { if (deletable) state.deletePickedJoint() },
            Modifier.enabledOrFaint(deletable).testTag(ShellTags.JOINT_DELETE),
            kind = ButtonKind.Quiet,
            icon = SquashIcons.DeleteSocket,
        )
    }
}
