package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.GazeCheats
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MouthShape
import dev.chieppa.squash.document.SetGazeCheats
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_camera_bias
import dev.chieppa.squash.uidesign.generated.face_controls
import dev.chieppa.squash.uidesign.generated.face_dilation
import dev.chieppa.squash.uidesign.generated.face_gaze
import dev.chieppa.squash.uidesign.generated.face_mouth_frown
import dev.chieppa.squash.uidesign.generated.face_mouth_grimace
import dev.chieppa.squash.uidesign.generated.face_mouth_o
import dev.chieppa.squash.uidesign.generated.face_mouth_smile
import dev.chieppa.squash.uidesign.generated.face_open
import dev.chieppa.squash.uidesign.generated.face_shape
import dev.chieppa.squash.uidesign.generated.face_size_compensation
import dev.chieppa.squash.uidesign.generated.face_value
import dev.chieppa.squash.uidesign.generated.face_width
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val HUNDREDTHS = 100f

/** How far a dilation slider reaches either way, as a share of the pupil's size. */
private const val MOST_DILATION = 1f

/** How wide a mouth's width control reaches. */
private val WIDTH_RANGE = 0.5f..1.5f

private val MOUTH_SHAPE_NAMES = mapOf(
    MouthShape.SMILE to Res.string.face_mouth_smile,
    MouthShape.FROWN to Res.string.face_mouth_frown,
    MouthShape.O to Res.string.face_mouth_o,
    MouthShape.GRIMACE to Res.string.face_mouth_grimace,
)

@Composable
internal fun EyeControls(state: AppState, session: CharacterSession, picked: PickedGroup, group: MarkGroup, eyes: List<MarkGroup>) {
    val drag = rememberCommandDrag(session)
    fun set(change: (MarkControls) -> MarkControls) {
        val both = if (state.face.bothEyes) eyes else listOf(group)
        drag.update(Together(both.map { SetMarkControls(picked.part, it.id, change(it.controls)) }))
    }
    DrawerSection(stringResource(Res.string.face_controls)) {
        ControlSlider(Res.string.face_open, group.controls.open, FaceTags.OPEN, drag::finish) { value -> set { it.copy(open = value) } }
        ControlSlider(Res.string.face_shape, group.controls.expressionBlend, FaceTags.SHAPE, drag::finish) { value -> set { it.copy(expressionBlend = value) } }
        ControlSlider(Res.string.face_dilation, group.controls.dilation, FaceTags.DILATION, drag::finish, -MOST_DILATION..MOST_DILATION, shown = { ((1f + it) * HUNDREDTHS).roundToInt() / HUNDREDTHS }) { value -> set { it.copy(dilation = value) } }
    }
}

@Composable
internal fun MouthControls(session: CharacterSession, picked: PickedGroup, group: MarkGroup) {
    val drag = rememberCommandDrag(session)
    fun set(controls: MarkControls) = drag.update(SetMarkControls(picked.part, group.id, controls))
    DrawerSection(stringResource(Res.string.face_controls)) {
        ControlSlider(Res.string.face_open, group.controls.open, FaceTags.OPEN, drag::finish) { set(group.controls.copy(open = it)) }
        val names = MOUTH_SHAPE_NAMES.mapValues { stringResource(it.value) }
        SettingRow(stringResource(Res.string.face_shape)) {
            Segmented<MouthShape?>(MouthShape.entries, group.controls.mouthShape, { chosen ->
                session.send(SetMarkControls(picked.part, group.id, group.controls.copy(mouthShape = chosen.takeUnless { it == group.controls.mouthShape })))
            }, { it?.let(names::getValue).orEmpty() }, Modifier.testTag(FaceTags.SHAPE))
        }
        ControlSlider(Res.string.face_width, group.controls.width, FaceTags.WIDTH, drag::finish, WIDTH_RANGE) { set(group.controls.copy(width = it)) }
    }
}

@Composable
private fun ControlSlider(
    label: StringResource,
    value: Float,
    tag: String,
    onFinish: () -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    shown: (Float) -> Float = { (it * HUNDREDTHS).roundToInt() / HUNDREDTHS },
    onChange: (Float) -> Unit,
) {
    val name = stringResource(label)
    SettingRow(name) {
        Slider(value, onChange, name, Modifier.weight(1f).testTag(tag), valueRange = range, onValueChangeFinished = onFinish)
        RowText(stringResource(Res.string.face_value, shown(value)), subtle = true)
    }
}

@Composable
internal fun GazeSection(session: CharacterSession, character: Character) {
    val drag = rememberCommandDrag(session)
    val cheats: GazeCheats = character.gazeCheats
    DrawerSection(stringResource(Res.string.face_gaze), divider = false) {
        ControlSlider(Res.string.face_camera_bias, cheats.cameraBias, FaceTags.BIAS, drag::finish) { drag.update(SetGazeCheats(cheats.copy(cameraBias = it))) }
        val size = stringResource(Res.string.face_size_compensation)
        SettingRow(size) {
            Spacer(Modifier.weight(1f))
            Toggle(cheats.sizeCompensation, { session.send(SetGazeCheats(cheats.copy(sizeCompensation = it))) }, size, Modifier.testTag(FaceTags.SIZE))
        }
    }
}
