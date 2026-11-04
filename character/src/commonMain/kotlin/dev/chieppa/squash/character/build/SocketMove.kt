package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.Side

/**
 * The command that puts socket [socket] of [parent] at [u] and [v] on that part's surface, as seen by a part on [side]. A
 * paired parent holds it as a parameter like any other, on the pair or with [thisSide] on that side. A single parent has one
 * socket for both of its children, so [thisSide] writes that side's own place, and a move of both clears the sides' own.
 * The stored v reads as the left side's, since placing reflects it for a right side on an unmirrored parent.
 */
fun moveSocket(character: Character, parent: PartId, socket: String, side: Side?, u: Float, v: Float, parentMirrored: Boolean, thisSide: Boolean): SetParameters {
    val stored = if (side == Side.RIGHT && !parentMirrored) 1f - v else v
    val paired = character.part(parent)?.pair != null
    if (paired || side == null) {
        return SetParameters(parent, mapOf(PlacementParams.socketU(socket) to u, PlacementParams.socketV(socket) to stored), paired && thisSide)
    }
    if (thisSide) {
        return SetParameters(parent, mapOf(PlacementParams.socketU(socket, side) to u, PlacementParams.socketV(socket, side) to stored))
    }
    val cleared: Map<String, Float?> = Side.entries.flatMap { listOf(PlacementParams.socketU(socket, it) to null, PlacementParams.socketV(socket, it) to null) }.toMap()
    return SetParameters(parent, cleared + mapOf(PlacementParams.socketU(socket) to u, PlacementParams.socketV(socket) to stored))
}
