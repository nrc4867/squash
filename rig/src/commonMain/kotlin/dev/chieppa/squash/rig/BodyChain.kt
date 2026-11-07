package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs

/** How close two bones' pivots must mirror each other across the body's plane to make a pair. */
private const val TWIN_TOLERANCE = 1e-3f

/** A step of the body chain, from rest point [from] to [to], both carried by the bone [owner], a place in [Rig.bones]. */
internal class ChainLink(val owner: Int, val from: Vec3, val to: Vec3, val ahead: Boolean)

/**
 * The bones a body curve bends, as steps from the end of the part behind the root part to the end of the part ahead of it:
 * on the fennec from the tail's tip through the tail, the torso's joints and the head's base to the head's end. The root
 * part is the root bone's; behind and ahead are its unpaired children with joints, hanging from its first and last carrying
 * bones. [root] is the first step on the root part. Steps on the part ahead are [ChainLink.ahead]. A bone's span stretches
 * with the curve's length when it is in [stretched].
 */
internal class BodyChain(val links: List<ChainLink>, val root: Int, val stretched: Set<Int>)

internal fun bodyChain(rig: Rig): BodyChain? {
    val rootBone = rig.parents.indexOfFirst { it < 0 }.takeIf { it >= 0 } ?: return null
    val rootPart = rig.bones[rootBone].name.part
    val spine = rig.bones.indices.filter { rig.bones[it].name.part == rootPart }
    val children = rig.bones.indices
        .filter { rig.bones[it].name.part != rootPart && rig.parents[it] in spine && firstOfPart(rig, it) && !hasTwin(rig, it) }
        .sortedBy { spine.indexOf(rig.parents[it]) }
    val behind = children.firstOrNull()?.takeIf { children.size >= 2 }
    val ahead = children.lastOrNull()
    val start = behind?.let { spine.indexOf(rig.parents[it]) } ?: 0
    val end = ahead?.let { spine.indexOf(rig.parents[it]) } ?: spine.lastIndex
    val links = mutableListOf<ChainLink>()
    val stretched = mutableSetOf<Int>()
    if (behind != null) {
        val tail = owning(rig, partBones(rig, behind))
        for (k in tail.indices.reversed()) {
            val next = rig.bones[tail[k]].let { it.pivot + it.direction * it.length }
            links += ChainLink(tail[k], next, rig.bones[tail[k]].pivot, ahead = false)
        }
        stretched += tail
    }
    val root = links.size
    var from = behind?.let { rig.bones[it].pivot } ?: rig.bones[spine[start]].pivot
    for (k in start..end) {
        val to = when {
            k < end -> rig.bones[spine[k + 1]].pivot
            ahead != null -> rig.bones[ahead].pivot
            else -> rig.bones[spine[k]].let { it.pivot + it.direction * it.length }
        }
        links += ChainLink(spine[k], from, to, ahead = false)
        stretched += spine[k]
        from = to
    }
    if (ahead != null) for (bone in owning(rig, partBones(rig, ahead))) {
        val b = rig.bones[bone]
        links += ChainLink(bone, b.pivot, b.pivot + b.direction * b.length, ahead = true)
    }
    return BodyChain(links, root, stretched)
}

private fun firstOfPart(rig: Rig, bone: Int): Boolean = rig.bones.indexOfFirst { it.name.part == rig.bones[bone].name.part } == bone

private fun partBones(rig: Rig, first: Int): List<Int> = rig.bones.indices.filter { rig.bones[it].name.part == rig.bones[first].name.part }

/** The bones of a part that own a span of it. */
private fun owning(rig: Rig, bones: List<Int>): List<Int> = bones.filter { rig.bones[it].length > 0f }

/** Whether another part's first bone hangs from the same bone at [bone]'s pivot mirrored across the body's plane. */
private fun hasTwin(rig: Rig, bone: Int): Boolean {
    val pivot = rig.bones[bone].pivot
    return rig.bones.indices.any { other ->
        other != bone && rig.parents[other] == rig.parents[bone] && firstOfPart(rig, other) &&
            (rig.bones[other].pivot - pivot.mirrored).length < TWIN_TOLERANCE && abs(pivot.z) > TWIN_TOLERANCE
    }
}

