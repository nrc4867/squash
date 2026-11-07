package dev.chieppa.squash.screens.surface.pose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.rig.CurveBones
import dev.chieppa.squash.rig.PoseSolver
import dev.chieppa.squash.rig.bendTo
import dev.chieppa.squash.rig.chainBends
import dev.chieppa.squash.rig.clearCurve
import dev.chieppa.squash.rig.curveBones
import dev.chieppa.squash.screens.surface.Readout
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.pose_chain
import dev.chieppa.squash.uidesign.generated.pose_chain_head
import dev.chieppa.squash.uidesign.generated.pose_chain_neck
import dev.chieppa.squash.uidesign.generated.pose_chain_spine
import dev.chieppa.squash.uidesign.generated.pose_chain_tail
import dev.chieppa.squash.uidesign.generated.pose_curve_clear
import dev.chieppa.squash.uidesign.generated.pose_curve_squash
import dev.chieppa.squash.uidesign.generated.pose_curve_stretch
import dev.chieppa.squash.uidesign.generated.pose_degrees
import dev.chieppa.squash.uidesign.generated.pose_keep_volume
import dev.chieppa.squash.uidesign.generated.pose_length
import dev.chieppa.squash.uidesign.generated.pose_tool_body_curve
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** Test tags for the Body curve sections. */
object BodyCurveTags {
    const val LENGTH = "pose.curve.length"
    const val LENGTH_CHIP = "pose.curve.length.chip"
    const val KEEP_VOLUME = "pose.curve.volume"
    const val CLEAR = "pose.curve.clear"
    const val CHAIN = "pose.curve.chain"

    fun bend(row: String) = "pose.curve.bend.$row"
}

/**
 * Body curve's options: the curve's Length against the body's, with squash or stretch on a chip away from the whole, one undo step a
 * slider drag; Keep volume for every joint the length stretches; and Clear curve, which takes the curve away and rests the chain.
 * Each change bends the chain again from the pose as it stands, planted paws held.
 */
@Composable
internal fun BodyCurveSection(session: CharacterSession, solver: PoseSolver, pose: Pose, sliders: SliderGesture) {
    val bones = curveBones(solver.rig) ?: return
    val curve = pose.curve
    val length = curve?.length ?: 1f
    val percent = (length * PERCENT).roundToInt()
    val lengthLabel = stringResource(Res.string.pose_length)
    DrawerSection(stringResource(Res.string.pose_tool_body_curve)) {
        SettingRow(lengthLabel) {
            Slider(
                length,
                { value -> curve?.let { sliders.change(SetPose(solver.bendTo(pose, it.copy(length = value)).pose)) } },
                lengthLabel,
                Modifier.weight(1f).testTag(BodyCurveTags.LENGTH),
                LENGTH_RANGE,
                sliders::done,
            )
            Readout(stringResource(Res.string.edit_percent, percent))
            when {
                percent < PERCENT -> Chip(stringResource(Res.string.pose_curve_squash), Modifier.testTag(BodyCurveTags.LENGTH_CHIP))
                percent > PERCENT -> Chip(stringResource(Res.string.pose_curve_stretch), Modifier.testTag(BodyCurveTags.LENGTH_CHIP))
            }
        }
        val keeps = bones.stretched.all { pose.of(it.part, it.joint)?.keepVolume ?: true }
        SwitchRow(stringResource(Res.string.pose_keep_volume), keeps, { session.send(SetPose(keepingVolume(pose, bones, it))) }, BodyCurveTags.KEEP_VOLUME)
        if (curve != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Start) {
                Button(stringResource(Res.string.pose_curve_clear), { session.send(SetPose(solver.clearCurve(pose).pose)) }, Modifier.testTag(BodyCurveTags.CLEAR), kind = ButtonKind.Quiet)
            }
        }
    }
}

/** [pose] with every joint the curve's length stretches keeping volume, or lengthening alone when [keep] is false. */
private fun keepingVolume(pose: Pose, bones: CurveBones, keep: Boolean): Pose =
    bones.stretched.fold(pose) { out, name -> out.with((out.of(name.part, name.joint) ?: BonePose(name.part, name.joint)).copy(keepVolume = keep)) }

/** The chain's parts with how far the pose bends each, read only: Spine, Neck, Head and Tail. */
@Composable
internal fun ChainSection(solver: PoseSolver, pose: Pose) {
    val bends = chainBends(solver.rig, pose) ?: return
    DrawerSection(stringResource(Res.string.pose_chain)) {
        BendRow(stringResource(Res.string.pose_chain_spine), bends.spine, "spine")
        bends.neck?.let { BendRow(stringResource(Res.string.pose_chain_neck), it, "neck") }
        bends.head?.let { BendRow(stringResource(Res.string.pose_chain_head), it, "head") }
        bends.tail?.let { BendRow(stringResource(Res.string.pose_chain_tail), it, "tail") }
    }
}

@Composable
private fun BendRow(label: String, degrees: Float, row: String) {
    Box(Modifier.testTag(BodyCurveTags.bend(row))) {
        SettingRow(label) {
            Spacer(Modifier.weight(1f))
            Readout(stringResource(Res.string.pose_degrees, degrees.roundToInt()))
        }
    }
}
