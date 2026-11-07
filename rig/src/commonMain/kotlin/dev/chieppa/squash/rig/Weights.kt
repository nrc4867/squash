package dev.chieppa.squash.rig

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
 * The weights of [part]: each vertex on the bone owning its u, blended with the next across a band of [JOINT_REACH] radii
 * either side of each joint inside the part, with its parent's bone across the seam band at its socket, and with each
 * child's first bone round that child's rim. [index] gives a bone's place in the rig, [parent] the part [part] sits on, [children] the
 * parts on [part]'s sockets, and [paint] what painted strokes make of each seam.
 */
internal fun weightsOf(
    part: RiggedPart,
    carrier: BoneName?,
    parent: RiggedPart?,
    children: List<RiggedPart>,
    index: Map<BoneName, Int>,
    paint: SeamPaint,
    checkCancelled: () -> Unit,
): PartWeights {
    val surface = part.placed.surface
    val vertices = surface.mesh.vertexCount
    val bones = IntArray(vertices * MAX_INFLUENCES)
    val weights = FloatArray(vertices * MAX_INFLUENCES)
    val own = OwnWeights(part, index)
    val carrierIndex = carrier?.let(index::get)
    val childBand = if (carrierIndex != null) childBandOf(part, parent) else 0f
    val rims = children.mapNotNull { child -> Rim.of(child, part, index) }
    var first = 0
    for (patch in surface.patches) {
        checkCancelled()
        for (local in 0 until patch.nu * patch.nv) {
            val vertex = first + local
            val at = SeamVertex(part.id, patch.name, patch.uOf(local), (local % patch.nv).toFloat() / patch.segments)
            val blend = Blend(if (part.joints.isEmpty) carrierIndex?.let { mapOf(it to 1f) }.orEmpty() else own.at(at.u))
            if (carrierIndex != null && !part.joints.isEmpty) {
                blend.give(carrierIndex, paint.share(Seam(part.id), at, childShare(at.u, childBand)))
            }
            if (rims.isNotEmpty()) {
                val position = part.placed.origin + surface.mesh.position(vertex)
                blend.giveAll(rimShares(rims, at, position, paint))
            }
            blend.write(vertex, bones, weights)
        }
        first += patch.nu * patch.nv
    }
    return PartWeights(part.id, bones, weights)
}

/** The u a vertex of this patch weighs by: the patch's own for one that rides at one u, as a pad does, else its ring's. */
private fun Patch.uOf(local: Int): Float = boneU ?: ringUs[local / nv]

/** What [rims] give a parent's vertex [at], at [position] in body space, held so they never take more than the seam's share. */
private fun rimShares(rims: List<Rim>, at: SeamVertex, position: Vec3, paint: SeamPaint): Map<Int, Float> {
    val given = rims.associate { rim -> rim.bone to paint.share(rim.seam, at, rimShare((position - rim.anchor).length, rim.radius)) }
    val total = given.values.sum()
    return if (total <= SEAM_SHARE) given else given.mapValues { it.value * SEAM_SHARE / total }
}

/** A child's seam as its parent's vertices meet it. */
private class Rim(val seam: Seam, val bone: Int, val anchor: Vec3, val radius: Float) {
    companion object {
        fun of(child: RiggedPart, parent: RiggedPart, index: Map<BoneName, Int>): Rim? {
            if (child.joints.isEmpty) return null
            val anchor = child.placed.anchor ?: return null
            val bone = index[child.bone(0)] ?: return null
            return Rim(Seam(child.id), bone, anchor, seamRadius(child, parent))
        }
    }
}

/** How far along u a part's seam band reaches from its socket end: [SEAM_REACH] seam radii, held to half the way to its next joint. */
internal fun childBandOf(part: RiggedPart, parent: RiggedPart?): Float {
    val perU = part.rings.lengthPerU
    if (perU <= 0f) return 0f
    val next = if (part.joints.owning > 1) part.joints.specs[1].u else 1f
    return (SEAM_REACH * seamRadius(part, parent) / perU).coerceAtMost(next / 2f)
}

/** The weights a part gives its own bones alone, by u. */
internal class OwnWeights(private val part: RiggedPart, index: Map<BoneName, Int>) {
    private val joints = part.joints
    private val bones = IntArray(joints.specs.size) { index.getValue(part.bone(it)) }
    /** Half the band across each owning joint after the first, along u; 0 for the first. */
    val halfWidths = FloatArray(joints.owning) { if (it == 0) 0f else jointBand(it) }

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

    /** Half the band across the joint at [k] along u, held to half the way to the joints around it. */
    private fun jointBand(k: Int): Float {
        val perU = part.rings.lengthPerU
        if (perU <= 0f) return 0f
        val u = joints.specs[k].u
        val room = minOf(u - joints.specs[k - 1].u, joints.spanEnd(k) - u) / 2f
        return (JOINT_REACH * part.rings.radius(u) / perU).coerceAtMost(room)
    }
}
