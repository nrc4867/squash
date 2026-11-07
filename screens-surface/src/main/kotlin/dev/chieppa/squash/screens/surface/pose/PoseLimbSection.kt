package dev.chieppa.squash.screens.surface.pose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBoneAngle
import dev.chieppa.squash.document.SetBoneStretch
import dev.chieppa.squash.document.SetKeepVolume
import dev.chieppa.squash.document.SetPlanted
import dev.chieppa.squash.document.SetRiding
import dev.chieppa.squash.rig.Limb
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.surface.Readout
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.pose_bend
import dev.chieppa.squash.uidesign.generated.pose_degrees
import dev.chieppa.squash.uidesign.generated.pose_keep_volume
import dev.chieppa.squash.uidesign.generated.pose_length
import dev.chieppa.squash.uidesign.generated.pose_planted
import dev.chieppa.squash.uidesign.generated.pose_rides
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** How far the Length slider stretches a limb either way, as a factor of its rest length. */
internal val LENGTH_RANGE = 0.5f..1.5f

/** How far the Bend slider runs for a knee with no limits, in degrees. */
private val FREE_BEND = -180f..180f

/**
 * The picked limb, named by its paw: whether it is planted and whether, lifted, it rides with the body; its knee's Bend, read and set
 * about the hinge; the Length its end joint gives the lower bone, with Keep volume, where its template offers a stretch there. With
 * Mirror on a change writes the twin too.
 */
@Composable
internal fun LimbSection(state: AppState, session: CharacterSession, rig: Rig, character: Character, limb: Limb, names: (PartId) -> String, sliders: SliderGesture) {
    val mirror by state.mirror.collectAsState()
    val pose = character.pose
    val knee = rig.bones[limb.lower]
    val end = rig.bones[limb.end]
    val bend = pose.of(knee.name.part, knee.name.joint)?.degrees ?: 0f
    val ankle = pose.of(end.name.part, end.name.joint)
    val bendLabel = stringResource(Res.string.pose_bend)
    val lengthLabel = stringResource(Res.string.pose_length)
    DrawerSection(names(limb.id)) {
        SwitchRow(stringResource(Res.string.pose_planted), limb.id in pose.planted, { session.send(SetPlanted(limb.id, it)) }, PoseInspectorTags.LIMB_PLANTED)
        SwitchRow(stringResource(Res.string.pose_rides), limb.id !in pose.still, { session.send(SetRiding(limb.id, it)) }, PoseInspectorTags.RIDES)
        SettingRow(bendLabel) {
            val range = knee.limits?.let { it.min..it.max } ?: FREE_BEND
            Slider(bend, { sliders.change(SetBoneAngle(knee.name.part, knee.name.joint, it, mirror)) }, bendLabel, Modifier.weight(1f).testTag(PoseInspectorTags.BEND), range, sliders::done)
            Readout(stringResource(Res.string.pose_degrees, bend.roundToInt()))
        }
        if (end.stretch) {
            val stretch = ankle?.stretch ?: 1f
            SettingRow(lengthLabel) {
                Slider(stretch, { sliders.change(SetBoneStretch(end.name.part, end.name.joint, it, mirror)) }, lengthLabel, Modifier.weight(1f).testTag(PoseInspectorTags.LENGTH), LENGTH_RANGE, sliders::done)
                Readout(stringResource(Res.string.edit_percent, (stretch * PERCENT).roundToInt()))
            }
            SwitchRow(stringResource(Res.string.pose_keep_volume), ankle?.keepVolume ?: true, { session.send(SetKeepVolume(end.name.part, end.name.joint, it, mirror)) }, PoseInspectorTags.KEEP_VOLUME)
        }
    }
}
