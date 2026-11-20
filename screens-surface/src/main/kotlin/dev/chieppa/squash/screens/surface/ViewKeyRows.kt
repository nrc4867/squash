package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.viewkeys.FaceFollow
import dev.chieppa.squash.document.FaceStyle
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetViewKeys
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.face_follow
import dev.chieppa.squash.uidesign.generated.face_view_fixed
import dev.chieppa.squash.uidesign.generated.face_view_keys
import dev.chieppa.squash.uidesign.generated.face_view_turning
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** Test tags for the head's view keys in the Face inspector. */
object ViewKeyTags {
    const val STYLE = "face.view-style"
    const val FOLLOW = "face.follow"
}

/**
 * How the head [holder]'s face meets a turning camera: Turning face or Fixed face, and how far the face follows the camera, from 0 to
 * 100 %, each showing the head template's default until set; one undo step a drag.
 */
@Composable
internal fun ViewKeyRows(session: CharacterSession, holder: PartId) {
    val document by session.document.collectAsState()
    val character = document.character
    val keys = character.viewKeys
    val template = character.part(holder)?.template?.id ?: return
    val follow = FaceFollow.of(keys, template)
    val gesture = remember(session) { SliderGesture(session) }
    val turning = stringResource(Res.string.face_view_turning)
    val fixed = stringResource(Res.string.face_view_fixed)
    val label = stringResource(Res.string.face_follow)
    DrawerSection(stringResource(Res.string.face_view_keys)) {
        Segmented(
            FaceStyle.entries, follow.style, { session.send(SetViewKeys(keys.copy(face = it, faceFollow = null, keepFace = null))) },
            { if (it == FaceStyle.TURNING) turning else fixed }, Modifier.testTag(ViewKeyTags.STYLE), fill = true,
        )
        SettingRow(label) {
            Slider(follow.follow, { gesture.change(SetViewKeys(keys.copy(faceFollow = it.coerceIn(0f, 1f)))) }, label, Modifier.weight(1f).testTag(ViewKeyTags.FOLLOW), 0f..1f, gesture::done)
            Readout(stringResource(Res.string.edit_percent, (follow.follow * PERCENT).roundToInt()))
        }
    }
}
