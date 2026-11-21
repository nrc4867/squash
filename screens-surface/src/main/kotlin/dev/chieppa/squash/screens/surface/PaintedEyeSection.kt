package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Box
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
import dev.chieppa.squash.document.MOST_LASHES
import dev.chieppa.squash.document.MOST_GAZE
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetEyeFromFront
import dev.chieppa.squash.document.SetEyeGaze
import dev.chieppa.squash.document.SetEyeRoundness
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.frontAmount
import dev.chieppa.squash.document.roundness
import dev.chieppa.squash.document.styled
import dev.chieppa.squash.document.withHighlight
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_colors
import dev.chieppa.squash.uidesign.generated.face_flat
import dev.chieppa.squash.uidesign.generated.face_from_front
import dev.chieppa.squash.uidesign.generated.face_gaze
import dev.chieppa.squash.uidesign.generated.face_gaze_forward
import dev.chieppa.squash.uidesign.generated.face_gaze_up
import dev.chieppa.squash.uidesign.generated.face_highlight_size
import dev.chieppa.squash.uidesign.generated.face_more
import dev.chieppa.squash.uidesign.generated.face_new_eyes
import dev.chieppa.squash.uidesign.generated.face_pupil
import dev.chieppa.squash.uidesign.generated.face_pupil_round
import dev.chieppa.squash.uidesign.generated.face_pupil_slit
import dev.chieppa.squash.uidesign.generated.face_role_brow
import dev.chieppa.squash.uidesign.generated.face_role_iris
import dev.chieppa.squash.uidesign.generated.face_role_lashes
import dev.chieppa.squash.uidesign.generated.face_role_lid
import dev.chieppa.squash.uidesign.generated.face_role_pupil
import dev.chieppa.squash.uidesign.generated.face_role_white
import dev.chieppa.squash.uidesign.generated.face_roundness
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.VectorField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The painted eye's id Add eyes gives. */
private const val PAINTED_EYES_ID = "eyes"

/** Where an eye sits on a head whose template names no eye landmark. */
private const val DEFAULT_U = 0.6f
private const val DEFAULT_V = 0.2f

/** How far Roundness reaches: from four times as wide as tall to half as tall again as wide. */
private val ROUNDNESS_RANGE = 0.25f..1.5f

/** How far a step of the gaze's fields moves it. */
private const val GAZE_STEP = 0.05f

private val SWATCH_GAP = 6.dp

/** Test tags for the painted eye's controls. */
object PaintedEyeTags {
    const val NEW = "face.painted.new"
    const val ROUNDNESS = "face.painted.roundness"
    const val PUPIL = "face.painted.pupil"
    const val MORE = "face.painted.more"
    const val FROM_FRONT = "face.painted.from-front"
    const val HIGHLIGHT = "face.painted.highlight"
    const val GAZE = "face.painted.gaze"
    const val FLAT = "face.painted.flat"

    fun fill(name: String) = "face.painted.fill.$name"

    fun line(name: String) = "face.painted.line.$name"

    fun detail(name: String) = "face.painted.detail.$name"
}

/**
 * The Eyes of a head: its painted eye's Roundness, Pupil and Colors, and under More how much rounder it reads from the
 * front, its highlight, its gaze, whether it lies flat across the face, its lines and its detail (lid mass, lash clumps, iris tones, lid
 * shadow and opening shape). Size, turn and place are the panes' handles. A head with no painted eye offers to add one
 * at its eye landmark.
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
    val look = eye.look
    val drag = rememberCommandDrag(session)
    fun styled(change: (EyeLook) -> EyeLook) = session.send(SetPaintedEye(eye.id, eye.styled(change)))
    DrawerSection {
        ControlSlider(Res.string.face_roundness, eye.roundness, PaintedEyeTags.ROUNDNESS, drag::finish, ROUNDNESS_RANGE) { drag.update(SetEyeRoundness(eye.id, it)) }
        val round = stringResource(Res.string.face_pupil_round)
        val slit = stringResource(Res.string.face_pupil_slit)
        SettingRow(stringResource(Res.string.face_pupil)) {
            Segmented(listOf(false, true), look.slit, { on -> styled { it.copy(slit = on) } }, { if (it) slit else round }, Modifier.weight(1f).testTag(PaintedEyeTags.PUPIL), fill = true)
        }
        SettingRow(stringResource(Res.string.face_colors)) {
            FillSwatch(character, session, Res.string.face_role_iris, "iris", look.iris) { slot -> styled { it.copy(iris = slot) } }
            Spacer(Modifier.width(SWATCH_GAP))
            FillSwatch(character, session, Res.string.face_role_pupil, "pupil", look.pupil) { slot -> styled { it.copy(pupil = slot) } }
            Spacer(Modifier.width(SWATCH_GAP))
            FillSwatch(character, session, Res.string.face_role_white, "white", look.white) { slot -> styled { it.copy(white = slot) } }
        }
    }
    MoreSection(state, session, eye)
}

/** The eye's less used settings, folded away until opened. */
@Composable
private fun MoreSection(state: AppState, session: CharacterSession, eye: PaintedEye) {
    val look = eye.look
    val drag = rememberCommandDrag(session)
    fun styled(change: (EyeLook) -> EyeLook) = session.send(SetPaintedEye(eye.id, eye.styled(change)))
    Box(Modifier.testTag(PaintedEyeTags.MORE)) {
        DrawerSection(stringResource(Res.string.face_more), divider = false, collapsed = !state.face.moreOpen, onCollapsedChange = { state.face.moreOpen = !it }) {
            ControlSlider(Res.string.face_from_front, eye.frontAmount, PaintedEyeTags.FROM_FRONT, drag::finish) { drag.update(SetEyeFromFront(eye.id, it)) }
            ControlSlider(Res.string.face_highlight_size, look.highlightSize, PaintedEyeTags.HIGHLIGHT, drag::finish) { size ->
                drag.update(SetPaintedEye(eye.id, eye.withHighlight(size)))
            }
            val gaze = stringResource(Res.string.face_gaze)
            val axes = listOf(stringResource(Res.string.face_gaze_forward), stringResource(Res.string.face_gaze_up))
            SettingRow(gaze) {
                VectorField(
                    listOf(eye.gazeForward, eye.gazeUp),
                    { axis, value -> drag.update(if (axis == 0) SetEyeGaze(eye.id, value, eye.gazeUp) else SetEyeGaze(eye.id, eye.gazeForward, value)) },
                    gaze,
                    Modifier.testTag(PaintedEyeTags.GAZE),
                    axes = axes,
                    step = GAZE_STEP,
                    range = -MOST_GAZE..MOST_GAZE,
                    onValueChangeFinished = drag::finish,
                )
            }
            LineToggle(Res.string.face_flat, PaintedEyeTags.FLAT, eye.flat) { on -> session.send(SetPaintedEye(eye.id, eye.copy(flat = on))) }
            LineToggle(Res.string.face_role_brow, PaintedEyeTags.line("brow"), look.brow) { on -> styled { it.copy(brow = on) } }
            SettingRow(stringResource(Res.string.face_role_lashes)) {
                Segmented((0..MOST_LASHES).toList(), look.lashes, { count -> styled { it.copy(lashes = count) } }, { it.toString() }, Modifier.weight(1f).testTag(PaintedEyeTags.line("lashes")), fill = true)
            }
            LineToggle(Res.string.face_role_lid, PaintedEyeTags.line("lid"), look.lid) { on -> styled { it.copy(lid = on) } }
            EyeDetailControls(session, eye, drag)
        }
    }
}

/** A fill of the eye as its swatch, which opens the palette, named by its tooltip. */
@Composable
private fun FillSwatch(character: Character, session: CharacterSession, label: StringResource, name: String, slot: SlotName?, onPick: (SlotName) -> Unit) {
    SlotChoice(character.palette, slot, stringResource(label), PaintedEyeTags.fill(name), { PaintedEyeTags.fill("$name.${it.value}") }, onPick, editor = session)
}

/** A line of the eye switched on or off. */
@Composable
private fun LineToggle(label: StringResource, tag: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val title = stringResource(label)
    SettingRow(title) {
        Spacer(Modifier.weight(1f))
        Toggle(on, onChange, title, Modifier.testTag(tag))
    }
}
