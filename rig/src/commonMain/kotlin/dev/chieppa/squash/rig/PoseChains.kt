package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role

/**
 * A leg, an arm or a wing as a pose reaches with it, by places in [Rig.bones]: its [upper] and [lower] bones, the [end] joint its paw
 * hangs from, and the paw's own bone, [foot], when a paw sits there. It is named by its paw, [id], which the planted set holds. A limb
 * that [plants] stands on the ground and can be pinned there; a wing cannot, nor can an arm on an upright body, which reaches with its hand.
 */
class Limb(val leg: PartId, val paw: PartId?, val upper: Int, val lower: Int, val end: Int, val foot: Int?, val plants: Boolean = true) {
    val id: PartId get() = paw ?: leg
}

/** A tail, a chain of bones curled in equal shares, by places in [Rig.bones], root first. */
class Tail(val part: PartId, val bones: List<Int>)

/** What turns to look: the [head]'s bone, and the [neck] bones above it, which take a share of the turn. */
class LookChain(val head: Int, val neck: List<Int>)

/**
 * The handles a pose is made with, found by the parts' roles in the rig's shape: the [root] bone that carries the whole body, every
 * [limbs] leg, arm or wing with the foot or hand at its end, every [tails] chain, and the [look] chain to the head, through any necks above it.
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
    val roles = rig.parts.associate { it.id to it.role }
    val upright = rig.isUpright
    fun withRole(of: Set<Role>) = rig.parts.filter { roles[it.id] in of && it.id in bonesOf }.map { it.id }
    val limbs = withRole(Role.LIMBS).mapNotNull { leg ->
        val bones = bonesOf.getValue(leg).takeIf { it.size >= LIMB_JOINTS } ?: return@mapNotNull null
        val end = bones[2]
        val paw = children[leg].orEmpty().firstOrNull { roles[it] in Role.LIMB_ENDS && rig.parents[bonesOf.getValue(it).first()] == end }
        Limb(leg, paw, bones[0], bones[1], end, paw?.let { bonesOf.getValue(it).first() }, plants(roles[leg], upright))
    }
    val tails = withRole(setOf(Role.TAIL)).filter { bonesOf.getValue(it).size >= 2 }.map { Tail(it, bonesOf.getValue(it)) }
    val look = withRole(setOf(Role.HEAD)).firstOrNull()?.let { head ->
        val necks = generateSequence(parentPart[head]) { parentPart[it] }.takeWhile { roles[it] == Role.NECK }.toList().reversed()
        val headBone = bonesOf.getValue(head).first()
        val neck = if (necks.isEmpty()) listOfNotNull(rig.parents[headBone].takeIf { it >= 0 }) else necks.flatMap { bonesOf.getValue(it) }
        LookChain(headBone, neck)
    }
    return PoseChains(root, limbs, tails, look)
}

/** Whether a limb of [role] stands on the ground: a leg does, and an arm does on a body that is not [upright]; a wing never does. */
fun plants(role: Role?, upright: Boolean): Boolean = role == Role.LEG || role == Role.ARM && !upright

/** How far up the root part must rest for the body to stand upright, as its rest axis's upward share. */
private const val UPRIGHT = 0.7f

/** The part the root bone belongs to, the body every other part hangs from. */
val Rig.rootPart: RigPart?
    get() = parents.indexOfFirst { it < 0 }.takeIf { it >= 0 }?.let { part(bones[it].name.part) }

/** Whether the body stands upright, its root part resting up, as a biped's torso does; its arms then reach and never plant. */
val Rig.isUpright: Boolean
    get() = (rootPart?.axis?.y ?: 0f) >= UPRIGHT

/** A limb's hip, knee and the end its paw hangs from. */
private const val LIMB_JOINTS = 3
