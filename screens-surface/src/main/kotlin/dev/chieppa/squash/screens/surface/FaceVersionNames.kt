package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_version_front
import dev.chieppa.squash.uidesign.generated.face_version_side
import dev.chieppa.squash.uidesign.generated.face_version_three_quarter
import dev.chieppa.squash.uidesign.generated.face_writes
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The test tag of the chip naming the version a face stroke writes. */
const val FACE_WRITES_TAG = "face.writes"

/** The name of a head's angle version [bin]. */
fun versionName(bin: CameraBin): StringResource = when (bin) {
    FACE_FRONT -> Res.string.face_version_front
    FACE_THREE_QUARTER -> Res.string.face_version_three_quarter
    else -> Res.string.face_version_side
}

/** The chip naming [bin] as the version a face stroke writes. */
@Composable
fun FaceVersionChip(bin: CameraBin) {
    Chip(stringResource(Res.string.face_writes, stringResource(versionName(bin))), Modifier.testTag(FACE_WRITES_TAG), kind = ChipKind.On)
}
