package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.face.eyeLandmark
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_controls
import dev.chieppa.squash.uidesign.generated.face_eye_height
import dev.chieppa.squash.uidesign.generated.face_eye_look_out
import dev.chieppa.squash.uidesign.generated.face_eye_tilt
import dev.chieppa.squash.uidesign.generated.face_highlight_size
import dev.chieppa.squash.uidesign.generated.face_iris_size
import dev.chieppa.squash.uidesign.generated.face_layers
import dev.chieppa.squash.uidesign.generated.face_new_eyes
import dev.chieppa.squash.uidesign.generated.face_pupil_size
import dev.chieppa.squash.uidesign.generated.face_role_brow
import dev.chieppa.squash.uidesign.generated.face_role_highlight
import dev.chieppa.squash.uidesign.generated.face_role_iris
import dev.chieppa.squash.uidesign.generated.face_role_lashes
import dev.chieppa.squash.uidesign.generated.face_role_lid
import dev.chieppa.squash.uidesign.generated.face_role_pupil
import dev.chieppa.squash.uidesign.generated.face_role_white
import dev.chieppa.squash.uidesign.generated.face_slit
import dev.chieppa.squash.uidesign.generated.face_width
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The painted eye's id Add eyes gives. */
private const val PAINTED_EYES_ID = "eyes"

/** Where an eye sits on a head whose template names no eye landmark. */
private const val DEFAULT_U = 0.6f
private const val DEFAULT_V = 0.2f

/** How far the opening's and iris's sliders reach, as shares of the head's u and v. */
private val OPENING_RANGE = 0.01f..0.15f
private val IRIS_RANGE = 0.01f..0.1f

/** How far the tilt and look-out sliders reach, in degrees. */
private val TURN_RANGE = -45f..45f

private val SWATCH_GAP = 8.dp

/** Test tags for the painted eye's controls. */
object PaintedEyeTags {
    const val NEW = "face.painted.new"
    const val WIDTH = "face.painted.width"
    const val HEIGHT = "face.painted.height"
    const val TILT = "face.painted.tilt"
    const val LOOK_OUT = "face.painted.look-out"
    const val IRIS = "face.painted.iris"
    const val PUPIL = "face.painted.pupil"
    const val HIGHLIGHT = "face.painted.highlight"
    const val SLIT = "face.painted.slit"

    fun fill(name: String) = "face.painted.fill.$name"

    fun line(name: String) = "face.painted.line.$name"
}

/**
 * The Eyes of a head on a one surface: its painted eye's controls and its layers, each fill with its swatch and each line switched on or
 * off. With the front angle version picked, they work on the eye's front look, which starts as its look. A head with no painted eye
 * offers to add one at its eye landmark.
 */
@Composable
internal fun PaintedEyeSection(state: AppState, session: CharacterSession, character: Character, holder: PartId) {
    val eye = character.paintedEyes.firstOrNull { it.anchor.part == holder }
    if (eye == null) {
        DrawerSection(divider = false) {
            Button(stringResource(Res.string.face_new_eyes), {
                val landmark = character.part(holder)?.let { eyeLandmark(session.builtTemplate(it)) }
                val place = Anchor(holder, BODY_PATCH, landmark?.u ?: DEFAULT_U, landmark?.v ?: DEFAULT_V)
                session.send(SetPaintedEye(PAINTED_EYES_ID, PaintedEye(PAINTED_EYES_ID, place)))
            }, Modifier.fillMaxWidth().testTag(PaintedEyeTags.NEW), kind = ButtonKind.Quiet, icon = SquashIcons.Marks)
        }
        return
    }
    val front = state.face.version == FACE_FRONT
    val look = if (front) eye.front ?: eye.look else eye.look
    val drag = rememberCommandDrag(session)
    fun changed(change: (EyeLook) -> EyeLook) = SetPaintedEye(eye.id, if (front) eye.copy(front = change(look)) else eye.copy(look = change(look)))
    fun set(change: (EyeLook) -> EyeLook) = drag.update(changed(change))
    fun now(change: (EyeLook) -> EyeLook) = session.send(changed(change))
    DrawerSection(stringResource(Res.string.face_controls)) {
        ControlSlider(Res.string.face_width, look.width, PaintedEyeTags.WIDTH, drag::finish, OPENING_RANGE) { v -> set { it.copy(width = v) } }
        ControlSlider(Res.string.face_eye_height, look.height, PaintedEyeTags.HEIGHT, drag::finish, OPENING_RANGE) { v -> set { it.copy(height = v) } }
        ControlSlider(Res.string.face_eye_tilt, look.tilt, PaintedEyeTags.TILT, drag::finish, TURN_RANGE, shown = { it.roundToWhole() }) { v -> set { it.copy(tilt = v) } }
        ControlSlider(Res.string.face_eye_look_out, look.lookOut, PaintedEyeTags.LOOK_OUT, drag::finish, TURN_RANGE, shown = { it.roundToWhole() }) { v -> set { it.copy(lookOut = v) } }
        ControlSlider(Res.string.face_iris_size, look.irisSize, PaintedEyeTags.IRIS, drag::finish, IRIS_RANGE) { v -> set { it.copy(irisSize = v) } }
        ControlSlider(Res.string.face_pupil_size, look.pupilSize, PaintedEyeTags.PUPIL, drag::finish) { v -> set { it.copy(pupilSize = v) } }
        ControlSlider(Res.string.face_highlight_size, look.highlightSize, PaintedEyeTags.HIGHLIGHT, drag::finish) { v -> set { it.copy(highlightSize = v) } }
        LineToggle(Res.string.face_slit, PaintedEyeTags.SLIT, look.slit) { on -> now { it.copy(slit = on) } }
    }
    DrawerSection(stringResource(Res.string.face_layers), divider = false) {
        LineToggle(Res.string.face_role_brow, PaintedEyeTags.line("brow"), look.brow) { on -> now { it.copy(brow = on) } }
        LineToggle(Res.string.face_role_lashes, PaintedEyeTags.line("lashes"), look.lashes) { on -> now { it.copy(lashes = on) } }
        LineToggle(Res.string.face_role_lid, PaintedEyeTags.line("lid"), look.lid) { on -> now { it.copy(lid = on) } }
        FillRow(character, session, Res.string.face_role_highlight, "highlight", look.highlight) { slot -> now { it.copy(highlight = slot) } }
        FillRow(character, session, Res.string.face_role_pupil, "pupil", look.pupil) { slot -> now { it.copy(pupil = slot) } }
        FillRow(character, session, Res.string.face_role_iris, "iris", look.iris) { slot -> now { it.copy(iris = slot) } }
        FillRow(character, session, Res.string.face_role_white, "white", look.white) { slot -> now { it.copy(white = slot) } }
    }
}

private fun Float.roundToWhole(): Float = kotlin.math.round(this)

/** A fill of the eye: its swatch, which opens the palette, and its name. */
@Composable
private fun FillRow(character: Character, session: CharacterSession, label: StringResource, name: String, slot: SlotName?, onPick: (SlotName) -> Unit) {
    val title = stringResource(label)
    ListRow(false, {}) {
        SlotChoice(character.palette, slot, title, PaintedEyeTags.fill(name), { PaintedEyeTags.fill("$name.${it.value}") }, onPick, editor = session)
        Spacer(Modifier.width(SWATCH_GAP))
        RowText(title, Modifier.weight(1f))
    }
}

/** A line or setting of the eye switched on or off. */
@Composable
private fun LineToggle(label: StringResource, tag: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val title = stringResource(label)
    SettingRow(title) {
        Spacer(Modifier.weight(1f))
        Toggle(on, onChange, title, Modifier.testTag(tag))
    }
}
