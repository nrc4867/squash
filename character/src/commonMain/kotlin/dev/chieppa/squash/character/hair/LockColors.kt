package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.PartColor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.blendedSlot
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.document.scalpFlowOn

/** The palette slot lock hair takes where neither it nor its mass names one. */
val HAIR_SLOT = SlotName("hair")

/**
 * The base colour of every lock group [character] holds and of each band: its own slot, else its mass's base slot, else the palette's
 * hair slot, else its head's; each parting's strip of scalp, and each short-hair shell's stubble tone.
 */
fun lockColors(character: Character): Map<PartId, PartColor> = character.lockGroups.flatMap { group ->
    listOfNotNull(
        lockGroupPartId(group.id) to PartColor(groupSlot(character, group), emptyList()),
        group.band?.let { lockBandPartId(group.id) to PartColor(it, emptyList()) },
    )
}.toMap() + partingColors(character) + shellColors(character)

/** Each of [character]'s short-hair shells as its build reads it, with its head's base slot, the skin its edge and fade show. */
fun shellSpecs(character: Character): List<ShellSpec> = character.hairShells.map { ShellSpec(it, skinOf(character, it.on)) }

/** How far a parting's scalp tone carries the head's colour toward its hair's: a skin darkened toward the hair, as scalp shows through it. */
private const val SCALP_TOWARD_HAIR = 0.4f

/** [group]'s slot: its own, else its mass's base slot, else the palette's hair slot, else its head's base slot. */
private fun groupSlot(character: Character, group: LockGroup): SlotName? = group.color
    ?: group.mass?.let(character::part)?.let { character.resolved(it).surface.baseSlot }
    ?: paletteHair(character)
    ?: skinOf(character, group.on)

private fun paletteHair(character: Character): SlotName? = HAIR_SLOT.takeIf { slot -> character.palette.slots.any { it.name == slot } }

private fun skinOf(character: Character, head: PartId): SlotName? = character.part(head)?.let { character.resolved(it).surface.baseSlot }

/**
 * The colour of the strip of scalp along each parting over a head with locks: the parting's own slot, else the head's base colour carried
 * toward its first group's hair colour, a scalp tone.
 */
private fun partingColors(character: Character): Map<PartId, PartColor> = character.lockGroups.groupBy { it.on }.mapNotNull { (head, groups) ->
    val parting = character.scalpFlowOn(head).parting
    if (!parting.shown) return@mapNotNull null
    val skin = skinOf(character, head)
    val hair = groupSlot(character, groups.first())
    val slot = parting.color ?: if (skin != null && hair != null && skin != hair) blendedSlot(skin, hair, SCALP_TOWARD_HAIR) else skin
    partingPartId(head) to PartColor(slot, emptyList())
}.toMap()

/**
 * Each shell's tone: its own slot, else the head's base colour carried its shade of the way toward the hair's, the first group's on that
 * head, else the palette's hair slot.
 */
private fun shellColors(character: Character): Map<PartId, PartColor> = character.hairShells.associate { shell ->
    shellPartId(shell.on) to PartColor(shellTone(character, shell), emptyList())
}

private fun shellTone(character: Character, shell: HairShell): SlotName? {
    shell.tone?.let { return it }
    val skin = skinOf(character, shell.on)
    val hair = character.lockGroups.firstOrNull { it.on == shell.on }?.let { groupSlot(character, it) } ?: paletteHair(character)
    return if (skin != null && hair != null && skin != hair) blendedSlot(skin, hair, shell.shade) else hair ?: skin
}
