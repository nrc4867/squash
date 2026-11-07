package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId

/**
 * A leg as a pose reaches with it, by places in [Rig.bones]: its [upper] and [lower] bones, the [end] joint its paw hangs from,
 * and the paw's own bone, [foot], when a paw sits there. It is named by its paw, [id], which the planted set holds.
 */
class Limb(val leg: PartId, val paw: PartId?, val upper: Int, val lower: Int, val end: Int, val foot: Int?) {
    val id: PartId get() = paw ?: leg
}

/** A tail, a chain of bones curled in equal shares, by places in [Rig.bones], root first. */
class Tail(val part: PartId, val bones: List<Int>)

/** What turns to look: the [head]'s bone, and the [neck] bones above it, which take a share of the turn. */
class LookChain(val head: Int, val neck: List<Int>)

/**
 * The handles a pose is made with, found in the rig's shape: the [root] bone that carries the whole body, every [limbs] leg with
 * its paw, every [tails] chain hanging from the root's part with nothing on it, and the [look] chain to the head, the part
 * hanging from the root that carries parts of its own, followed through any one-child part between, such as a neck.
 */
class PoseChains(val root: Int, val limbs: List<Limb>, val tails: List<Tail>, val look: LookChain?) {
    /** The limb named [id], by its paw or its leg. */
    fun limb(id: PartId): Limb? = limbs.firstOrNull { it.id == id || it.leg == id }

    companion object {
        val NONE = PoseChains(-1, emptyList(), emptyList(), null)
    }
}

/** [rig]'s handles; see [PoseChains]. */
fun poseChains(rig: Rig): PoseChains {
    val root = rig.parents.indexOfFirst { it < 0 }
    if (root < 0) return PoseChains.NONE
    val bonesOf = rig.bones.indices.groupBy { rig.bones[it].name.part }
    val parentPart = bonesOf.mapValues { (_, bones) -> rig.parents[bones.first()].takeIf { it >= 0 }?.let { rig.bones[it].name.part } }
    val children = parentPart.entries.filter { it.value != null }.groupBy({ it.value!! }, { it.key })
    val rootPart = rig.bones[root].name.part
    val weightBearing = rig.parts.filter { it.bearsWeight }.map { it.id }.toSet()
    val limbs = weightBearing.mapNotNull { leg ->
        val bones = bonesOf[leg]?.takeIf { it.size >= LIMB_JOINTS } ?: return@mapNotNull null
        val end = bones[2]
        val paw = children[leg].orEmpty().firstOrNull { rig.parents[bonesOf.getValue(it).first()] == end }
        Limb(leg, paw, bones[0], bones[1], end, paw?.let { bonesOf.getValue(it).first() })
    }
    val fromRoot = children[rootPart].orEmpty().filter { it !in weightBearing }
    val tails = fromRoot.filter { children[it].isNullOrEmpty() && (bonesOf[it]?.size ?: 0) >= 2 }.map { Tail(it, bonesOf.getValue(it)) }
    val look = fromRoot.firstOrNull { !children[it].isNullOrEmpty() }?.let { first ->
        var head = first
        val between = ArrayList<PartId>()
        while (true) {
            val next = children[head].orEmpty().singleOrNull { !children[it].isNullOrEmpty() } ?: break
            between += head
            head = next
        }
        val headBone = bonesOf.getValue(head).first()
        val neck = if (between.isEmpty()) listOfNotNull(rig.parents[headBone].takeIf { it >= 0 }) else between.flatMap { bonesOf.getValue(it) }
        LookChain(headBone, neck)
    }
    return PoseChains(root, limbs, tails, look)
}

/** A limb's hip, knee and the end its paw hangs from. */
private const val LIMB_JOINTS = 3
