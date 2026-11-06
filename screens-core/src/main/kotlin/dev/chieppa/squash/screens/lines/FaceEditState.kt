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

/**
 * What the face tools remember: the [group] picked in the Marks drawer and the [role] Draw mark and Edit curves work in, the angle version [version] they write, the one the view
 * shows if none, and the test gaze: whether it is [gazeOn] and its [gazeTarget] in body space, which is not part of the character.
 */
@Stable
class FaceEditState {
    var group by mutableStateOf<PickedGroup?>(null)
    var role by mutableStateOf<MarkRole?>(null)
    var version by mutableStateOf<CameraBin?>(null)
    var gazeOn by mutableStateOf(false)
    var gazeTarget by mutableStateOf<Vec3?>(null)

    fun clear() {
        group = null
        role = null
        version = null
        gazeOn = false
        gazeTarget = null
    }
}
