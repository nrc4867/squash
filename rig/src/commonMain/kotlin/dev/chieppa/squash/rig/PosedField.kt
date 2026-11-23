package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.smoothMax
import dev.chieppa.squash.geom.field.smoothMin

/** The forward difference a piece's gradient is read over, as a share of the field's cell. */
private const val GRADIENT_SHARE = 0.25f

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

    /**
     * Each member's box posed by [frames], low corner then high, six floats a member: the box round its rest box's corners as each of
     * its pieces' maps takes them.
     */
    fun posedBoxes(frames: BoneFrames): FloatArray {
        val maps = maps(frames.transforms)
        val out = FloatArray(members.size * 6)
        for ((m, member) in members.withIndex()) {
            var lo = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            var hi = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (piece in pieces[m]) for (corner in 0 until 8) {
                val c = Vec3(
                    if (corner and 1 == 0) member.low.x else member.high.x,
                    if (corner and 2 == 0) member.low.y else member.high.y,
                    if (corner and 4 == 0) member.low.z else member.high.z,
                )
                val q = maps[piece.slot]?.invoke(c) ?: c
                lo = Vec3(minOf(lo.x, q.x), minOf(lo.y, q.y), minOf(lo.z, q.z))
                hi = Vec3(maxOf(hi.x, q.x), maxOf(hi.y, q.y), maxOf(hi.z, q.z))
            }
            out[m * 6] = lo.x; out[m * 6 + 1] = lo.y; out[m * 6 + 2] = lo.z
            out[m * 6 + 3] = hi.x; out[m * 6 + 4] = hi.y; out[m * 6 + 5] = hi.z
        }
        return out
    }

    /**
     * The field posed by [frames]: each piece read through the inverse of its map. With [bends], for each member the share of its
     * radius at its joints that fills the crease inside each joint the pose bends (none for a member with 0), its pieces meet there by
     * a smooth union that far ([Posed.own]).
     */
    fun posed(frames: BoneFrames, bends: FloatArray? = null): Posed = Posed(maps(frames.transforms).map { it?.inverse }.toTypedArray(), bends)

    /** The field at rest. */
    val rest: Posed by lazy { Posed(arrayOfNulls(slots)) }

    /** The field with each slot's map from posed body space back to rest in [inverses], null for one that stays. */
    inner class Posed(private val inverses: Array<Affine?>, private val bends: FloatArray? = null) {
        /**
         * Member [m]'s own field at posed body point [p]: the least over its pieces, each read at [p] taken back by its map; [reads]
         * counts the solid reads. Where the member fills its bent joints' creases, its two nearest pieces meet by a smooth union as wide
         * as its fill of their rounding, by the share [posedBlendShare] gives the angle between their gradients: a crease where they
         * meet square fills, while the outside of the bend, where they run alongside each other, stays as their union.
         */
        fun own(m: Int, p: Vec3, reads: LongArray): Float {
            val bend = bends?.getOrNull(m) ?: 0f
            if (bend > 0f && pieces[m].size > 1) return filled(m, p, bend, reads)
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

        private fun pieceAt(m: Int, piece: MemberPiece, p: Vec3, reads: LongArray): Float {
            val q = inverses[piece.slot]?.invoke(p) ?: p
            reads[0]++
            return smoothMax(members[m].own(q), piece.cut(q), piece.rounding)
        }

        private fun filled(m: Int, p: Vec3, bend: Float, reads: LongArray): Float {
            var first: MemberPiece? = null
            var second: MemberPiece? = null
            var a = Float.MAX_VALUE
            var b = Float.MAX_VALUE
            for (piece in pieces[m]) {
                val d = pieceAt(m, piece, p, reads)
                if (d < a) {
                    second = first; b = a; first = piece; a = d
                } else if (d < b) {
                    second = piece; b = d
                }
            }
            val one = first ?: return a
            val two = second ?: return a
            val k = bend * minOf(one.rounding, two.rounding)
            if (k <= 0f || b - a >= k) return a
            val h = cell * GRADIENT_SHARE
            fun gradient(piece: MemberPiece, at: Float): Vec3 = Vec3(
                pieceAt(m, piece, p + Vec3(h, 0f, 0f), reads) - at,
                pieceAt(m, piece, p + Vec3(0f, h, 0f), reads) - at,
                pieceAt(m, piece, p + Vec3(0f, 0f, h), reads) - at,
            ).unitOr(Vec3.ZERO)
            val share = posedBlendShare(gradient(one, a) dot gradient(two, b))
            return smoothMin(a, b, k * share)
        }
    }
}
