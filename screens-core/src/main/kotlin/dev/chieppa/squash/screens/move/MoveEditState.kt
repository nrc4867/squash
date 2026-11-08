package dev.chieppa.squash.screens.move

import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SocketRef
import kotlinx.coroutines.flow.MutableStateFlow

/** What a press of the Move tool does: drag sockets and joints, or place a socket, a joint or a landmark on the surface. */
enum class MoveMode { PLACE_PART, ADD_SOCKET, ADD_JOINT, ADD_LANDMARK }

/** Joint [joint] of part [part]. */
data class JointRef(val part: PartId, val joint: String)

/**
 * What the Move tool remembers: its [mode], the [outward] angle and [pair] flag the next socket placed takes, the [count] of sockets
 * it places in a row with the [size] a part on the row's first starts at and its [falloff] to the last, the hand-made socket
 * [picked], which Delete socket and the Outward angle field act on, the [hinge], [limits] and [stretch] the next joint takes, and the
 * joint [pickedJoint], which Delete joint and those fields act on. Never saved or undone.
 */
class MoveEditState {
    val mode = MutableStateFlow(MoveMode.PLACE_PART)
    val outward = MutableStateFlow(0f)
    val pair = MutableStateFlow(true)
    val count = MutableStateFlow(1)
    val size = MutableStateFlow(1f)
    val falloff = MutableStateFlow(DEFAULT_FALLOFF)
    val picked = MutableStateFlow<SocketRef?>(null)
    val hinge = MutableStateFlow(Hinge.X)
    val limits = MutableStateFlow(HandJoints.DEFAULT_LIMITS)
    val stretch = MutableStateFlow(false)
    val pickedJoint = MutableStateFlow<JointRef?>(null)

    companion object {
        /** The share of the first's size a part on a new row's last socket starts at. */
        const val DEFAULT_FALLOFF = 0.5f
    }

    fun clear() {
        mode.value = MoveMode.PLACE_PART
        outward.value = 0f
        pair.value = true
        count.value = 1
        size.value = 1f
        falloff.value = DEFAULT_FALLOFF
        picked.value = null
        hinge.value = Hinge.X
        limits.value = HandJoints.DEFAULT_LIMITS
        stretch.value = false
        pickedJoint.value = null
    }
}
