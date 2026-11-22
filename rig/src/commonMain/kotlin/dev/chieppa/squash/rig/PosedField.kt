package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.smoothMax

/**
 * One bound of a [MemberPiece]: across a joint, the plane through [point] with outward [normal]; the piece reaches [margin] past it.
 */
internal class PieceBound(val point: Vec3, val normal: Vec3, val margin: Float) {
    /** How far rest point [q] lies past the bound and its margin; negative within. */
    fun outside(q: Vec3): Float = ((q - point) dot normal) - margin
}

/**
 * One piece of a member's solid: the member read in rest space through the map its [bone] gives it, kept within its [bounds], each
 * reached [PieceBound.margin] past, so neighbouring pieces overlap there and their union at rest is the member's own field; each end is
 * rounded off by the least margin, so a piece posed alone ends in a round cap, while at rest a point lies a whole margin inside one of the
 * pieces, where its rounding changes nothing. A piece moves rigidly with its bone, or never moves for none (-1). [slot] is its place
 * among the field's maps.
 */
internal class MemberPiece(val bone: Int, val bounds: List<PieceBound>, val slot: Int) {
    val rounding: Float = bounds.minOfOrNull { it.margin } ?: 0f

    /** How far rest point [q] lies outside the piece's bounds, the most over them; negative inside all of them. */
    fun cut(q: Vec3): Float {
        var most = -Float.MAX_VALUE
        for (bound in bounds) {
            val d = bound.outside(q)
            if (d > most) most = d
        }
        return most
    }
}

/**
 * A one surface's field split into rigid pieces by the bones that carry its skin (Vaillant et al., "Implicit Skinning", 2013, whose
 * field is one piece per bone, cut from the skin by its weights): each member is cut into one piece for each bone of its own run with a
 * length, at the planes across the joints between them, each moving rigidly with its bone, so the posed field is the same members read
 * in each piece's space and joined as at rest.
 */
class PosedField(surface: SkinSurface, skin: SurfaceSkin, rig: Rig) {
    private val cell = surface.cell
    val members: List<JoinedMember> = surface.field.members
    internal val pieces: List<List<MemberPiece>>
    private val slots: Int
    private val slotBones: IntArray

    /** For each bone, the members one of whose pieces it moves. */
    val membersOf: List<IntArray>

    init {
        val bonesBySlot = ArrayList<Int>()
        val next = { bone: Int -> bonesBySlot += bone; bonesBySlot.size - 1 }
        val own = members.indices.map { surface.ownBones[it].toList() }
        pieces = members.indices.map { m -> piecesOf(own[m], m, rig, next) }
        slots = bonesBySlot.size
        slotBones = bonesBySlot.toIntArray()
        membersOf = List(rig.bones.size) { b -> members.indices.filter { m -> pieces[m].any { it.bone == b } }.toIntArray() }
    }

    private fun piecesOf(own: List<Int>, m: Int, rig: Rig, next: (Int) -> Int): List<MemberPiece> {
        if (own.isEmpty()) return listOf(MemberPiece(-1, emptyList(), next(-1)))
        val long = own.filter { rig.bones[it].length > 0f }.ifEmpty { listOf(own.first()) }
        val member = members[m]
        return long.map { b ->
            val bone = rig.bones[b]
            val bounds = ArrayList<PieceBound>()
            val parent = rig.parents[b]
            if (parent in long) bounds += PieceBound(bone.pivot, (rig.bones[parent].direction + bone.direction).normalized() * -1f, radiusAt(member, bone.pivot))
            for (child in long) if (rig.parents[child] == b) {
                val nextBone = rig.bones[child]
                bounds += PieceBound(nextBone.pivot, (bone.direction + nextBone.direction).normalized(), radiusAt(member, nextBone.pivot))
            }
            MemberPiece(b, bounds, next(b))
        }
    }

    /** How far the member's surface lies from [pivot], a joint inside it, at least a cell: how far past the joint its pieces reach. */
    private fun radiusAt(member: JoinedMember, pivot: Vec3): Float = maxOf(-member.own(pivot), cell)

    /** Each slot's map from rest to posed under [transforms]; null for one that stays. */
    internal fun maps(transforms: List<Affine>): Array<Affine?> = Array(slots) { slotBones[it].let { b -> if (b >= 0) transforms[b] else null } }

    /** The field posed by [frames]: each piece read through the inverse of its map. */
    fun posed(frames: BoneFrames): Posed = Posed(maps(frames.transforms).map { it?.inverse }.toTypedArray())

    /** The field at rest. */
    val rest: Posed by lazy { Posed(arrayOfNulls(slots)) }

    /** The field with each slot's map from posed body space back to rest in [inverses], null for one that stays. */
    inner class Posed(private val inverses: Array<Affine?>) {
        /** Member [m]'s own field at posed body point [p]: the least over its pieces, each read at [p] taken back by its map; [reads] counts the solid reads. */
        fun own(m: Int, p: Vec3, reads: LongArray): Float {
            val member = members[m]
            var best = Float.MAX_VALUE
            for (piece in pieces[m]) {
                val q = inverses[piece.slot]?.invoke(p) ?: p
                val cut = piece.cut(q)
                if (cut >= best) continue
                reads[0]++
                val d = smoothMax(member.own(q), cut, piece.rounding)
                if (d < best) best = d
            }
            return best
        }
    }
}
