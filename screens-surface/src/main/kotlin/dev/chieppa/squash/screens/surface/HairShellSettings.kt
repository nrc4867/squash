package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.Hairline
import dev.chieppa.squash.document.HIGHEST_HAIRLINE
import dev.chieppa.squash.document.LOWEST_HAIRLINE
import dev.chieppa.squash.document.MOST_SHELL_SOFT
import dev.chieppa.squash.document.MOST_SHELL_STEPS
import dev.chieppa.squash.document.MOST_SHELL_THICKNESS
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetHairShell
import dev.chieppa.squash.document.ShellFade
import dev.chieppa.squash.document.hairShellOn
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_hair_shell
import dev.chieppa.squash.uidesign.generated.face_hair_shell_add
import dev.chieppa.squash.uidesign.generated.face_hair_shell_ears
import dev.chieppa.squash.uidesign.generated.face_hair_shell_fade_high
import dev.chieppa.squash.uidesign.generated.face_hair_shell_fade_low
import dev.chieppa.squash.uidesign.generated.face_hair_shell_fade_mid
import dev.chieppa.squash.uidesign.generated.face_hair_shell_fade_none
import dev.chieppa.squash.uidesign.generated.face_hair_shell_fade_to
import dev.chieppa.squash.uidesign.generated.face_hair_shell_front
import dev.chieppa.squash.uidesign.generated.face_hair_shell_nape
import dev.chieppa.squash.uidesign.generated.face_hair_shell_remove
import dev.chieppa.squash.uidesign.generated.face_hair_shell_shade
import dev.chieppa.squash.uidesign.generated.face_hair_shell_sideburns
import dev.chieppa.squash.uidesign.generated.face_hair_shell_soft
import dev.chieppa.squash.uidesign.generated.face_hair_shell_steps
import dev.chieppa.squash.uidesign.generated.face_hair_shell_temples
import dev.chieppa.squash.uidesign.generated.face_hair_shell_thickness
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.Radio
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val FADE_NAMES: Map<ShellFade, StringResource> = mapOf(
    ShellFade.NONE to Res.string.face_hair_shell_fade_none,
    ShellFade.LOW to Res.string.face_hair_shell_fade_low,
    ShellFade.MID to Res.string.face_hair_shell_fade_mid,
    ShellFade.HIGH to Res.string.face_hair_shell_fade_high,
)

/** Test tags for the Hair section's Shell. */
object HairShellTags {
    const val ADD = "face.hair.shell.add"
    const val REMOVE = "face.hair.shell.remove"

    fun fade(fade: ShellFade) = "face.hair.shell.fade.${fade.name.lowercase()}"
    fun setting(name: String) = "face.hair.shell.$name"
}

/**
 * The Hair section's Shell, the hair cut to the skin over [head]: Add shell where it has none; else its fade, how much tone the fade
 * leaves, its thickness, stubble tone, steps and, fading smoothly, softness, its hairline's front, temples, sideburns, ears and nape, and Remove shell; each choice
 * and each drag one undo step.
 */
@Composable
internal fun ShellSettings(session: CharacterSession, head: PartId) {
    val document by session.document.collectAsState()
    val shell = document.character.hairShellOn(head)
    Divider(label = stringResource(Res.string.face_hair_shell))
    if (shell == null) {
        Button(stringResource(Res.string.face_hair_shell_add), { session.send(SetHairShell(head, HairShell(head))) }, Modifier.testTag(HairShellTags.ADD), kind = ButtonKind.Quiet)
        return
    }
    for (fade in ShellFade.entries) {
        Radio(shell.fade == fade, { session.send(SetHairShell(head, shell.copy(fade = fade))) }, stringResource(FADE_NAMES.getValue(fade)), Modifier.testTag(HairShellTags.fade(fade)))
    }
    if (shell.fade != ShellFade.NONE) {
        ShellSlider(session, head, "fadeTo", stringResource(Res.string.face_hair_shell_fade_to), shell.fadeTo, 0f..1f) { s, v -> s.copy(fadeTo = v) }
    }
    ShellSlider(session, head, "thickness", stringResource(Res.string.face_hair_shell_thickness), shell.thickness, 0f..MOST_SHELL_THICKNESS) { s, v -> s.copy(thickness = v) }
    ShellSlider(session, head, "shade", stringResource(Res.string.face_hair_shell_shade), shell.shade, 0f..1f) { s, v -> s.copy(shade = v) }
    ShellSlider(session, head, "steps", stringResource(Res.string.face_hair_shell_steps), shell.steps.toFloat(), 0f..MOST_SHELL_STEPS.toFloat()) { s, v -> s.copy(steps = stepsOf(v)) }
    if (shell.steps == 0) {
        ShellSlider(session, head, "soft", stringResource(Res.string.face_hair_shell_soft), shell.soft, 0f..MOST_SHELL_SOFT) { s, v -> s.copy(soft = v) }
    }
    HairlineSlider(session, head, "front", stringResource(Res.string.face_hair_shell_front), shell.hairline.front) { h, v -> h.copy(front = v) }
    HairlineSlider(session, head, "temples", stringResource(Res.string.face_hair_shell_temples), shell.hairline.temples) { h, v -> h.copy(temples = v) }
    HairlineSlider(session, head, "sideburns", stringResource(Res.string.face_hair_shell_sideburns), shell.hairline.sideburns) { h, v -> h.copy(sideburns = v) }
    HairlineSlider(session, head, "ears", stringResource(Res.string.face_hair_shell_ears), shell.hairline.ears) { h, v -> h.copy(ears = v) }
    HairlineSlider(session, head, "nape", stringResource(Res.string.face_hair_shell_nape), shell.hairline.nape) { h, v -> h.copy(nape = v) }
    Button(stringResource(Res.string.face_hair_shell_remove), { session.send(SetHairShell(head, null)) }, Modifier.testTag(HairShellTags.REMOVE), kind = ButtonKind.Quiet)
}

/** One of the shell's numbers on a slider; one drag is one undo step, the values it passes through previews of a single [SetHairShell]. */
@Composable
private fun ShellSlider(
    session: CharacterSession,
    head: PartId,
    name: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    change: (HairShell, Float) -> HairShell,
) = HairSlider(session, HairShellTags.setting(name), label, value, range) { document, next ->
    document.character.hairShellOn(head)?.let { SetHairShell(head, change(it, next)) }
}

/** A steps slider's value as a shell's steps: whole, 0 for smooth, and never a single band. */
private fun stepsOf(value: Float): Int = value.roundToInt().let { if (it == 1) 2 else it }

/** One of the hairline's heights on a slider, in degrees up the head. */
@Composable
private fun HairlineSlider(session: CharacterSession, head: PartId, name: String, label: String, value: Float, change: (Hairline, Float) -> Hairline) =
    ShellSlider(session, head, "hairline.$name", label, value, LOWEST_HAIRLINE..HIGHEST_HAIRLINE) { s, v -> s.copy(hairline = change(s.hairline, v)) }
