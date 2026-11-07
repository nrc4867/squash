package dev.chieppa.squash.screens.surface.pose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.SetBoneMode
import dev.chieppa.squash.document.SetBoneTurn
import dev.chieppa.squash.rig.BoneAngles
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.angles
import dev.chieppa.squash.rig.turnReach
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.surface.Readout
import dev.chieppa.squash.screens.surface.rig.RigNames
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.pose_degrees
import dev.chieppa.squash.uidesign.generated.pose_free
import dev.chieppa.squash.uidesign.generated.pose_lean
import dev.chieppa.squash.uidesign.generated.pose_limited
import dev.chieppa.squash.uidesign.generated.pose_limits
import dev.chieppa.squash.uidesign.generated.pose_limits_none
import dev.chieppa.squash.uidesign.generated.pose_limits_range
import dev.chieppa.squash.uidesign.generated.pose_locked
import dev.chieppa.squash.uidesign.generated.pose_tip
import dev.chieppa.squash.uidesign.generated.pose_tool_turn
import dev.chieppa.squash.uidesign.generated.pose_turn
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** How far a slider runs for an angle its limits do not hold, in degrees. */
private val WHOLE_TURN = -180f..180f

/**
 * The picked bone's turn, its name on a chip: Tip, Turn and Lean sliders with their degrees, the Limits chip reading the template's
 * range, and Free, Limited or Locked. Sliders run over the limits unless the bone is free, and a locked bone's sliders move nothing.
 * With Mirror on a change writes the twin too, which turns the mirrored way.
 */
@Composable
internal fun TurnSection(state: AppState, session: CharacterSession, rig: Rig, character: Character, name: BoneName, names: RigNames, sliders: SliderGesture) {
    val bone = rig.bone(name) ?: return
    val mirror by state.mirror.collectAsState()
    val set = character.pose.of(name.part, name.joint)
    val mode = set?.mode ?: JointMode.LIMITED
    val angles = set.angles()
    val limits = bone.limits.takeIf { mode != JointMode.FREE }
    val reach = limits?.turnReach()
    fun send(next: BoneAngles) {
        if (mode != JointMode.LOCKED) sliders.change(SetBoneTurn(name.part, name.joint, next.tip, next.turn, next.lean, mirror))
    }
    val title = stringResource(Res.string.pose_tool_turn)
    DrawerSection(title) {
        SettingRow(title) {
            Spacer(Modifier.weight(1f))
            Chip(names.of(name), Modifier.testTag(PoseInspectorTags.BONE), kind = ChipKind.On)
        }
        AngleRow(stringResource(Res.string.pose_tip), angles.tip, limits?.let { it.min..it.max } ?: WHOLE_TURN, PoseInspectorTags.TIP, sliders) { send(angles.copy(tip = it)) }
        AngleRow(stringResource(Res.string.pose_turn), angles.turn, reach?.let { -it..it } ?: WHOLE_TURN, PoseInspectorTags.TURN, sliders) { send(angles.copy(turn = it)) }
        AngleRow(stringResource(Res.string.pose_lean), angles.lean, reach?.let { -it..it } ?: WHOLE_TURN, PoseInspectorTags.LEAN, sliders) { send(angles.copy(lean = it)) }
        SettingRow(stringResource(Res.string.pose_limits)) {
            Spacer(Modifier.weight(1f))
            val range = bone.limits?.let { stringResource(Res.string.pose_limits_range, it.min.roundToInt(), it.max.roundToInt()) } ?: stringResource(Res.string.pose_limits_none)
            Chip(range, Modifier.testTag(PoseInspectorTags.LIMITS), mono = true)
        }
        val labels = mapOf(JointMode.FREE to stringResource(Res.string.pose_free), JointMode.LIMITED to stringResource(Res.string.pose_limited), JointMode.LOCKED to stringResource(Res.string.pose_locked))
        Segmented(JointMode.entries, mode, { session.send(SetBoneMode(name.part, name.joint, it, mirror)) }, { labels.getValue(it) }, Modifier.testTag(PoseInspectorTags.MODE), fill = true)
    }
}

/** A slider for one angle with its degrees beside it; a range of no width, as a hinge's turn has, runs a whole turn but is held there. */
@Composable
private fun AngleRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String, sliders: SliderGesture, onChange: (Float) -> Unit) {
    SettingRow(label) {
        val shown = if (range.start < range.endInclusive) range else WHOLE_TURN
        Slider(value, onChange, label, Modifier.weight(1f).testTag(tag), shown, sliders::done)
        Readout(stringResource(Res.string.pose_degrees, value.roundToInt()))
    }
}
