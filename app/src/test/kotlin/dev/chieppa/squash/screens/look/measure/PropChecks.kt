package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.rig.Rig
import kotlin.math.abs

/** Each part a garment owns that [rig] holds, by id, with the bones it moves with: `follow` when bound to the body's, else `single`. */
fun bindLines(rig: Rig, rest: AssembledCharacter): String? {
    val owned = rest.parts.filter { it.owner != null }.mapNotNull { placed -> placed.id?.let(rig::part) }
    if (owned.isEmpty()) return null
    return "owned parts bound " + owned.joinToString { "${it.id.value} ${if (it.hold != null) "follow" else "single"}" }
}

/**
 * How far each part a garment owns strays from moving rigidly between [rest] and [posed]: the largest change in any vertex's distance
 * from the part's first vertex, in body units. A prop riding one bone stays near 0.
 */
fun propDrift(rest: AssembledCharacter, posed: AssembledCharacter): Map<String, Float> = rest.parts.filter { it.owner != null }.mapNotNull { before ->
    val after = posed.part(requireNotNull(before.id)) ?: return@mapNotNull null
    val count = minOf(before.surface.mesh.vertexCount, after.surface.mesh.vertexCount)
    if (count == 0) return@mapNotNull null
    val a0 = before.point(0)
    val b0 = after.point(0)
    requireNotNull(before.id).value to (0 until count).maxOf { v -> abs((before.point(v) - a0).length - (after.point(v) - b0).length) }
}.toMap()
