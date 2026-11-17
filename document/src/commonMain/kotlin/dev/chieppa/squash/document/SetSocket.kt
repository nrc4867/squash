package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Seats [part] on [socket] in place of the socket it sits on, as one step, keeping everything else of it. Only [part] moves, not the
 * other side of its pair, which names a socket on its own parent. The root sits on no socket and takes none.
 */
@Serializable
@SerialName("setSocket")
data class SetSocket(val part: PartId, val socket: SocketRef) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val was = requireNotNull(instance.socket) { "Part ${part.value} is the root and sits on no socket" }
        val parts = document.character.parts.map { if (it.id == part) it.copy(socket = socket) else it }
        return Applied(document.copy(character = document.character.copy(parts = parts)), SetSocket(part, was))
    }
}
