package dev.chieppa.squash.screens.lines

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3

/** A mark group by the part that holds it. */
data class PickedGroup(val part: PartId, val id: MarkGroupId)

/** What the Face stage works on: the eyes or the mouth. */
enum class FaceFeature { EYES, MOUTH }

/**
 * What the face tools remember, and act on only while Face is on screen: the [feature] the inspector shows, the [group] picked and the [role] Draw mark
 * and Edit curves work in, whether an eye's work writes [bothEyes], the angle [version] they write, the one the view shows if none, and whether it is
 * [locked], so the view shows it from any camera too; and the test gaze's [gazeTarget] in body space, which is not part of the character.
 */
@Stable
class FaceEditState {
    var feature by mutableStateOf(FaceFeature.EYES)
    var group by mutableStateOf<PickedGroup?>(null)
    var role by mutableStateOf<MarkRole?>(null)
    var bothEyes by mutableStateOf(true)
    var version by mutableStateOf<CameraBin?>(null)
    var locked by mutableStateOf(false)
    var gazeTarget by mutableStateOf<Vec3?>(null)

    /** The version the view shows from any camera, when one is locked. */
    val lockedVersion: CameraBin? get() = version.takeIf { locked }

    fun clear() {
        feature = FaceFeature.EYES
        group = null
        role = null
        bothEyes = true
        version = null
        locked = false
        gazeTarget = null
    }
}
