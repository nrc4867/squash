package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.hair.fit.fitResiduals
import dev.chieppa.squash.character.hair.fit.hairFit
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetHairOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_hair_outline
import dev.chieppa.squash.uidesign.generated.face_hair_outline_fit
import dev.chieppa.squash.uidesign.generated.face_hair_outline_off
import dev.chieppa.squash.uidesign.generated.face_hair_outline_off_value
import dev.chieppa.squash.uidesign.generated.face_hair_outline_refit
import dev.chieppa.squash.uidesign.generated.face_hair_outline_remove
import dev.chieppa.squash.uidesign.generated.face_hair_outline_show
import dev.chieppa.squash.uidesign.generated.face_hair_outline_trace
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Checkbox
import dev.chieppa.stretch.ui.Divider
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

private val OFF_READOUT_WIDTH = 120.dp

/** A length in the head's drawn units, to three places. */
private fun headUnits(value: Float): String = "%.3f".format(Locale.ROOT, value)

/** Test tags for the Hair section's Outline. */
object HairOutlineTags {
    const val TRACE = "face.hair.outline.trace"
    const val SHOW = "face.hair.outline.show"
    const val FIT = "face.hair.outline.fit"
    const val REFIT = "face.hair.outline.refit"
    const val REMOVE = "face.hair.outline.remove"
    const val OFF = "face.hair.outline.off"
}

/**
 * The Hair section's Outline, the hair outline of [head]: Trace from reference, which outlines the hair in the front view's reference
 * image; Show outline over the panes; Fit to outline, the outline's fit switch; Refit, which fits the locks and the inner mass to it; how
 * far the front outline lies from the hair's silhouette, mean and worst in the head's drawn units; and Remove outline. Each change one undo
 * step.
 */
@Composable
internal fun OutlineSettings(state: AppState, session: CharacterSession, head: PartId) {
    val document by session.document.collectAsState()
    val body by session.bodyAssembly.output.collectAsState()
    val built by session.assembly.output.collectAsState()
    val character = document.character
    val outline = character.hairOutlineOn(head)
    val traceable = character.traces[View.FRONT]?.shown == true && body != null
    Divider(label = stringResource(Res.string.face_hair_outline))
    Button(
        stringResource(Res.string.face_hair_outline_trace),
        {
            val assembled = body ?: return@Button
            tracedHairOutline(session.document.value, state.images, assembled, head)?.let { session.send(SetHairOutline(head, it)) }
        },
        Modifier.testTag(HairOutlineTags.TRACE),
        kind = ButtonKind.Quiet,
        enabled = traceable,
    )
    if (outline == null) return
    Checkbox(state.face.hairOutline, { state.face.hairOutline = it }, stringResource(Res.string.face_hair_outline_show), Modifier.testTag(HairOutlineTags.SHOW))
    Checkbox(outline.fit, { session.send(SetHairOutline(head, outline.copy(fit = it))) }, stringResource(Res.string.face_hair_outline_fit), Modifier.testTag(HairOutlineTags.FIT))
    Button(
        stringResource(Res.string.face_hair_outline_refit),
        {
            val assembled = body ?: return@Button
            hairFit(session.document.value.character, assembled, head)?.command?.let(session::send)
        },
        Modifier.testTag(HairOutlineTags.REFIT),
        kind = ButtonKind.Quiet,
        enabled = outline.fit && body != null,
    )
    val off = remember(built, outline) { built?.let { assembled -> fitResiduals(character, assembled, head).firstOrNull { it.view == OutlineView.FRONT } } }
    if (off != null) {
        SettingRow(stringResource(Res.string.face_hair_outline_off)) {
            BasicText(
                stringResource(Res.string.face_hair_outline_off_value, headUnits(off.mean), headUnits(off.worst)),
                Modifier.width(OFF_READOUT_WIDTH).testTag(HairOutlineTags.OFF),
                style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
                maxLines = 1,
            )
        }
    }
    Button(
        stringResource(Res.string.face_hair_outline_remove),
        {
            session.send(SetHairOutline(head, null))
            state.face.hairOutline = false
        },
        Modifier.testTag(HairOutlineTags.REMOVE),
        kind = ButtonKind.Quiet,
    )
}
