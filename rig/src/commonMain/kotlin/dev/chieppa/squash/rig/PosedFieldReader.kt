package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.smoothMin
import kotlin.math.sqrt

/** How many points a read with its gradient takes: the point and one past it along each axis. */
private const val STENCIL = 4

private val OFFSETS = arrayOf(Vec3.ZERO, Vec3.X, Vec3.Y, Vec3.Z)

/**
 * Reads a posed field over a few of its members at a time, with its gradient by forward differences [h] long. Each member joins its
 * parent, where both are read, by its blend; a blend the rest field shares by the angle between the two members' gradients takes, read
 * for a corner at rest ([readRest]), the share the rest field gives there, as its surface was meshed, and posed, that share moved by as
 * much as [posedBlendShare] moves from the angle at rest to the angle read here from the same differences ([read]), so a join the pose
 * turns into a crease fills, one it lays against itself meets as a contact, and where the pose changes nothing the field is the rest
 * field the corner was meshed on. Every other pair meets by its plain union. One reader serves one thread; [reads] counts the solid reads
 * it has made.
 */
internal class PosedFieldReader(field: PosedField, private val h: Float) {
    private val members: List<JoinedMember> = field.members

    private val owns = Array(STENCIL) { FloatArray(members.size) { Float.NaN } }
    private val blends = FloatArray(members.size)
    private val values = FloatArray(STENCIL)

    val reads = LongArray(1)

    /** Each member's share of its blend at the corner being read, as [readRest] last read it or [restAt] set it. */
    private val restShares = FloatArray(members.size) { 1f }

    /** What [posedBlendShare] gave each member's join at rest at the corner being read. */
    private val restOperators = FloatArray(members.size) { 1f }

    private var atRest = false

    /** The gradient at the point last read with one. */
    var gradient: Vec3 = Vec3.ZERO
        private set

    /**
     * The rest field [rest] gives at [p], a corner's rest place, over the [count] members from [from] in [set], with its gradient, each
     * join's share the rest field's own there; each member's shares are written from [from] in [joins] for [restAt] to set again.
     */
    fun readRest(rest: PosedField.Posed, p: Vec3, set: IntArray, from: Int, count: Int, joins: RestJoins): Float {
        for (i in from until from + count) {
            restShares[set[i]] = 1f
            restOperators[set[i]] = 1f
        }
        atRest = true
        val value = read(rest, p, set, from, count, withGradient = true)
        atRest = false
        for (i in from until from + count) {
            joins.shares[i] = restShares[set[i]]
            joins.operators[i] = restOperators[set[i]]
        }
        return value
    }

    /** Sets the rest shares of the [count] members from [from] in [set] to those [joins] holds there, before the posed reads of one corner. */
    fun restAt(set: IntArray, from: Int, count: Int, joins: RestJoins) {
        for (i in from until from + count) {
            restShares[set[i]] = joins.shares[i]
            restOperators[set[i]] = joins.operators[i]
        }
    }

    /** The field [posed] gives at [p] over the [count] members from [from] in [set], and with [withGradient], its [gradient]. */
    fun read(posed: PosedField.Posed, p: Vec3, set: IntArray, from: Int, count: Int, withGradient: Boolean): Float {
        val points = if (withGradient) STENCIL else 1
        for (k in 0 until points) {
            val q = if (k == 0) p else p + OFFSETS[k] * h
            for (i in from until from + count) owns[k][set[i]] = posed.own(set[i], q, reads)
        }
        for (i in from until from + count) {
            val m = set[i]
            val member = members[m]
            val parent = member.parent
            blends[m] = when {
                parent < 0 || member.blend <= 0f || owns[0][parent].isNaN() -> 0f
                member.angles == null || !withGradient -> member.blend
                else -> member.blend * share(m, member, p, cosine(differences(m), differences(parent)))
            }
        }
        for (k in 0 until points) {
            var joined = Float.MAX_VALUE
            for (i in from until from + count) {
                val m = set[i]
                val d = owns[k][m]
                val t = if (blends[m] > 0f) smoothMin(owns[k][members[m].parent], d, blends[m]) else d
                if (t < joined) joined = t
            }
            values[k] = joined
        }
        if (withGradient) gradient = Vec3(values[1] - values[0], values[2] - values[0], values[3] - values[0]) * (1f / h)
        for (k in 0 until points) for (i in from until from + count) owns[k][set[i]] = Float.NaN
        return values[0]
    }

    /** The share of [member]'s blend at [p] for gradients meeting at [cos]: the rest field's at rest, moved by the operator's change posed. */
    private fun share(m: Int, member: JoinedMember, p: Vec3, cos: Float): Float {
        val operator = posedBlendShare(cos)
        if (!atRest) return (restShares[m] + operator - restOperators[m]).coerceIn(0f, 1f)
        val rest = member.angles?.at(p) ?: 1f
        restShares[m] = rest
        restOperators[m] = operator
        return rest
    }

    private fun differences(m: Int): Vec3 = Vec3(owns[1][m] - owns[0][m], owns[2][m] - owns[0][m], owns[3][m] - owns[0][m])

    private fun cosine(a: Vec3, b: Vec3): Float {
        val lengths = sqrt((a dot a) * (b dot b))
        return if (lengths > 1e-12f) (a dot b) / lengths else 1f
    }
}
