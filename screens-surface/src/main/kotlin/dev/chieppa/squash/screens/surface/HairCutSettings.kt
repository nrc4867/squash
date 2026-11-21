package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockTrim
import dev.chieppa.squash.document.MOST_LOCK_COIL
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_hair_coil
import dev.chieppa.squash.uidesign.generated.face_hair_lie
import dev.chieppa.squash.uidesign.generated.face_hair_stand
import dev.chieppa.squash.uidesign.generated.face_hair_trim
import dev.chieppa.squash.uidesign.generated.face_hair_trim_level
import dev.chieppa.squash.uidesign.generated.face_hair_trim_tilt
import dev.chieppa.stretch.ui.Checkbox
import org.jetbrains.compose.resources.stringResource

/** The lowest and highest a trim's slider reaches, a share of the head's reach above the skull's middle. */
private const val LOWEST_TRIM = -3f
private const val HIGHEST_TRIM = 2f

/** The most a trim's slider tilts either way, in degrees. */
private const val MOST_TRIM_TILT = 60f

/** Test tags for a group's cut. */
object HairCutTags {
    const val TRIM = "face.hair.trim"
}

/**
 * The picked group's cut: how much of each lock lies along the head, Trim with its height and tilt where it is on, and on a tied group
 * how far its locks coil into a bun; each switch and each drag one undo step.
 */
@Composable
internal fun GroupCutSettings(session: CharacterSession, group: LockGroup) {
    val id = group.id
    GroupSlider(session, id, "lie", stringResource(Res.string.face_hair_lie), group.lie, 0.05f..0.95f) { g, v -> g.copy(lie = v) }
    GroupSlider(session, id, "stand", stringResource(Res.string.face_hair_stand), group.stand, 0f..1f) { g, v -> g.copy(stand = v) }
    Checkbox(group.trim != null, { on -> session.send(SetLockGroup(id, group.copy(trim = if (on) LockTrim() else null))) }, stringResource(Res.string.face_hair_trim), Modifier.testTag(HairCutTags.TRIM))
    group.trim?.let { trim ->
        GroupSlider(session, id, "trim.level", stringResource(Res.string.face_hair_trim_level), trim.level, LOWEST_TRIM..HIGHEST_TRIM) { g, v -> g.copy(trim = (g.trim ?: trim).copy(level = v)) }
        GroupSlider(session, id, "trim.tilt", stringResource(Res.string.face_hair_trim_tilt), trim.tilt, -MOST_TRIM_TILT..MOST_TRIM_TILT) { g, v -> g.copy(trim = (g.trim ?: trim).copy(tilt = v)) }
    }
    if (group.tie > 0f) {
        GroupSlider(session, id, "coil", stringResource(Res.string.face_hair_coil), group.coil, 0f..MOST_LOCK_COIL) { g, v -> g.copy(coil = v) }
    }
}
