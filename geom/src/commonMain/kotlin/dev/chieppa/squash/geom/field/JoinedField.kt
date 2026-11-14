package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.sqrt

/**
 * One run's solid placed in body space: [solid] in its part's own space, which a body point reaches by taking off [origin] and, when
 * [mirrored], reflecting it across the body's plane. It joins member [parent], the run it grows from, by a smooth union of radius
 * [blend], 0 for a plain union, and every other member by a plain union; -1 for none. [part] numbers the part it is a run of. A member
 * [ease]d is its solid grown by that much all round, as a garment grows the body.
 */
class JoinedMember(
    val solid: Solid,
    val origin: Vec3,
    val mirrored: Boolean,
    val blend: Float,
    val parent: Int = -1,
    val part: Int = 0,
    val ease: Float = 0f,
) {
    /** [p], a body point, in the part's own space. */
    fun local(p: Vec3): Vec3 {
        val q = p - origin
        return if (mirrored) q.mirrored else q
    }

    /** Where the member reaches in body space. */
    val low: Vec3 = solid.low.let { lo -> if (mirrored) Vec3(lo.x, lo.y, -solid.high.z) + origin else lo + origin } - Vec3(ease, ease, ease)
    val high: Vec3 = solid.high.let { hi -> if (mirrored) Vec3(hi.x, hi.y, -solid.low.z) + origin else hi + origin } + Vec3(ease, ease, ease)

    /** The member's own field at body point [p]. */
    fun own(p: Vec3): Float = solid.distance(local(p)) - ease

    /** Writes the member's own field at body point [p] and where on its run it lies into [out], see [SweptSolid.place]. */
    fun place(p: Vec3, out: FloatArray) {
        val clipped = solid as? ClippedSolid
        val swept = (clipped?.solid ?: solid) as? SweptSolid
        if (swept == null) {
            out[PLACE_FIELD] = own(p)
            out[PLACE_U] = 0f
            out[PLACE_V] = 0f
        } else {
            val q = local(p)
            swept.place(q, out)
            if (clipped != null) out[PLACE_FIELD] = maxOf(out[PLACE_FIELD], clipped.outside(q))
            out[PLACE_FIELD] -= ease
        }
    }

    /** What the member reads as at any point: its solid, placed the same way. Equal keys read the same own field everywhere. */
    val key: MemberKey = MemberKey(solid, origin, mirrored, ease)

    /** This member grown by [by] more all round, joined as it is. */
    fun eased(by: Float, parent: Int = this.parent): JoinedMember = JoinedMember(solid, origin, mirrored, blend, parent, part, ease + by)

    /** How far body point [p] lies outside the member's box; 0 inside it. */
    fun boxDistance(p: Vec3): Float {
        val dx = maxOf(low.x - p.x, 0f, p.x - high.x)
        val dy = maxOf(low.y - p.y, 0f, p.y - high.y)
        val dz = maxOf(low.z - p.z, 0f, p.z - high.z)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/** A member's solid, place and ease, equal for the same solid object placed and grown the same way. */
class MemberKey(val solid: Solid, val origin: Vec3, val mirrored: Boolean, val ease: Float = 0f) {
    override fun equals(other: Any?): Boolean =
        other is MemberKey && solid === other.solid && origin == other.origin && mirrored == other.mirrored && ease == other.ease

    override fun hashCode(): Int = ((solid.hashCode() * 31 + origin.hashCode()) * 31 + mirrored.hashCode()) * 31 + ease.hashCode()
}

/**
 * Runs' solids joined into one body: the least, over every member, of the member's field smoothly joined to its parent's by the
 * member's blend, so a run blends into the run it grows from and meets every other run, a sibling beside it, by a plain union.
 * Negative inside. Each member's field is read once a point; [union] joins fields already read, so a caller holding them per member
 * joins them again by arithmetic alone.
 */
class JoinedField(val members: List<JoinedMember>) {
    /** For each member, the widest blend it joins by: its own to its parent, or a child's to it. */
    val reaches: FloatArray = FloatArray(members.size) { i -> members[i].blend }.also { reach ->
        members.forEach { member -> if (member.parent >= 0) reach[member.parent] = maxOf(reach[member.parent], member.blend) }
    }

    /** The term member [i] adds at a point whose members' own fields are [owns], NaN for one not read there, which counts as far. */
    fun term(i: Int, owns: FloatArray): Float {
        val d = owns[i]
        if (d.isNaN()) return Float.MAX_VALUE
        val member = members[i]
        val parent = member.parent
        if (parent < 0 || member.blend <= 0f) return d
        val p = owns[parent]
        return if (p.isNaN()) d else smoothMin(p, d, member.blend)
    }

    /** The joined field at a point whose members' own fields are [owns], NaN for those not read there. */
    fun union(owns: FloatArray): Float {
        var joined = Float.MAX_VALUE
        for (i in members.indices) {
            val t = term(i, owns)
            if (t < joined) joined = t
        }
        return joined
    }

    /**
     * The member whose term is least at a point whose own fields are [owns]: the one whose share of the surface there is the largest
     * or, across a smooth union, the child of the pair. -1 when none is read.
     */
    fun winner(owns: FloatArray): Int {
        var best = -1
        var joined = Float.MAX_VALUE
        for (i in members.indices) {
            val t = term(i, owns)
            if (t < joined) {
                joined = t
                best = i
            }
        }
        return best
    }

    /**
     * The child's share of the surface across [winner]'s smooth union with its parent at a point whose own fields are [owns]: the union's
     * own mixing weight, clamp(0.5 + 0.5 (d_parent − d_child) / blend, 0, 1); 1 when the winner joins nothing smoothly there.
     */
    fun childShare(winner: Int, owns: FloatArray): Float {
        val member = members[winner]
        val parent = member.parent
        if (parent < 0 || member.blend <= 0f) return 1f
        val p = owns[parent]
        if (p.isNaN()) return 1f
        return (0.5f + 0.5f * (p - owns[winner]) / member.blend).coerceIn(0f, 1f)
    }

    /** The joined field at body point [p], reading every member whose box lies near enough to matter. */
    fun value(p: Vec3): Float {
        val count = members.size
        if (count == 0) return Float.MAX_VALUE
        val owns = FloatArray(count) { Float.NaN }
        read(p, owns)
        return union(owns)
    }

    /** Reads into [owns] the own field at [p] of each member near enough to change the join there; the rest stay NaN. */
    fun read(p: Vec3, owns: FloatArray) {
        val count = members.size
        val boxes = FloatArray(count) { members[it].boxDistance(p) }
        var nearest = Float.MAX_VALUE
        for (i in 0 until count) if (boxes[i] == 0f) {
            owns[i] = members[i].own(p)
            nearest = minOf(nearest, owns[i])
        }
        if (nearest == Float.MAX_VALUE) {
            var closest = 0
            for (i in 1 until count) if (boxes[i] < boxes[closest]) closest = i
            owns[closest] = members[closest].own(p)
            nearest = owns[closest]
        }
        for (i in 0 until count) {
            if (!owns[i].isNaN() || boxes[i] >= maxOf(nearest, 0f) + reaches[i] + SLACK) continue
            owns[i] = members[i].own(p)
        }
    }

    private companion object {
        /** How much farther than the nearest a box may lie and still be read, for fields that are only near a distance. */
        const val SLACK = 1e-3f
    }
}
