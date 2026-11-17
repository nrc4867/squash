package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MouthShape
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_controls
import dev.chieppa.squash.uidesign.generated.face_mouth_frown
import dev.chieppa.squash.uidesign.generated.face_mouth_grimace
import dev.chieppa.squash.uidesign.generated.face_mouth_o
import dev.chieppa.squash.uidesign.generated.face_mouth_smile
import dev.chieppa.squash.uidesign.generated.face_open
import dev.chieppa.squash.uidesign.generated.face_shape
import dev.chieppa.squash.uidesign.generated.face_value
import dev.chieppa.squash.uidesign.generated.face_width
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val HUNDREDTHS = 100f

/** How wide a mouth's width control reaches. */
private val WIDTH_RANGE = 0.5f..1.5f

private val MOUTH_SHAPE_NAMES = mapOf(
    MouthShape.SMILE to Res.string.face_mouth_smile,
    MouthShape.FROWN to Res.string.face_mouth_frown,
    MouthShape.O to Res.string.face_mouth_o,
    MouthShape.GRIMACE to Res.string.face_mouth_grimace,
)

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
internal fun ControlSlider(
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
