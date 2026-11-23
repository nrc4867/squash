package dev.chieppa.squash.rig

import dev.chieppa.squash.character.keys.KeyShape
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.MemberKey

/** What a member of a field reads as and how it joins: equal signs read the same field everywhere and join the same way. */
internal data class MemberSign(val key: MemberKey, val blend: Float, val parent: Int, val part: Int) {
    companion object {
        fun of(member: JoinedMember): MemberSign = MemberSign(member.key, member.blend, member.parent, member.part)
    }
}

/**
 * What a key's shape was made from besides the key itself: the plain field's [members] its vertices read, with their [signs], and the
 * map each bone its moved vertices hang on takes in the key's pose, by name ([bones], [maps]). A build that finds them all the same, and
 * the vertices kept with the same weights, takes the shape as it was.
 */
internal class KeyReads(val members: IntArray, val signs: List<MemberSign>, val bones: List<BoneName>, val maps: List<Affine>) {
    /** Whether [base] reads as these members did and [rig] poses these bones in [pose] as they were. */
    fun stillHolds(base: CharacterField, rig: Rig, pose: Pose): Boolean {
        val members = base.field.members
        for ((i, m) in this.members.withIndex()) if (m >= members.size || MemberSign.of(members[m]) != signs[i]) return false
        val frames = boneFrames(rig, pose)
        for ((i, name) in bones.withIndex()) {
            val b = rig.indexOf(name) ?: return false
            if (frames.transforms[b] != maps[i]) return false
        }
        return true
    }

    companion object {
        fun of(read: IntArray, base: CharacterField, rig: Rig, frames: BoneFrames, skin: SurfaceSkin, shape: KeyShape): KeyReads {
            val used = HashSet<Int>()
            for (v in shape.moved) for (slot in 0 until MAX_INFLUENCES) {
                val bone = skin.bones[v * MAX_INFLUENCES + slot]
                if (bone >= 0 && skin.weights[v * MAX_INFLUENCES + slot] > 0f) used += bone
            }
            val bones = used.sorted()
            return KeyReads(read, read.map { MemberSign.of(base.field.members[it]) }, bones.map { rig.bones[it].name }, bones.map { frames.transforms[it] })
        }
    }
}

/** For each vertex of [from]'s mesh, its place in [to]'s, or -1 where [to]'s mesh did not keep it; null where [to] does not follow [from]. */
internal fun keptVertices(from: CharacterSurface, to: CharacterSurface): IntArray? {
    if (from.mesh === to.mesh) return IntArray(from.mesh.vertexCount) { it }
    val change = to.mesh.change?.takeIf { it.previous === from.mesh } ?: return null
    val out = IntArray(from.mesh.vertexCount) { -1 }
    change.previousOf.forEachIndexed { v, was -> if (was >= 0) out[was] = v }
    return out
}

/** [shape] carried onto the vertices [kept] gives each of its own, those not kept left out; null where [whole] and any is not kept. */
internal fun KeyShape.carried(kept: IntArray, whole: Boolean): KeyShape? {
    val moved = ArrayList<Int>(this.moved.size)
    val from = ArrayList<Int>(this.moved.size)
    for ((i, v) in this.moved.withIndex()) {
        val to = kept.getOrElse(v) { -1 }
        if (to < 0) {
            if (whole) return null
            continue
        }
        moved += to
        from += i
    }
    if (from.size == this.moved.size) return KeyShape(moved.toIntArray(), offsets, turns)
    return KeyShape(
        moved.toIntArray(),
        FloatArray(from.size * 3) { offsets[from[it / 3] * 3 + it % 3] },
        FloatArray(from.size * 3) { turns[from[it / 3] * 3 + it % 3] },
    )
}

/** Whether every vertex [shape] moves hangs on the same bones by the same weights in [now], placed by [kept], as in [was] ([wasRig], [rig]). */
internal fun sameWeights(shape: KeyShape, kept: IntArray, was: SurfaceSkin, wasRig: Rig, now: SurfaceSkin, rig: Rig): Boolean {
    for (v in shape.moved) {
        val to = kept.getOrElse(v) { -1 }
        if (to < 0 || to >= now.vertexCount) return false
        for (slot in 0 until MAX_INFLUENCES) {
            val a = was.bones[v * MAX_INFLUENCES + slot]
            val b = now.bones[to * MAX_INFLUENCES + slot]
            if ((a < 0) != (b < 0)) return false
            if (a >= 0 && wasRig.bones[a].name != rig.bones[b].name) return false
            if (was.weights[v * MAX_INFLUENCES + slot] != now.weights[to * MAX_INFLUENCES + slot]) return false
        }
    }
    return true
}
