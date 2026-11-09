package dev.chieppa.squash.screens.surface.pose

import dev.chieppa.squash.screens.edit.SliderGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import dev.chieppa.stretch.theme.StretchSizes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ResetBone
import dev.chieppa.squash.document.ResetPose
import dev.chieppa.squash.document.SetPlanted
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.PoseSolver
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.rig.limbNames
import dev.chieppa.squash.screens.rig.rememberRig
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.surface.rig.RigNames
import dev.chieppa.squash.screens.surface.rig.rigNames
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.pose_firm
import dev.chieppa.squash.uidesign.generated.pose_firm_hint
import dev.chieppa.squash.uidesign.generated.pose_pin_paw
import dev.chieppa.squash.uidesign.generated.pose_planted
import dev.chieppa.squash.uidesign.generated.pose_reset_bone
import dev.chieppa.squash.uidesign.generated.pose_reset_pose
import dev.chieppa.squash.uidesign.generated.pose_stretch
import dev.chieppa.squash.uidesign.generated.pose_stretch_hint
import dev.chieppa.squash.uidesign.generated.pose_tool_drag
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind

import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

/** Test tags for the Pose inspector. */
object PoseInspectorTags {
    const val FIRM = "pose.drag.firm"
    const val STRETCH = "pose.drag.stretch"
    const val LIMB = "pose.limb"
    const val LIMB_PLANTED = "pose.limb.planted"
    const val RIDES = "pose.limb.rides"
    const val BEND = "pose.limb.bend"
    const val CURL = "pose.limb.curl"
    const val LENGTH = "pose.limb.length"
    const val KEEP_VOLUME = "pose.limb.volume"
    const val BONE = "pose.turn.bone"
    const val TIP = "pose.turn.tip"
    const val TURN = "pose.turn.turn"
    const val LEAN = "pose.turn.lean"
    const val LIMITS = "pose.turn.limits"
    const val MODE = "pose.turn.mode"
    const val RESET_BONE = "pose.reset.bone"
    const val RESET_POSE = "pose.reset.pose"

    fun pin(limb: PartId) = "pose.planted.${limb.value}"
}

/**
 * The Pose inspector: the armed tool's options first, Drag's Firm and Stretch; then the picked limb and the picked bone's turn, the
 * Turn section first while Turn is armed; then the paws with their pins; then Reset bone and Reset pose. With Body curve armed, the
 * curve's options, the paws with their pins, and how far the curve bends each part of the chain.
 */
@Composable
fun PoseInspector(state: AppState, session: CharacterSession) {
    val tool by state.tool.collectAsState()
    val document by session.document.collectAsState()
    val rig = rememberRig(state) ?: return
    val solver = state.pose.solverFor(rig)
    val names = rigNames(document.character, state.templates, rig)
    val limbs = limbNames(document.character, state.templates)
    val sliders = remember(session) { SliderGesture(session) }
    val limb = state.pose.limb?.let(solver.chains::limb)
    val bone = state.pose.bone ?: limb?.let { rig.bones[it.lower].name }
    if (tool == BuildTool.BODY_CURVE) {
        BodyCurveSection(session, solver, document.character.pose, sliders)
        PlantedSection(session, solver, document.character, limbs)
        ChainSection(solver, document.character.pose)
        return
    }
    if (tool == BuildTool.DRAG) DragOptions(state)
    if (tool == BuildTool.TURN && bone != null) TurnSection(state, session, rig, document.character, bone, names, sliders)
    if (limb != null) LimbSection(state, session, rig, document.character, limb, limbs, sliders)
    if (tool != BuildTool.TURN && bone != null) TurnSection(state, session, rig, document.character, bone, names, sliders)
    PlantedSection(session, solver, document.character, limbs)
    ResetButtons(state, session, bone)
}

/** A row with [label] at its start, as wide as it needs, and a switch at its end, its tooltip [hint]. */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String, hint: String? = null) {
    val row = @Composable {
        Row(Modifier.fillMaxWidth().heightIn(min = StretchSizes.ctl), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, style = Stretch.type.body.merge(TextStyle(color = Stretch.colors.inkControl)), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Toggle(checked, onChange, label, Modifier.testTag(tag))
        }
    }
    if (hint != null) Tooltip(hint) { row() } else row()
}

@Composable
private fun DragOptions(state: AppState) {
    DrawerSection(stringResource(Res.string.pose_tool_drag)) {
        SwitchRow(stringResource(Res.string.pose_firm), state.pose.firm, { state.pose.firm = it }, PoseInspectorTags.FIRM, stringResource(Res.string.pose_firm_hint))
        SwitchRow(stringResource(Res.string.pose_stretch), state.pose.stretch, { state.pose.stretch = it }, PoseInspectorTags.STRETCH, stringResource(Res.string.pose_stretch_hint))
    }
}

/** One row a paw, with the pin glyph and a switch that plants or frees it. */
@Composable
private fun PlantedSection(session: CharacterSession, solver: PoseSolver, character: Character, names: (PartId) -> String) {
    val planting = solver.chains.limbs.filter { it.plants }
    if (planting.isEmpty()) return
    DrawerSection(stringResource(Res.string.pose_planted)) {
        planting.forEach { limb ->
            val name = names(limb.id)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SquashIcons.Pin, Stretch.colors.inkMuted)
                SettingRow(name) {
                    Spacer(Modifier.weight(1f))
                    Toggle(limb.id in character.pose.planted, { session.send(SetPlanted(limb.id, it)) }, stringResource(Res.string.pose_pin_paw, name), Modifier.testTag(PoseInspectorTags.pin(limb.id)))
                }
            }
        }
    }
}

/** Reset bone, for the picked bone and with Mirror its twin, and Reset pose, quiet, at the foot. */
@Composable
private fun ResetButtons(state: AppState, session: CharacterSession, bone: BoneName?) {
    val mirror by state.mirror.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (bone != null) {
            Button(stringResource(Res.string.pose_reset_bone), { session.send(ResetBone(bone.part, bone.joint, bothSides = mirror)) }, Modifier.testTag(PoseInspectorTags.RESET_BONE), kind = ButtonKind.Quiet)
        }
        Button(stringResource(Res.string.pose_reset_pose), { session.send(ResetPose) }, Modifier.testTag(PoseInspectorTags.RESET_POSE), kind = ButtonKind.Quiet)
    }
}
