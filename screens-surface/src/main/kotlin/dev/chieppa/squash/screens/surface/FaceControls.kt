package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MouthShapeValues
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_controls
import dev.chieppa.squash.uidesign.generated.face_lower_lip
import dev.chieppa.squash.uidesign.generated.face_open
import dev.chieppa.squash.uidesign.generated.face_round
import dev.chieppa.squash.uidesign.generated.face_smile
import dev.chieppa.squash.uidesign.generated.face_tongue
import dev.chieppa.squash.uidesign.generated.face_upper_lip
import dev.chieppa.squash.uidesign.generated.face_value
import dev.chieppa.squash.uidesign.generated.face_wide
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val HUNDREDTHS = 100f

private val UNIT = 0f..1f
private val SIGNED = -1f..1f

/** One of a mouth's seven controls: its label, its test tag's name, its range, and how it reads and writes the group's controls and a shape's values. */
internal class MouthControl(
    val label: StringResource,
    val name: String,
    val range: ClosedFloatingPointRange<Float>,
    val read: (MarkControls) -> Float,
    val write: (MarkControls, Float) -> MarkControls,
    val shapeRead: (MouthShapeValues) -> Float?,
    val shapeWrite: (MouthShapeValues, Float) -> MouthShapeValues,
)

/** The seven controls, in the order the inspector shows them. */
internal val MOUTH_CONTROLS = listOf(
    MouthControl(Res.string.face_open, "open", UNIT, { it.open }, { c, v -> c.copy(open = v) }, { it.open }, { s, v -> s.copy(open = v) }),
    MouthControl(Res.string.face_wide, "wide", SIGNED, { it.wide }, { c, v -> c.copy(wide = v) }, { it.wide }, { s, v -> s.copy(wide = v) }),
    MouthControl(Res.string.face_round, "round", UNIT, { it.round }, { c, v -> c.copy(round = v) }, { it.round }, { s, v -> s.copy(round = v) }),
    MouthControl(Res.string.face_smile, "smile", SIGNED, { it.smile }, { c, v -> c.copy(smile = v) }, { it.smile }, { s, v -> s.copy(smile = v) }),
    MouthControl(Res.string.face_upper_lip, "upper-lip", UNIT, { it.upperLip }, { c, v -> c.copy(upperLip = v) }, { it.upperLip }, { s, v -> s.copy(upperLip = v) }),
    MouthControl(Res.string.face_lower_lip, "lower-lip", SIGNED, { it.lowerLip }, { c, v -> c.copy(lowerLip = v) }, { it.lowerLip }, { s, v -> s.copy(lowerLip = v) }),
    MouthControl(Res.string.face_tongue, "tongue", UNIT, { it.tongue }, { c, v -> c.copy(tongue = v) }, { it.tongue }, { s, v -> s.copy(tongue = v) }),
)

/** The mouth's own seven controls, what it shows while no shape is picked; each slider's drag is one step of undo. */
@Composable
internal fun MouthControls(session: CharacterSession, picked: PickedGroup, group: MarkGroup) {
    val drag = rememberCommandDrag(session)
    DrawerSection(stringResource(Res.string.face_controls)) {
        MOUTH_CONTROLS.forEach { control ->
            ControlSlider(control.label, control.read(group.controls), FaceTags.control(control.name), drag::finish, control.range) {
                drag.update(SetMarkControls(picked.part, group.id, control.write(group.controls, it)))
            }
        }
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
