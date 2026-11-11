package dev.chieppa.squash.screens.look

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side

/** The parts of a character the close-ups and checks are about, found by role, the left one of a pair. */
class LookParts(private val character: Character) {
    private fun first(role: Role): PartId? = character.parts.firstOrNull { it.role == role && it.side != Side.RIGHT }?.id

    val head: PartId? = first(Role.HEAD)
    val hand: PartId? = first(Role.HAND)
    val foot: PartId? = first(Role.FOOT)

    /** The head and every part sitting on it, as a face close-up frames them. */
    fun faceFramed(head: PartId): Set<PartId> = setOf(head) + character.parts.filter { it.socket?.parent == head }.map { it.id }
}
