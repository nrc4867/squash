package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs

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
