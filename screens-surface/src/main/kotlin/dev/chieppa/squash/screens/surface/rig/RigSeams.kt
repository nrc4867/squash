package dev.chieppa.squash.screens.surface.rig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.seamStrokes
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.rig_paired
import dev.chieppa.squash.uidesign.generated.rig_reset
import dev.chieppa.squash.uidesign.generated.rig_reset_hint
import dev.chieppa.squash.uidesign.generated.rig_seams
import dev.chieppa.squash.uidesign.generated.rig_stroke
import dev.chieppa.squash.uidesign.generated.rig_strokes
import dev.chieppa.squash.uidesign.generated.rig_tidy
import dev.chieppa.squash.uidesign.generated.rig_tidy_hint
import dev.chieppa.squash.uidesign.generated.rig_to_twin
import dev.chieppa.squash.uidesign.generated.rig_to_twin_hint
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val LINK = 14.dp
private val BUTTON_GAP = 6.dp

/** Test tags for the Seams section. */
object RigSeamsTags {
    const val TIDY = "rig.seams.tidy"
    const val TO_TWIN = "rig.seams.toTwin"
    const val RESET = "rig.seams.reset"

    fun seam(child: PartId) = "rig.seams.${child.value}"
    fun strokes(child: PartId) = "rig.seams.strokes.${child.value}"
    fun paired(child: PartId) = "rig.seams.paired.${child.value}"
}

/**
 * The Seams section: a row for every seam, named by its two parts, with its stroke count once it holds any and a link where it
 * is one of a pair; a row picks its seam for Tidy, To twin and Reset, which act on it as one undo step each.
 */
@Composable
fun SeamsSection(state: AppState, session: CharacterSession, rig: Rig, names: RigNames) {
    val document by session.document.collectAsState()
    val character = document.character
    val current = currentSeam(rig, state.rig.seam, state.rig.picked)
    DrawerSection(stringResource(Res.string.rig_seams)) {
        for (seam in rig.seams) {
            val strokes = character.seamStrokes(seam.child).size
            ListRow(current == seam.child, { state.rig.seam = seam.child }, Modifier.testTag(RigSeamsTags.seam(seam.child))) {
                RowText(names.seam(seam.parent, seam.child), Modifier.weight(1f))
                if (strokes > 0) {
                    Chip(if (strokes == 1) stringResource(Res.string.rig_stroke) else stringResource(Res.string.rig_strokes, strokes), Modifier.testTag(RigSeamsTags.strokes(seam.child)))
                }
                if (character.part(seam.child)?.pair != null) {
                    Icon(SquashIcons.SeamPaired, Stretch.colors.inkMuted, Modifier.testTag(RigSeamsTags.paired(seam.child)), size = LINK, contentDescription = stringResource(Res.string.rig_paired))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            SeamButton(Res.string.rig_tidy, Res.string.rig_tidy_hint, RigSeamsTags.TIDY) {
                current?.let { tidyCommand(rig, session.document.value.character, it, state.thisSide) }?.let(session::send)
            }
            SeamButton(Res.string.rig_to_twin, Res.string.rig_to_twin_hint, RigSeamsTags.TO_TWIN) {
                current?.let { toTwinCommand(session.document.value.character, it) }?.let(session::send)
            }
            SeamButton(Res.string.rig_reset, Res.string.rig_reset_hint, RigSeamsTags.RESET) {
                current?.let { resetCommand(session.document.value.character, it, state.thisSide) }?.let(session::send)
            }
        }
    }
}

@Composable
private fun SeamButton(text: org.jetbrains.compose.resources.StringResource, hint: org.jetbrains.compose.resources.StringResource, tag: String, onClick: () -> Unit) {
    Tooltip(stringResource(hint)) {
        Button(stringResource(text), onClick, Modifier.testTag(tag), kind = ButtonKind.Quiet)
    }
}
