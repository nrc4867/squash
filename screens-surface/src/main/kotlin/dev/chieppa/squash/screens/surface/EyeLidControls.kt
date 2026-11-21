package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.EyeLidMark
import dev.chieppa.squash.document.EyeLids
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.LidPose
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetEyeLids
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.styled
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_lid_above
import dev.chieppa.squash.uidesign.generated.face_lid_blink
import dev.chieppa.squash.uidesign.generated.face_lid_closed_at
import dev.chieppa.squash.uidesign.generated.face_lid_closed_bend
import dev.chieppa.squash.uidesign.generated.face_lid_closed_tilt
import dev.chieppa.squash.uidesign.generated.face_lid_colors
import dev.chieppa.squash.uidesign.generated.face_lid_fade
import dev.chieppa.squash.uidesign.generated.face_lid_fade_to
import dev.chieppa.squash.uidesign.generated.face_lid_lower_rise
import dev.chieppa.squash.uidesign.generated.face_lid_skin
import dev.chieppa.squash.uidesign.generated.face_lid_slot
import dev.chieppa.squash.uidesign.generated.face_lid_upper_close
import dev.chieppa.squash.uidesign.generated.face_lid_wing
import dev.chieppa.squash.uidesign.generated.face_lids
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Toggle
import dev.chieppa.stretch.ui.ToggleButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How far the closed line may sit, in the opening's heights from its middle. */
private val CLOSED_AT_RANGE = -2f..1f

/** How far the closed line's middle may bend, in heights. */
private val CLOSED_BEND_RANGE = -1f..1f

/** How far the closed line may turn, in degrees. */
private val CLOSED_TILT_RANGE = -45f..45f

/** How far the lid's colour may fade and reach above the open lid, in heights. */
private val LID_PAINT_RANGE = 0f..3f

/** The liner wing a lid takes when Wing is switched on: from the back corner, out and up past it. */
private val WING = EyeLidMark(along = -1f, above = 0f, out = 0.45f, up = 0.3f, width = 0.1f, bend = 0.4f)

private val SWATCH_GAP = 6.dp

/** Test tags for the painted eye's lid controls. */
object EyeLidTags {
    const val SECTION = "face.painted.lids"
    const val BLINK = "face.painted.lids.blink"
    const val WING = "face.painted.lids.wing"

    fun slider(name: String) = "face.painted.lids.$name"

    fun fill(name: String) = "face.painted.lids.fill.$name"
}

/** One look slider of the lids: its label, its test tag's name, its range, how it reads the lids and how it changes them. */
private class LidSlider(
    val label: StringResource,
    val name: String,
    val range: ClosedFloatingPointRange<Float>,
    val read: (EyeLids) -> Float,
    val write: (EyeLids, Float) -> EyeLids,
)

private val CLOSED_SLIDERS = listOf(
    LidSlider(Res.string.face_lid_closed_at, "closed-at", CLOSED_AT_RANGE, { it.closedAt }) { l, v -> l.copy(closedAt = v) },
    LidSlider(Res.string.face_lid_closed_bend, "closed-bend", CLOSED_BEND_RANGE, { it.closedBend }) { l, v -> l.copy(closedBend = v) },
    LidSlider(Res.string.face_lid_closed_tilt, "closed-tilt", CLOSED_TILT_RANGE, { it.closedTilt }) { l, v -> l.copy(closedTilt = v) },
)

private val PAINT_SLIDERS = listOf(
    LidSlider(Res.string.face_lid_fade, "fade", LID_PAINT_RANGE, { it.fade }) { l, v -> l.copy(fade = v) },
    LidSlider(Res.string.face_lid_above, "above", LID_PAINT_RANGE, { it.above }) { l, v -> l.copy(above = v) },
)

/**
 * The painted eye's Lids, folded away until opened: where its lids stand, a Blink button that shows every eye shut in the panes while it
 * is on, which is no edit and is let go when the section leaves, where they meet when closed, and the upper lid's colour, fade and wing.
 * Each slider's drag is one step of undo and a repaint of the eye.
 */
@Composable
internal fun EyeLidSection(state: AppState, session: CharacterSession, character: Character, eye: PaintedEye) {
    val drag = rememberCommandDrag(session)
    val lids = eye.look.lids
    fun styled(change: (EyeLids) -> EyeLids) = session.send(SetPaintedEye(eye.id, eye.styled { it.copy(lids = change(it.lids)) }))
    val preview = session.sessionSurface.lidPreview
    val shown by preview.collectAsState()
    DisposableEffect(preview) { onDispose { preview.value = null } }
    Box(Modifier.testTag(EyeLidTags.SECTION)) {
        DrawerSection(stringResource(Res.string.face_lids), divider = false, collapsed = !state.face.lidsOpen, onCollapsedChange = { state.face.lidsOpen = !it }) {
            ControlSlider(Res.string.face_lid_upper_close, eye.upperClose, EyeLidTags.slider("upper-close"), drag::finish) { drag.update(SetEyeLids(eye.id, it, eye.lowerRise)) }
            ControlSlider(Res.string.face_lid_lower_rise, eye.lowerRise, EyeLidTags.slider("lower-rise"), drag::finish) { drag.update(SetEyeLids(eye.id, eye.upperClose, it)) }
            ToggleButton(
                stringResource(Res.string.face_lid_blink),
                shown == LidPose.BLINK,
                { on -> preview.value = if (on) LidPose.BLINK else null },
                Modifier.fillMaxWidth().testTag(EyeLidTags.BLINK),
            )
            LidSliders(CLOSED_SLIDERS, eye, drag)
            SettingRow(stringResource(Res.string.face_lid_colors)) {
                LidSwatch(character, session, Res.string.face_lid_slot, "slot", lids.slot) { slot -> styled { it.copy(slot = slot) } }
                Spacer(Modifier.width(SWATCH_GAP))
                LidSwatch(character, session, Res.string.face_lid_fade_to, "fade-to", lids.fadeTo) { slot -> styled { it.copy(fadeTo = slot) } }
            }
            LidSliders(PAINT_SLIDERS, eye, drag)
            val wing = stringResource(Res.string.face_lid_wing)
            SettingRow(wing) {
                Spacer(Modifier.weight(1f))
                Toggle(lids.marks.isNotEmpty(), { on -> styled { it.copy(marks = if (on) listOf(WING) else emptyList()) } }, wing, Modifier.testTag(EyeLidTags.WING))
            }
        }
    }
}

/** Each of [sliders] over [eye]'s lids, its drags sent through [drag], each change made to its look and its front look alike. */
@Composable
private fun LidSliders(sliders: List<LidSlider>, eye: PaintedEye, drag: CommandDrag) = sliders.forEach { slider ->
    ControlSlider(slider.label, slider.read(eye.look.lids), EyeLidTags.slider(slider.name), drag::finish, slider.range) { value ->
        drag.update(SetPaintedEye(eye.id, eye.styled { look: EyeLook -> look.copy(lids = slider.write(look.lids, value)) }))
    }
}

/** A colour of the lid as its swatch, which opens the palette with Skin to clear it. */
@Composable
private fun LidSwatch(character: Character, session: CharacterSession, label: StringResource, name: String, slot: SlotName?, onPick: (SlotName?) -> Unit) {
    SlotChoice(
        character.palette,
        slot,
        stringResource(label),
        EyeLidTags.fill(name),
        { EyeLidTags.fill("$name.${it.value}") },
        onPick,
        editor = session,
        footer = { close ->
            Button(stringResource(Res.string.face_lid_skin), {
                onPick(null)
                close()
            }, Modifier.fillMaxWidth().testTag(EyeLidTags.fill("$name.skin")), kind = ButtonKind.Quiet)
        },
    )
}
