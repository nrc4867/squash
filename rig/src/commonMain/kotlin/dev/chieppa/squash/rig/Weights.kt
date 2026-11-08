package dev.chieppa.squash.rig

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3

/** The most bones one vertex follows. */
const val MAX_INFLUENCES = 4

/** A weight below this is dropped and the rest shared out again. */
private const val MIN_WEIGHT = 1e-4f

/**
 * Which bones each vertex of a part's mesh follows and how much, [MAX_INFLUENCES] slots a vertex, in the order of the part
 * surface's mesh. A slot holds an index into [Rig.bones], or -1 when empty, and a vertex's weights sum to one. A vertex
 * with every slot empty follows no bone and stays where it rests.
 */
class PartWeights(val part: PartId, val bones: IntArray, val weights: FloatArray) {
    val vertexCount: Int get() = bones.size / MAX_INFLUENCES

    /** The bones [vertex] follows, each with its weight, heaviest first. */
    fun of(vertex: Int): List<Pair<Int, Float>> =
        (0 until MAX_INFLUENCES).map { vertex * MAX_INFLUENCES + it }.filter { bones[it] >= 0 }.map { bones[it] to weights[it] }

    /** How much [vertex] follows [bone], 0 when not at all. */
    fun weightOf(vertex: Int, bone: Int): Float {
        var total = 0f
        for (slot in 0 until MAX_INFLUENCES) if (bones[vertex * MAX_INFLUENCES + slot] == bone) total += weights[vertex * MAX_INFLUENCES + slot]
        return total
    }
}

/** One vertex being weighed: its bones and shares so far. */
private class Blend(own: Map<Int, Float>) {
    val shares = own.toMutableMap()

    /** Gives [share] of the whole to [bone], the rest keeping their proportions. */
    fun give(bone: Int, share: Float) {
        if (share <= 0f) return
        shares.replaceAll { _, w -> w * (1f - share) }
        shares[bone] = (shares[bone] ?: 0f) + share
    }

    /** Gives each bone of [shares] its share at once. */
    fun giveAll(given: Map<Int, Float>) {
        val total = given.values.sum()
        if (total <= 0f) return
        shares.replaceAll { _, w -> w * (1f - total) }
        given.forEach { (bone, share) -> shares[bone] = (shares[bone] ?: 0f) + share }
    }

    fun write(vertex: Int, bones: IntArray, weights: FloatArray) {
        val kept = shares.entries.filter { it.value >= MIN_WEIGHT }.sortedByDescending { it.value }.take(MAX_INFLUENCES)
        val total = kept.sumOf { it.value.toDouble() }.toFloat()
        for (slot in 0 until MAX_INFLUENCES) {
            val entry = kept.getOrNull(slot)
            bones[vertex * MAX_INFLUENCES + slot] = entry?.key ?: -1
            weights[vertex * MAX_INFLUENCES + slot] = if (entry != null && total > 0f) entry.value / total else 0f
        }
    }
}

/**
 * Every vertex of [part]: its address as the bands weigh it, by the u its bone reads, the anchor that lands on it, and its place
 * in body space.
 */
internal class PartVertices(part: RiggedPart) {
    val addresses: List<SeamVertex>
    val anchors: List<Anchor>
    val positions: FloatArray

    init {
        val surface = part.placed.surface
        val list = ArrayList<SeamVertex>(surface.mesh.vertexCount)
        val landing = ArrayList<Anchor>(surface.mesh.vertexCount)
        for (patch in surface.patches) for (local in 0 until patch.nu * patch.nv) {
            val v = (local % patch.nv).toFloat() / patch.segments
            list += SeamVertex(part.id, patch.name, patch.uOf(local), v)
            landing += Anchor(part.id, patch.name, patch.ringUs[local / patch.nv], v)
        }
        addresses = list
        anchors = landing
        positions = FloatArray(list.size * 3) { i ->
            val p = surface.mesh.position(i / 3)
            val o = part.placed.origin
            when (i % 3) {
                0 -> p.x + o.x
                1 -> p.y + o.y
                else -> p.z + o.z
            }
        }
    }

    fun position(vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])
}

/**
 * The weights of [part]: each vertex on the bone owning its u, blended with the next across a band of [JOINT_REACH] radii
 * either side of each joint inside the part, with its parent's bone across the seam band at its socket, and with each
 * child's first bone round that child's rim, each band as wide as [reach] sets. [index] gives a bone's place in the rig,
 * [parent] the part [part] sits on, [children] the parts on [part]'s sockets, and [paint] what painted strokes make of each seam.
 */
internal fun weightsOf(
    part: RiggedPart,
    carrier: BoneName?,
    parent: RiggedPart?,
    children: List<RiggedPart>,
    index: Map<BoneName, Int>,
    paint: SeamPaint,
    checkCancelled: () -> Unit,
    reach: JointReach = JointReach.GUESSED,
    vertices: PartVertices = PartVertices(part),
): PartWeights {
    val count = vertices.addresses.size
    val bones = IntArray(count * MAX_INFLUENCES)
    val weights = FloatArray(count * MAX_INFLUENCES)
    val own = OwnWeights(part, index, reach)
    val carrierIndex = carrier?.let(index::get)
    checkCancelled()
    val parentShares = if (carrierIndex != null && !part.joints.isEmpty) {
        val band = childBandOf(part, parent, reach)
        paint.shares(Seam(part.id), SeamVertices(part.id, true, vertices.addresses, vertices.positions), FloatArray(count) { childShare(vertices.addresses[it].u, band) })
    } else {
        null
    }
    val rims = children.mapNotNull { child -> Rim.of(child, part, index, reach) }.map { rim ->
        checkCancelled()
        val guessed = FloatArray(count) { rimShare((vertices.position(it) - rim.anchor).length, rim.radius) }
        rim.bone to paint.shares(rim.seam, SeamVertices(part.id, false, vertices.addresses, vertices.positions), guessed)
    }
    for (vertex in 0 until count) {
        val address = vertices.addresses[vertex]
        val u = address.u
        val blend = Blend(if (part.joints.isEmpty) carrierIndex?.let { mapOf(it to 1f) }.orEmpty() else own.at(address.patch, u))
        if (carrierIndex != null && parentShares != null) blend.give(carrierIndex, parentShares[vertex])
        if (rims.isNotEmpty()) blend.giveAll(heldShares(rims.associate { (bone, shares) -> bone to shares[vertex] }))
        blend.write(vertex, bones, weights)
    }
    return PartWeights(part.id, bones, weights)
}

/** The u a vertex of this patch weighs by: the patch's own for one that rides at one u, as a pad does, else its ring's. */
private fun Patch.uOf(local: Int): Float = boneU ?: ringUs[local / nv]

/** What several rims give one vertex, held so together they never take more than the seam's share, or than the most one was painted to. */
private fun heldShares(given: Map<Int, Float>): Map<Int, Float> {
    val total = given.values.sum()
    val most = maxOf(SEAM_SHARE, given.values.maxOrNull() ?: 0f)
    return if (total <= most) given else given.mapValues { it.value * most / total }
}

/** A child's seam as its parent's vertices meet it. */
private class Rim(val seam: Seam, val bone: Int, val anchor: Vec3, val radius: Float) {
    companion object {
        fun of(child: RiggedPart, parent: RiggedPart, index: Map<BoneName, Int>, reach: JointReach): Rim? {
            if (child.joints.isEmpty) return null
            val anchor = child.placed.anchor ?: return null
            val bone = index[child.bone(0)] ?: return null
            return Rim(Seam(child.id), bone, anchor, seamRadius(child, parent) * reach.of(child.id, child.joints.specs[0].name))
        }
    }
}

/**
 * How far along u a part's seam band reaches from its socket end: [SEAM_REACH] seam radii times its first joint's [reach], held to
 * half the way to its next joint.
 */
internal fun childBandOf(part: RiggedPart, parent: RiggedPart?, reach: JointReach = JointReach.GUESSED): Float {
    val perU = part.rings.lengthPerU
    if (perU <= 0f || part.joints.isEmpty) return 0f
    val next = if (part.joints.owning > 1) part.joints.specs[1].u else 1f
    val factor = reach.of(part.id, part.joints.specs[0].name)
    return (SEAM_REACH * factor * seamRadius(part, parent) / perU).coerceAtMost(next / 2f)
}

/**
 * The weights a part gives its own bones alone, by patch and u: along its chain, and along each digit from the bone it branches
 * from through the digit's joints, a band across each joint.
 */
internal class OwnWeights(private val part: RiggedPart, index: Map<BoneName, Int>, private val reach: JointReach = JointReach.GUESSED) {
    private val joints = part.joints
    private val bones = IntArray(joints.specs.size) { index.getValue(part.bone(it)) }

    /** Half the band across each owning joint after the first, along u; 0 for the first. */
    val halfWidths = FloatArray(joints.owning) { if (it == 0) 0f else jointBand(it) }

    /** Half the band across each joint of each digit, along u. */
    val digitHalfWidths = joints.digits.indices.map { d -> FloatArray(joints.digits[d].joints.size) { k -> digitBand(d, k) } }

    /** The shares a vertex of [patch] at [u] gives the part's bones. */
    fun at(patch: String, u: Float): Map<Int, Float> = joints.digitOf(patch)?.let { alongDigit(it, u) } ?: at(u)

    /** The shares a vertex of the part's own chain at [u] gives its bones. */
    fun at(u: Float): Map<Int, Float> {
        for (k in 1 until joints.owning) {
            val h = halfWidths[k]
            val ju = joints.specs[k].u
            if (h > 0f && u > ju - h && u < ju + h) {
                val t = smoothStep((u - (ju - h)) / (2f * h))
                return mapOf(bones[k - 1] to 1f - t, bones[k] to t)
            }
        }
        return mapOf(bones[joints.ownerAt(u)] to 1f)
    }

    private fun alongDigit(d: Int, u: Float): Map<Int, Float> {
        val chain = joints.digits[d].joints
        for (k in chain.indices) {
            val h = digitHalfWidths[d][k]
            val ju = chain[k].u
            if (h > 0f && u > ju - h && u < ju + h) {
                val t = smoothStep((u - (ju - h)) / (2f * h))
                val before = if (k == 0) joints.rootOf(d) else joints.indexOf(d, k - 1)
                return mapOf(bones[before] to 1f - t, bones[joints.indexOf(d, k)] to t)
            }
        }
        val k = chain.count { it.u <= u }
        return mapOf(bones[if (k == 0) joints.rootOf(d) else joints.indexOf(d, k - 1)] to 1f)
    }

    /** Half the band across the joint at [k] along u, its reach times [JOINT_REACH] radii, held to half the way to the joints around it. */
    private fun jointBand(k: Int): Float {
        val perU = part.rings.lengthPerU
        if (perU <= 0f) return 0f
        val u = joints.specs[k].u
        val room = minOf(u - joints.specs[k - 1].u, joints.spanEnd(k) - u) / 2f
        return (JOINT_REACH * reach.of(part.id, joints.specs[k].name) * part.rings.radius(u) / perU).coerceAtMost(room)
    }

    /** [jointBand] for joint [k] of digit [d], over the digit's own radius, held between its root, its joints and its tip. */
    private fun digitBand(d: Int, k: Int): Float {
        val perU = part.rings.lengthPerU
        if (perU <= 0f) return 0f
        val digit = joints.digits[d]
        val joint = digit.joints[k]
        val before = if (k == 0) digit.span.start else digit.joints[k - 1].u
        val room = minOf(joint.u - before, joints.digitSpanEnd(d, k) - joint.u) / 2f
        return (JOINT_REACH * reach.of(part.id, joint.name) * part.rings.radius(joint.u, digit.patch) / perU).coerceAtMost(room)
    }
}
