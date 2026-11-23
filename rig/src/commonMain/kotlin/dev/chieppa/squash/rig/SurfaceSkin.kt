package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.IN_TURN
import dev.chieppa.squash.character.surface.JAW_JOINT
import dev.chieppa.squash.character.surface.JawSide
import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.geom.surface.HELD
import kotlin.math.floor

private const val TINY = 1e-12f

/** Vertices or quads one piece of weighting work takes. */
private const val SKIN_CHUNK = 2048

/**
 * The bones each vertex of a one surface's drawn mesh follows: [MAX_INFLUENCES] bones a vertex in [bones], -1 for none, with their
 * [weights], summing to one. A vertex's weights are each part's share of it times that part's own weights at the vertex's (u, v) on
 * it, so deep in a part it follows the part as the part's own mesh would, and across a join the shares blend parent into child. Only
 * the runs in the line of joins through the vertex's own run count, the runs it grows from, those growing from it and those growing from
 * the same run as it where that run holds the vertex too, so a vertex on one paw never follows the other paw however near that lies,
 * while two legs share their crotch, and two socks meeting between the ankles each follow their own leg.
 * A vertex of a hair part follows only that part and the part it sits on, never further up its line of joins, so hair over a nape
 * follows the head and not the neck or torso under it.
 * A quad of the mesh whose corners belong to runs no line of joins relates, two parts touching closer than the mesh's cell, is in
 * [contacts]: a pose that parts them leaves it out rather than stretch it.
 */
class SurfaceSkin(val bones: IntArray, val weights: FloatArray, val contacts: BooleanArray) {
    val vertexCount: Int get() = bones.size / MAX_INFLUENCES

    companion object {
        /** [surface]'s weights from [rig]'s parts' own, each batch of the work run by [runAll]. */
        suspend fun of(rig: Rig, surface: CharacterSurface, runAll: RunAll = IN_TURN): SurfaceSkin {
            val mesh = surface.mesh
            val corners = mesh.vertexCount
            val count = surface.render.vertexCount
            val bones = IntArray(count * MAX_INFLUENCES) { -1 }
            val weights = FloatArray(count * MAX_INFLUENCES)
            val lineage = Lineage(surface)
            runAll(chunked(corners, SKIN_CHUNK) { from, to -> cornerWeights(rig, surface, lineage, from, to, bones, weights) })
            val indices = surface.render.indices
            val contacts = BooleanArray(mesh.quadCount)
            runAll(
                chunked(count - corners, SKIN_CHUNK) { from, to -> middleWeights(indices, corners, from, to, bones, weights) } +
                    chunked(contacts.size, SKIN_CHUNK) { from, to -> for (quad in from until to) contacts[quad] = touching(mesh, lineage, quad) },
            )
            return SurfaceSkin(bones, weights, contacts)
        }

        private fun cornerWeights(rig: Rig, surface: CharacterSurface, lineage: Lineage, from: Int, to: Int, bones: IntArray, weights: FloatArray) {
            val mesh = surface.mesh
            val readers = HashMap<Int, RunWeights?>()
            val blend = WeightBlend()
            for (v in from until to) {
                blend.clear()
                val own = mesh.winners[v]
                val atParent = own >= 0 && (0 until HELD).any { k -> mesh.held[v * HELD + k].let { it >= 0 && lineage.parentOf(own, it) } }
                for (k in 0 until HELD) {
                    val member = mesh.held[v * HELD + k]
                    if (member < 0) continue
                    if (own >= 0 && !lineage.weighs(own, member)) continue
                    if (own >= 0 && !atParent && lineage.siblings(own, member)) continue
                    val share = mesh.heldShares[v * HELD + k]
                    val side = surface.field.runs[member].jawSide
                    val jaw = if (side == null) -1 else rig.indexOf(BoneName(surface.field.runs[member].partId, JAW_JOINT)) ?: -1
                    val head = if (jaw >= 0) rig.parents[jaw] else -1
                    if (side == JawSide.LINING && jaw >= 0 && head >= 0) {
                        val floor = mesh.heldV[v * HELD + k].coerceIn(0f, 1f)
                        blend.add(jaw, share * floor)
                        blend.add(head, share * (1f - floor))
                        continue
                    }
                    val onJaw = if (jaw >= 0 && head >= 0) surface.field.openJawOf(surface.field.runs[member].partId)?.jawShare(mesh.position(v)) ?: 0f else 0f
                    if (onJaw > 0f) blend.add(jaw, share * onJaw)
                    if (onJaw >= 1f) continue
                    val reader = readers.getOrPut(member) { RunWeights.of(rig, surface, member) } ?: continue
                    reader.addTo(blend, mesh.heldU[v * HELD + k], mesh.heldV[v * HELD + k], share * (1f - onJaw), if (head >= 0) jaw else -1, head)
                }
                blend.write(bones, weights, v * MAX_INFLUENCES)
            }
        }

        /** Each quad middle [from] until [to]'s weights: the mean of its four corners'. */
        private fun middleWeights(indices: IntArray, corners: Int, from: Int, to: Int, bones: IntArray, weights: FloatArray) {
            val blend = WeightBlend()
            for (quad in from until to) {
                blend.clear()
                for (k in 0 until 4) {
                    val corner = indices[(quad * 4 + k) * 3]
                    for (slot in 0 until MAX_INFLUENCES) {
                        val bone = bones[corner * MAX_INFLUENCES + slot]
                        if (bone >= 0) blend.add(bone, weights[corner * MAX_INFLUENCES + slot] / 4f)
                    }
                }
                blend.write(bones, weights, (corners + quad) * MAX_INFLUENCES)
            }
        }

        /** Whether [quad]'s corners belong to runs no line of joins relates. */
        private fun touching(mesh: SurfaceMesh, lineage: Lineage, quad: Int): Boolean {
            val t = mesh.triangles
            val w = mesh.winners
            val c0 = w[t[quad * 6]]
            val c1 = w[t[quad * 6 + 1]]
            val c2 = w[t[quad * 6 + 4]]
            val c3 = w[t[quad * 6 + 5]]
            if (c0 < 0 || c1 < 0 || c2 < 0 || c3 < 0) {
                val all = intArrayOf(c0, c1, c2, c3)
                return all.any { a -> all.any { b -> a >= 0 && b >= 0 && !lineage.related(a, b) } }
            }
            if (c0 == c1 && c1 == c2 && c2 == c3) return false
            val all = intArrayOf(c0, c1, c2, c3)
            return all.any { a -> all.any { b -> !lineage.related(a, b) } }
        }
    }
}

/**
 * Which runs of a surface's field a line of joins relates, each standing for its part's first run: the same part, one growing from the
 * other, however far up, or both from the same run.
 */
private class Lineage(surface: CharacterSurface) {
    private val members = surface.field.field.members

    /** Each member's part's first member, which stands for all of the part's runs. */
    private val first = IntArray(members.size) { m -> members.indexOfFirst { it.part == members[m].part } }

    /** Whether each member is a run of a hair part. */
    private val hair = BooleanArray(members.size) { m -> surface.field.runs[m].part.role == Role.HAIR }

    /**
     * Whether a vertex won by [own] takes weights from [member]: as [related], except that a hair part and any other part relate only
     * where the other is the part the hair sits on.
     */
    fun weighs(own: Int, member: Int): Boolean {
        val x = first[own]
        val y = first[member]
        if (x == y) return true
        if (hair[x]) return members[x].parent >= 0 && first[members[x].parent] == y
        if (hair[y]) return members[y].parent >= 0 && first[members[y].parent] == x
        return related(own, member)
    }

    fun related(a: Int, b: Int): Boolean {
        val x = first[a]
        val y = first[b]
        return x == y || descends(x, y) || descends(y, x) || (members[x].parent >= 0 && members[x].parent == members[y].parent)
    }

    /** Whether [a] and [b] are runs of two parts that grow from the same run, neither from the other: two legs. */
    fun siblings(a: Int, b: Int): Boolean {
        val x = first[a]
        val y = first[b]
        return x != y && !descends(x, y) && !descends(y, x) && members[x].parent >= 0 && members[x].parent == members[y].parent
    }

    /** Whether [member] is a run of the part [child]'s part grows from. */
    fun parentOf(child: Int, member: Int): Boolean {
        val parent = members[first[child]].parent
        return parent >= 0 && first[member] == first[parent]
    }

    private fun descends(child: Int, ancestor: Int): Boolean {
        var at = child
        while (at >= 0) {
            if (at == ancestor) return true
            at = members[at].parent
        }
        return false
    }
}

/** Bones and their weights gathered for one vertex, kept to the [MAX_INFLUENCES] heaviest and made to sum to one. */
private class WeightBlend {
    private val bones = IntArray(32)
    private val weights = FloatArray(32)
    private var count = 0

    fun clear() {
        count = 0
    }

    fun add(bone: Int, weight: Float) {
        if (weight <= 0f) return
        for (i in 0 until count) if (bones[i] == bone) {
            weights[i] += weight
            return
        }
        if (count == bones.size) return
        bones[count] = bone
        weights[count] = weight
        count++
    }

    private val order = IntArray(MAX_INFLUENCES)
    private val taken = BooleanArray(bones.size)

    /** Writes the heaviest [MAX_INFLUENCES], heaviest first and the first added first among equals, made to sum to one. */
    fun write(outBones: IntArray, outWeights: FloatArray, at: Int) {
        val kept = minOf(count, MAX_INFLUENCES)
        taken.fill(false, 0, count)
        var total = 0.0
        for (slot in 0 until kept) {
            var best = -1
            for (i in 0 until count) if (!taken[i] && (best < 0 || weights[i] > weights[best])) best = i
            taken[best] = true
            order[slot] = best
            total += weights[best].toDouble()
        }
        val sum = total.toFloat()
        for (slot in 0 until MAX_INFLUENCES) {
            val i = if (slot < kept) order[slot] else -1
            outBones[at + slot] = if (i < 0 || sum <= TINY) -1 else bones[i]
            outWeights[at + slot] = if (i < 0 || sum <= TINY) 0f else weights[i] / sum
        }
    }
}

/** A run's part's own weights read at any (u, v) on the run, eased between the four grid vertices round it. */
private class RunWeights(private val part: RigPart, private val first: Int, private val run: dev.chieppa.squash.geom.Patch) {
    /** Adds the weights at (u, v) times [share] to [blend], any of bone [from] given to bone [to] in its place. */
    fun addTo(blend: WeightBlend, u: Float, v: Float, share: Float, from: Int = -1, to: Int = -1) {
        val us = run.ringUs
        var ring = 0
        while (ring < run.nu - 2 && us[ring + 1] < u) ring++
        val span = us[ring + 1] - us[ring]
        val t = (if (span > TINY) (u - us[ring]) / span else 0f).coerceIn(0f, 1f)
        val around = v * run.segments
        val column = floor(around).toInt()
        val s = around - column
        val c0 = column.mod(run.nv)
        val c1 = if (run.closedV) (column + 1).mod(run.nv) else (column + 1).coerceIn(0, run.nv - 1)
        add(blend, ring, c0.coerceIn(0, run.nv - 1), share * (1f - t) * (1f - s), from, to)
        add(blend, ring, c1, share * (1f - t) * s, from, to)
        add(blend, ring + 1, c0.coerceIn(0, run.nv - 1), share * t * (1f - s), from, to)
        add(blend, ring + 1, c1, share * t * s, from, to)
    }

    private fun add(blend: WeightBlend, ring: Int, column: Int, amount: Float, from: Int, to: Int) {
        if (amount <= 0f) return
        val vertex = first + run.index(ring, column)
        if (vertex >= part.vertexCount) return
        for (slot in 0 until MAX_INFLUENCES) {
            val bone = part.weights.bones[vertex * MAX_INFLUENCES + slot]
            if (bone >= 0) blend.add(if (bone == from) to else bone, part.weights.weights[vertex * MAX_INFLUENCES + slot] * amount)
        }
    }

    companion object {
        fun of(rig: Rig, surface: CharacterSurface, member: Int): RunWeights? {
            val run = surface.field.runs[member]
            val part = rig.part(run.partId) ?: return null
            val first = run.part.surface.patches.take(run.patchIndex).sumOf { it.nu * it.nv }
            return RunWeights(part, first, run.patch)
        }
    }
}
