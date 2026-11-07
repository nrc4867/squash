package dev.chieppa.squash.screens

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument

/**
 * Places a hand-made socket on [parent] at [u] and [v] with the Move tool's outward angle and pair flag, as one undo step, and picks
 * it. A paired socket on a part in the middle of the body stores v as the left side reads it, so a press on the right lands there.
 * See HLD: data-model, Hand-made sockets.
 */
fun AppState.addSocket(parent: PlacedPart, u: Float, v: Float, onRight: Boolean) {
    val opened = session.value ?: return
    val id = parent.id ?: return
    val character = opened.document.value.character
    val part = character.part(id) ?: return
    val paired = move.pair.value && part.pair == null
    val stored = if (paired && onRight && !parent.mirrored) 1f - v else v
    val number = HandSockets.nextId(character.resolvedShape(part).params)
    opened.send(HandSockets.add(id, number, u, stored, move.outward.value, paired))
    move.picked.value = SocketRef(id, HandSockets.name(number))
}

/** Whether the picked hand-made socket holds a part, which keeps it from being deleted. */
fun AppState.pickedSocketHolds(): Boolean {
    val picked = move.picked.value ?: return false
    return session.value?.document?.value?.character?.parts?.any { it.socket == picked } == true
}

/** Clears the picked hand-made socket from its part, one undo step, unless it holds a part. */
fun AppState.deletePickedSocket() {
    val picked = move.picked.value ?: return
    val number = HandSockets.idOf(picked.socket) ?: return
    if (pickedSocketHolds()) return
    session.value?.send(HandSockets.delete(picked.parent, number))
    move.picked.value = null
}

/** The outward angle the Outward angle field shows: the picked socket's, or the next one's. */
fun AppState.pickedOutward(): Float {
    val picked = move.picked.value ?: return move.outward.value
    val number = HandSockets.idOf(picked.socket) ?: return move.outward.value
    val character = session.value?.document?.value?.character ?: return move.outward.value
    val part = character.part(picked.parent) ?: return move.outward.value
    return character.resolvedShape(part).params[HandSockets.outward(number)] ?: 0f
}

/** Sets the picked socket's outward angle, one undo step, or the next socket's with none picked. */
fun AppState.setOutward(degrees: Float) {
    val value = degrees.coerceIn(-HandSockets.MAX_OUTWARD, HandSockets.MAX_OUTWARD)
    val picked = move.picked.value
    val number = picked?.let { HandSockets.idOf(it.socket) }
    if (picked == null || number == null) {
        move.outward.value = value
        return
    }
    session.value?.send(SetParameter(picked.parent, HandSockets.outward(number), value))
}

/** Picks the hand-made socket [socket] on [parent], or drops the pick for any other socket. */
fun AppState.pickSocket(parent: PartId, socket: String) {
    move.picked.value = SocketRef(parent, socket).takeIf { HandSockets.idOf(socket) != null }
}

/** Opens Add part's popover on the picked socket while it is empty, which T does with a socket picked. */
fun AppState.offerPickedSocket() {
    val picked = move.picked.value ?: return
    openSocketsNow().firstOrNull { it.ref == picked }?.let(::offerSocket)
}

/** Whether [document] still holds the hand-made socket [socket], which undo can take away. */
fun hasHandSocket(document: SquashDocument, socket: SocketRef): Boolean {
    val number = HandSockets.idOf(socket.socket) ?: return false
    val part = document.character.part(socket.parent) ?: return false
    return number in HandSockets.ids(document.character.resolvedShape(part).params)
}
