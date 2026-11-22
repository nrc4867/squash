package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.smoothMax

/** The most a weight may fall short of a vertex's heaviest and still count as its heaviest, so a band's even split goes to the deeper bone. */
private const val TIE = 1e-3f

/** The fewest of a member's vertices a bone of another part must carry most for the member to have a piece that bone moves. */
private const val FEWEST_CARRIED = 4

/** The most bones a carried piece's map blends. */
private const val MOST_CARRYING = 2

/** How far past a carried piece's ball, as a share of its radius, it and the member's own pieces each reach. */
private const val CARRIED_MARGIN = 0.5f

/**
 * One bound of a [MemberPiece]: across a joint, the plane through [point] with outward [normal]; round what another part's bone carries,
 * the ball about [point] [radius] wide, within [nearReach] of the member [near] when there is one, the piece inside it when [inside] or
 * outside it otherwise. The piece reaches [margin] past it.
 */
internal class PieceBound(val point: Vec3, val normal: Vec3?, val radius: Float, val inside: Boolean, val margin: Float, val near: JoinedMember? = null, val nearReach: Float = 0f) {
    /** How far rest point [q] lies past the bound and its margin; negative within. */
    fun outside(q: Vec3): Float {
        val n = normal
        if (n != null) return ((q - point) dot n) - margin
        val ball = (q - point).length - radius
        val within = if (near == null || ball > margin) ball else maxOf(ball, near.own(q) - nearReach)
        return (if (inside) within else -within) - margin
    }
}

/**
 * One piece of a member's solid: the member read in rest space through the map its [bones] give it, kept within its [bounds], each
 * reached [PieceBound.margin] past, so neighbouring pieces overlap there and their union at rest is the member's own field; each end is
 * rounded off by the least margin, so a piece posed alone ends in a round cap, while at rest a point lies a whole margin inside one of the
 * pieces, where its rounding changes nothing. A piece of one bone moves rigidly with it; a piece of several moves by their maps blended
 * by [shares], as linear blend moves the skin they share; one of none never moves. [slot] is its place among the field's pieces.
 */
internal class MemberPiece(val bones: IntArray, val shares: FloatArray, val bounds: List<PieceBound>, val slot: Int) {
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

    /** The map [transforms] give the piece, from rest to posed; null for a piece that never moves. */
    fun map(transforms: List<Affine>): Affine? = when (bones.size) {
        0 -> null
        1 -> transforms[bones[0]]
        else -> {
            var linear = Mat3(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)
            var offset = Vec3.ZERO
            for (i in bones.indices) {
                val t = transforms[bones[i]]
                linear = Mat3(linear.x + t.linear.x * shares[i], linear.y + t.linear.y * shares[i], linear.z + t.linear.z * shares[i])
                offset += t.offset * shares[i]
            }
            Affine(linear, offset)
        }
    }
}

/**
 * A one surface's field split into rigid pieces by the bones that carry its skin (Vaillant et al., "Implicit Skinning", 2013, whose
 * field is one piece per bone, cut from the skin by its weights): each member is cut into one piece for each bone of its own run with a
 * length, at the planes across the joints between them, and one more for each bone of another part that carries the most of a few of its
 * vertices, such as an arm's first bone round its socket on the torso: the member within a ball round those vertices, which the
 * member's own pieces leave out, moved by the bones those vertices follow blended by their mean weights there, as linear blend moves
 * them. Each piece of the member's own run moves rigidly with its bone, so the posed field is the same members read in each piece's
 * space and joined as at rest.
 */
class PosedField(surface: SkinSurface, skin: SurfaceSkin, rig: Rig) {
    private val cell = surface.cell
    val members: List<JoinedMember> = surface.field.members
    internal val pieces: List<List<MemberPiece>>
    private val slots: Int

    /** For each bone, the members one of whose pieces it moves. */
    val membersOf: List<IntArray>

    init {
        val carried = carriedBy(surface, skin, rig)
        var slot = 0
        pieces = members.indices.map { m -> piecesOf(surface.ownBones[m].toList(), m, rig, carried[m]) { slot++ } }
        slots = slot
        membersOf = List(rig.bones.size) { b -> members.indices.filter { m -> pieces[m].any { b in it.bones } }.toIntArray() }
    }

    /**
     * For each member, the bones of other parts that carry the most of at least [FEWEST_CARRIED] of the surface's vertices it holds most,
     * each with the ball round those vertices, about the seam's anchor where the bone is the first of a part whose socket one of the
     * member's own bones carries and about the vertices' middle otherwise, out to the farthest of them; and those vertices' mean weights.
     */
    private fun carriedBy(surface: SkinSurface, skin: SurfaceSkin, rig: Rig): List<List<Carried>> {
        val depth = IntArray(rig.bones.size)
        for (b in rig.bones.indices) depth[b] = if (rig.parents[b] < 0) 0 else depth[rig.parents[b]] + 1
        val own = members.indices.map { surface.ownBones[it].toHashSet() }
        val byMember = members.indices.map { HashMap<Int, MutableList<Int>>() }
        for (v in 0 until surface.vertexCount) {
            val m = surface.winners[v]
            if (m < 0) continue
            val bone = heaviest(skin, v, depth)
            if (bone < 0 || bone in own[m]) continue
            byMember[m].getOrPut(bone) { ArrayList() } += v
        }
        return members.indices.map { m ->
            byMember[m].entries.filter { it.value.size >= FEWEST_CARRIED }.sortedBy { it.key }.map { (bone, corners) ->
                val seam = rig.seams.firstOrNull { it.first == bone && it.carrier in own[m] }
                val centre = seam?.anchor ?: (corners.fold(Vec3.ZERO) { sum, v -> sum + surface.positions.vec(v * 3) } * (1f / corners.size))
                val radius = corners.maxOf { (surface.positions.vec(it * 3) - centre).length }
                val mean = HashMap<Int, Float>()
                for (v in corners) for (slot in 0 until MAX_INFLUENCES) {
                    val b = skin.bones[v * MAX_INFLUENCES + slot]
                    if (b >= 0) mean[b] = (mean[b] ?: 0f) + skin.weights[v * MAX_INFLUENCES + slot]
                }
                val kept = mean.entries.sortedByDescending { it.value }.take(MOST_CARRYING)
                val total = kept.sumOf { it.value.toDouble() }.toFloat()
                val child = members.indices.filter { bone in surface.ownBones[it] }.minByOrNull { members[it].own(centre) }
                Carried(
                    kept.map { it.key }.toIntArray(),
                    FloatArray(kept.size) { kept[it].value / total },
                    PieceBound(centre, null, radius, inside = true, margin = radius * CARRIED_MARGIN, near = child?.let { members[it] }, nearReach = child?.let { maxOf(members[it].blend, cell) } ?: 0f),
                )
            }
        }
    }

    /** A ball of a member's skin that bones of another part carry, [bones] with their mean [shares] there. */
    private class Carried(val bones: IntArray, val shares: FloatArray, val ball: PieceBound)

    private fun piecesOf(own: List<Int>, m: Int, rig: Rig, carried: List<Carried>, next: () -> Int): List<MemberPiece> {
        val leftOut = carried.map { PieceBound(it.ball.point, null, it.ball.radius, inside = false, margin = it.ball.margin, near = it.ball.near, nearReach = it.ball.nearReach) }
        val caps = { carried.map { MemberPiece(it.bones, it.shares, listOf(it.ball), next()) } }
        if (own.isEmpty()) return listOf(MemberPiece(IntArray(0), FloatArray(0), leftOut, next())) + caps()
        val long = own.filter { rig.bones[it].length > 0f }.ifEmpty { listOf(own.first()) }
        val member = members[m]
        return long.map { b ->
            val bone = rig.bones[b]
            val bounds = ArrayList(leftOut)
            val parent = rig.parents[b]
            if (parent in long) bounds += PieceBound(bone.pivot, (rig.bones[parent].direction + bone.direction).normalized() * -1f, 0f, false, radiusAt(member, bone.pivot))
            for (child in long) if (rig.parents[child] == b) {
                val nextBone = rig.bones[child]
                bounds += PieceBound(nextBone.pivot, (bone.direction + nextBone.direction).normalized(), 0f, false, radiusAt(member, nextBone.pivot))
            }
            MemberPiece(intArrayOf(b), floatArrayOf(1f), bounds, next())
        } + caps()
    }

    /** How far the member's surface lies from [pivot], a joint inside it, at least a cell: how far past the joint its pieces reach. */
    private fun radiusAt(member: JoinedMember, pivot: Vec3): Float = maxOf(-member.own(pivot), cell)

    /** Each piece's map from rest to posed under [transforms], by its slot; null for one that stays. */
    internal fun maps(transforms: List<Affine>): Array<Affine?> {
        val maps = arrayOfNulls<Affine>(slots)
        for (list in pieces) for (piece in list) maps[piece.slot] = piece.map(transforms)
        return maps
    }

    /** The field posed by [frames]: each piece read through the inverse of its map. */
    fun posed(frames: BoneFrames): Posed = Posed(maps(frames.transforms).map { it?.inverse }.toTypedArray())

    /** The field at rest. */
    val rest: Posed by lazy { Posed(arrayOfNulls(slots)) }

    /** The field with each piece's map from posed body space back to rest in [inverses] by its slot, null for one that stays. */
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

/** The bone carrying the most of vertex [v] of [skin], the deeper of two within [TIE]; -1 for none. */
private fun heaviest(skin: SurfaceSkin, v: Int, depth: IntArray): Int {
    var best = -1
    var weight = 0f
    for (slot in 0 until MAX_INFLUENCES) {
        val bone = skin.bones[v * MAX_INFLUENCES + slot]
        if (bone < 0) continue
        val w = skin.weights[v * MAX_INFLUENCES + slot]
        if (best < 0 || w > weight + TIE || (w > weight - TIE && depth[bone] > depth[best])) {
            if (w > weight) weight = w
            best = bone
        }
    }
    return best
}
