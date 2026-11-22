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
 * parent, where both are read, by its blend; a blend the rest field shares by the angle between the two members' gradients takes the
 * share [posedBlendShare] gives the angle read here from the same differences, so a join the pose turns into a crease fills and one it
 * lays against itself meets as a contact. Every other pair meets by its plain union. A vertex is moved back to the level this same
 * reader gives it at rest, so where the pose changes nothing the share changes nothing either. One reader serves one thread; [reads]
 * counts the solid reads it has made.
 */
internal class PosedFieldReader(field: PosedField, private val h: Float) {
    private val members: List<JoinedMember> = field.members

    private val owns = Array(STENCIL) { FloatArray(members.size) { Float.NaN } }
    private val blends = FloatArray(members.size)
    private val values = FloatArray(STENCIL)

    val reads = LongArray(1)

    /** The gradient at the point last read with one. */
    var gradient: Vec3 = Vec3.ZERO
        private set

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
                else -> member.blend * posedBlendShare(cosine(differences(m), differences(parent)))
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

    private fun differences(m: Int): Vec3 = Vec3(owns[1][m] - owns[0][m], owns[2][m] - owns[0][m], owns[3][m] - owns[0][m])

    private fun cosine(a: Vec3, b: Vec3): Float {
        val lengths = sqrt((a dot a) * (b dot b))
        return if (lengths > 1e-12f) (a dot b) / lengths else 1f
    }
}
