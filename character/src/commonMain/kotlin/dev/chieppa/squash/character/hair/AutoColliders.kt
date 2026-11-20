package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Collider
import dev.chieppa.squash.document.ColliderShape
import dev.chieppa.squash.document.LockPoint
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Vec3

/** How many places round a ring its radius is read from. */
private const val RING_SAMPLES = 12

/** How much of the head's reach its sphere takes, so it sits inside the skull rather than round the muzzle. */
private const val HEAD_SHARE = 0.85f

/** How far a shoulder's sphere grows past its arm's root ring. */
private const val SHOULDER_GROWTH = 1.3f

private fun Vec3.point() = LockPoint(x, y, z)

/**
 * The collision shapes [character] gets from its body at rest: a sphere in each head, a capsule along each neck from its root to its end,
 * and a sphere round each shoulder, the root of each arm. See HLD: data-model, Hair locks.
 */
fun autoColliders(character: AssembledCharacter): List<Collider> = buildList {
    for (part in character.parts) {
        val id = part.id ?: continue
        if (part.covering != null || part.hanging != null || part.spanning != null || part.locks != null || part.owner != null) continue
        when (part.role) {
            Role.HEAD -> {
                val scalp = Scalp.of(part, null)
                val reach = headReach(scalp) * HEAD_SHARE
                add(Collider("head-${id.value}", id, ColliderShape.SPHERE, scalp.middle.point(), radius = reach.coerceAtLeast(1e-3f)))
            }
            Role.NECK -> {
                val from = part.middleAt(0f) ?: continue
                val to = part.middleAt(1f) ?: continue
                add(Collider("neck-${id.value}", id, ColliderShape.CAPSULE, from.point(), to.point(), ringRadius(part, 0.5f).coerceAtLeast(1e-3f)))
            }
            Role.ARM -> {
                val root = part.middleAt(0f) ?: continue
                add(Collider("shoulder-${id.value}", id, ColliderShape.SPHERE, root.point(), radius = (ringRadius(part, 0f) * SHOULDER_GROWTH).coerceAtLeast(1e-3f)))
            }
            else -> Unit
        }
    }
}

/** How far a head reaches from its middle, as [scalp] reads it: the mean of its reach round it a little above level and over its top. */
fun headReach(scalp: Scalp): Float =
    ((0 until 8).map { scalp.reachAt(it * 45f, 20f) } + scalp.reachAt(0f, 90f)).average().toFloat()

/** The mean distance of [part]'s ring at [u] from its middle. */
private fun ringRadius(part: PlacedPart, u: Float): Float {
    val middle = part.middleAt(u) ?: return 0f
    val around = (0 until RING_SAMPLES).mapNotNull { part.pointOn(u, it.toFloat() / RING_SAMPLES) }
    return if (around.isEmpty()) 0f else around.map { (it - middle).length }.average().toFloat()
}
