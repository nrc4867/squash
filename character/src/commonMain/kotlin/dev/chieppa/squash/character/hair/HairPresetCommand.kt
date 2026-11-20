package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetColliders
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.document.Together

/** The template an inner mass is built from. */
const val MASS_TEMPLATE = "hair-volume"

/** The hair mass on [head]: the first part of the hair-volume template sitting on it, or null. */
fun massOn(character: Character, head: PartId): PartId? =
    character.parts.firstOrNull { it.template.id.value == MASS_TEMPLATE && it.socket?.parent == head }?.id

/**
 * One step that gives [head] the lock groups of [preset] in place of those it holds, rooted on its hair mass where it has one and sized to
 * the head as [assembled] builds it, and sets up the body's collision shapes where the character holds none. Null when the head builds nothing.
 */
fun hairPresetCommand(character: Character, assembled: AssembledCharacter, head: PartId, preset: HairPreset): DocumentCommand? {
    val placed = assembled.part(head) ?: return null
    val mass = massOn(character, head)
    val reach = headReach(Scalp.of(placed, mass?.let(assembled::part)))
    val others = character.lockGroups.filter { it.on != head }.map { it.id.value }.toSet()
    val prefix = if (presetGroupNames(preset).any { it in others }) "${head.value}-" else ""
    val removed = character.lockGroups.filter { it.on == head }.map { SetLockGroup(it.id, null) }
    val added = presetGroups(preset, head, mass, reach, prefix).map { SetLockGroup(it.id, it) }
    val colliders = if (character.colliders.isEmpty()) listOf(SetColliders(autoColliders(assembled))) else emptyList()
    return Together(removed + added + colliders)
}
