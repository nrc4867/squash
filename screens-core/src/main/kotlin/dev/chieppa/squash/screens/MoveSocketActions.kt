package dev.chieppa.squash.screens

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.move.CopyKnob
import dev.chieppa.squash.screens.move.SocketLayout
import kotlin.math.roundToInt

/** Where a row placed nearer a part's start than its middle ends, and where one placed nearer its end ends. */
private const val ROW_FAR_END = 0.92f
private const val ROW_NEAR_END = 0.08f
private const val MIDDLE = 0.5f

/**
 * Places a hand-made socket on [parent] at [u] and [v] with the Move tool's outward angle and pair flag, as one undo step, and picks
 * it; with the tool's count above 1, a row of that many from [u] toward the part's further end, at the tool's size and falloff. A
 * paired socket on a part in the middle of the body stores v as the left side reads it, so a press on the right lands there.
 * A press on a digit, [digit] as `DigitPatches` numbers it, places the socket on that digit's patch. See HLD: data-model, Hand-made sockets.
 */
fun AppState.addSocket(parent: PlacedPart, u: Float, v: Float, onRight: Boolean, digit: Int? = null) {
    val opened = session.value ?: return
    val id = parent.id ?: return
    val character = opened.document.value.character
    val part = character.part(id) ?: return
    val paired = move.pair.value && part.pair == null
    val stored = if (paired && onRight && !parent.mirrored) 1f - v else v
    val number = HandSockets.nextId(character.resolvedShape(part).params)
    val count = move.count.value
    val values = move.copyValues.value
    fun knob(k: CopyKnob) = values[k] ?: k.default
    opened.send(
        if (move.layout.value == SocketLayout.SPREAD) {
            HandSockets.addSpread(
                id, number, knob(CopyKnob.FROM_U), knob(CopyKnob.TO_U), knob(CopyKnob.FROM_V), knob(CopyKnob.TO_V), knob(CopyKnob.SPREAD_COUNT).roundToInt(),
                knob(CopyKnob.SEED).roundToInt(), knob(CopyKnob.SPACING), knob(CopyKnob.SIZE_JITTER), knob(CopyKnob.LEAN_JITTER), knob(CopyKnob.TURN_JITTER), move.outward.value, knob(CopyKnob.DROOP),
            )
        } else if (move.layout.value == SocketLayout.PATH) {
            HandSockets.addPath(id, number, u, v, if (u < MIDDLE) ROW_FAR_END else ROW_NEAR_END, v, knob(CopyKnob.PATH_COUNT).roundToInt(), knob(CopyKnob.ROLL), knob(CopyKnob.SAG))
        } else if (count > 1) {
            HandSockets.addRow(id, number, u, if (u < MIDDLE) ROW_FAR_END else ROW_NEAR_END, stored, move.outward.value, paired, count, move.size.value, move.falloff.value, digit)
        } else {
            HandSockets.add(id, number, u, stored, move.outward.value, paired, digit = digit)
        },
    )
    move.picked.value = SocketRef(id, HandSockets.name(number))
}

/** The layout of the picked hand-made socket, or with none picked, of the socket the next press places. */
fun AppState.pickedLayout(): SocketLayout {
    val (params, number) = pickedSocketParams() ?: return move.layout.value
    return CopyKnob.layoutOf(params, number)
}

/** What [knob] shows: the picked socket's value where it shows copies, else the next socket's. */
fun AppState.pickedCopyValue(knob: CopyKnob): Float {
    val picked = pickedSocketParams()
    if (picked != null && CopyKnob.layoutOf(picked.first, picked.second) != SocketLayout.SOCKETS) return picked.first[knob.key(picked.second)] ?: knob.default
    return move.copyValues.value[knob] ?: knob.default
}

/** Sets [knob] on the picked socket where it shows copies, one undo step, or for the next socket placed. */
fun AppState.setCopyValue(knob: CopyKnob, value: Float) {
    val held = knob.held(value)
    val picked = move.picked.value
    val params = pickedSocketParams()
    if (picked != null && params != null && CopyKnob.layoutOf(params.first, params.second) != SocketLayout.SOCKETS) {
        session.value?.send(SetParameter(picked.parent, knob.key(params.second), held))
        return
    }
    move.copyValues.value = move.copyValues.value + (knob to held)
}

/** The picked hand-made socket's part's parameters and its number, or null with none picked. */
private fun AppState.pickedSocketParams(): Pair<Map<String, Float>, Int>? {
    val picked = move.picked.value ?: return null
    val number = HandSockets.idOf(picked.socket) ?: return null
    val character = session.value?.document?.value?.character ?: return null
    val part = character.part(picked.parent) ?: return null
    return character.resolvedShape(part).params to number
}

/** Whether the picked hand-made socket, or any socket of the picked row, holds a part, which keeps it from being deleted. */
fun AppState.pickedSocketHolds(): Boolean {
    val picked = move.picked.value ?: return false
    val number = HandSockets.idOf(picked.socket)
    return session.value?.document?.value?.character?.parts?.any { part ->
        part.socket == picked || part.socket?.let { it.parent == picked.parent && number != null && HandSockets.rowOf(it.socket) == number } == true
    } == true
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

/** Picks the hand-made socket [socket] on [parent], or the whole row a socket of a row belongs to, or drops the pick for any other socket. */
fun AppState.pickSocket(parent: PartId, socket: String) {
    move.picked.value = (HandSockets.idOf(socket) ?: HandSockets.rowOf(socket))?.let { SocketRef(parent, HandSockets.name(it)) }
}

/** Targets the picked socket in Library while it is empty, which T does with a socket picked. */
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
