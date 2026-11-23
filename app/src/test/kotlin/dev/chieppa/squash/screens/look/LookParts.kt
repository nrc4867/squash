package dev.chieppa.squash.screens.look

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side

/**
 * The parts of a character the close-ups and checks are about, the left one of a pair, each found by its use: a limb's end is a hand on an
 * arm and a foot on a leg, whatever its template, so a paw on an arm is a hand; anything else by its role.
 */
class LookParts(private val character: Character) {
    private val parts = character.parts.filter { it.garment == null }

    private fun first(use: Role): PartId? = parts.firstOrNull { useOf(it) == use && it.side != Side.RIGHT }?.id

    /** What [part] is used as: a limb's end by the limb it hangs from, anything else by its role. */
    fun useOf(part: PartInstance): Role? {
        val role = part.role ?: return null
        if (role !in Role.LIMB_ENDS) return role
        return when (part.socket?.parent?.let(character::part)?.role) {
            Role.ARM -> Role.HAND
            Role.LEG -> Role.FOOT
            else -> role
        }
    }

    val head: PartId? = first(Role.HEAD)
    val hand: PartId? = first(Role.HAND)
    val foot: PartId? = first(Role.FOOT)
    val arm: PartId? = first(Role.ARM)
    val leg: PartId? = first(Role.LEG)
    val tail: PartId? = first(Role.TAIL)

    /** Both sides of the arm pair, or the one arm. */
    val arms: List<PartId> = parts.filter { it.role == Role.ARM }.map { it.id }

    /** Both sides of the leg pair, or the one leg. */
    val legs: List<PartId> = parts.filter { it.role == Role.LEG }.map { it.id }

    /** The head and every part sitting on it, as a face close-up frames them. */
    fun faceFramed(head: PartId): Set<PartId> = setOf(head) + character.parts.filter { it.socket?.parent == head }.map { it.id }
}
