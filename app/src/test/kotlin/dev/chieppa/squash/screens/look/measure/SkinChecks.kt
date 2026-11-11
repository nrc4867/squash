package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.geom.Vec3

/**
 * How many vertices of each body part show and are seen while they lie inside the shell of a garment's branch over that part, by
 * part: skin through or between garments where they cover it.
 */
fun bareSkin(body: AssembledCharacter, seen: (Vec3) -> Boolean): Map<String, Int> {
    val branches = body.parts.filter { it.covering != null }.groupBy { requireNotNull(it.covering).part }
    return body.parts.filter { it.isBody && it.id in branches }.associate { part ->
        val id = requireNotNull(part.id)
        val shells = branches.getValue(id).map(::Winding)
        val used = part.usedVertices()
        id.value to used.indices.count { v -> used[v] && part.point(v).let { p -> shells.any { it.inside(p) } && seen(p) } }
    }.filterValues { it > 0 }
}
