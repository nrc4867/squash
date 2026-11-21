package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetColliders
import dev.chieppa.squash.document.SetHairShell
import dev.chieppa.squash.document.hairShellOn
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.document.SetScalpFlow
import dev.chieppa.squash.document.Together

/** The template an inner mass is built from. */
const val MASS_TEMPLATE = "hair-volume"

/** The hair mass on [head]: the first part of the hair-volume template sitting on it, or null. */
fun massOn(character: Character, head: PartId): PartId? =
    character.parts.firstOrNull { it.template.id.value == MASS_TEMPLATE && it.socket?.parent == head }?.id

/**
 * One step that gives [head] the lock groups of [preset] in place of those it holds, rooted on its hair mass, sized to the head as
 * [assembled] builds it, with the preset's scalp flow and its short-hair shell (or none), and sets up the body's collision shapes where
 * the character holds none. A head with no mass gets one fitted over it in the same step where the preset lies on one, so no scalp shows
 * between the locks; a preset cut short at the sides roots on the head under its shell. Null when the head builds nothing.
 */
fun hairPresetCommand(character: Character, assembled: AssembledCharacter, head: PartId, preset: HairPreset): DocumentCommand? {
    val placed = assembled.part(head) ?: return null
    val massed = presetMassed(preset)
    val held = massOn(character, head).takeIf { massed }
    val fitted = if (held == null && massed) fittedMass(character, placed, head) else null
    val mass = held ?: fitted?.id
    val reach = headReach(Scalp.of(placed, held?.let(assembled::part)))
    val others = character.lockGroups.filter { it.on != head }.map { it.id.value }.toSet()
    val prefix = if (presetGroupNames(preset).any { it in others }) "${head.value}-" else ""
    val removed = character.lockGroups.filter { it.on == head }.map { SetLockGroup(it.id, null) }
    val added = presetGroups(preset, head, mass, reach, prefix).map { SetLockGroup(it.id, it) }
    val colliders = if (character.colliders.isEmpty()) listOf(SetColliders(autoColliders(assembled))) else emptyList()
    val flow = SetScalpFlow(head, presetFlow(preset, head))
    val shell = presetShell(preset, head)
    val shells = if (shell == null && character.hairShellOn(head) == null) emptyList() else listOf(SetHairShell(head, shell))
    return Together(fitted?.commands.orEmpty() + removed + added + flow + shells + colliders)
}
