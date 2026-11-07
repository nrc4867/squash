package dev.chieppa.squash.screens.move

import dev.chieppa.squash.document.SocketRef
import kotlinx.coroutines.flow.MutableStateFlow

/** What a press of the Move tool does: drag sockets and joints, or place a socket on the surface. */
enum class MoveMode { PLACE_PART, ADD_SOCKET }

/**
 * What the Move tool remembers: its [mode], the [outward] angle and [pair] flag the next socket placed takes, and the hand-made socket
 * [picked], which Delete socket and the Outward angle field act on. Never saved or undone.
 */
class MoveEditState {
    val mode = MutableStateFlow(MoveMode.PLACE_PART)
    val outward = MutableStateFlow(0f)
    val pair = MutableStateFlow(true)
    val picked = MutableStateFlow<SocketRef?>(null)

    fun clear() {
        mode.value = MoveMode.PLACE_PART
        outward.value = 0f
        pair.value = true
        picked.value = null
    }
}
