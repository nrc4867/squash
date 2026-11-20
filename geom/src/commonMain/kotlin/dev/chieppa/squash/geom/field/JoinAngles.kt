package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** Above this cosine of the angle between two members' gradients their surfaces run alongside each other, and their join does not blend. */
const val ALONGSIDE_COS = 0.985f

/** At or below this cosine two members' surfaces meet at a crease, and their join blends by its whole blend. */
const val CREASE_COS = 0.85f

/** How many lattice steps a join's blend spans. */
private const val STEPS_PER_BLEND = 3f

/** The most lattice points along any side of a join's box. */
private const val MOST_STEPS = 48

/** How far, in lattice steps, past where a join's blend can act its lattice is still read, so a cell reading it is read at all corners. */
private const val MARGIN_STEPS = 4f

/** The gradient's central differences, as a share of a lattice step. */
private const val GRADIENT_SHARE = 0.25f

private const val TINY = 1e-12f

/**
 * The share of its blend a smooth union of two members takes where their gradients meet with cosine [cos]: none where the surfaces run
 * alongside each other or lie on each other, so the union does not swell them, and all of it where they meet at a crease, so the
 * crease fills. Gourmel et al., "A gradient-based implicit blend": the operator is chosen at each point by the angle between the
 * fields' gradients, a plain union where they agree.
 */
fun angleShare(cos: Float): Float = smoothstep(ALONGSIDE_COS, CREASE_COS, cos)

/**
 * The share of its blend a child's smooth union with its parent takes at each point of a box round where their blend can act, sampled on
 * a lattice of [step] from [low] with [nx] by [ny] by [nz] points in [shares] and read between them trilinearly; 1 outside the box.
 */
class JoinAngles internal constructor(
    val low: Vec3,
    val step: Float,
    val nx: Int,
    val ny: Int,
    val nz: Int,
    private val shares: FloatArray,
) {
    /** The share of the blend at body point [p]. */
    fun at(p: Vec3): Float {
        val fx = (p.x - low.x) / step
        val fy = (p.y - low.y) / step
        val fz = (p.z - low.z) / step
        if (fx < 0f || fy < 0f || fz < 0f || fx > nx - 1 || fy > ny - 1 || fz > nz - 1) return 1f
        val i = floor(fx).toInt().coerceAtMost(nx - 2)
        val j = floor(fy).toInt().coerceAtMost(ny - 2)
        val k = floor(fz).toInt().coerceAtMost(nz - 2)
        val tx = fx - i
        val ty = fy - j
        val tz = fz - k
        fun s(a: Int, b: Int, c: Int): Float = shares[a + nx * (b + ny * c)]
        val x00 = s(i, j, k) + (s(i + 1, j, k) - s(i, j, k)) * tx
        val x10 = s(i, j + 1, k) + (s(i + 1, j + 1, k) - s(i, j + 1, k)) * tx
        val x01 = s(i, j, k + 1) + (s(i + 1, j, k + 1) - s(i, j, k + 1)) * tx
        val x11 = s(i, j + 1, k + 1) + (s(i + 1, j + 1, k + 1) - s(i, j + 1, k + 1)) * tx
        val y0 = x00 + (x10 - x00) * ty
        val y1 = x01 + (x11 - x01) * ty
        return y0 + (y1 - y0) * tz
    }

    companion object {
        /**
         * The shares of [blend] [child]'s smooth union with [parent] takes round where it can act, each point's from the angle between the
         * two members' own gradients there ([angleShare]); null where their boxes, grown by the blend, do not meet. Points where the
         * union cannot change the surface keep the whole blend.
         */
        fun of(child: JoinedMember, parent: JoinedMember, blend: Float): JoinAngles? {
            if (blend <= 0f) return null
            val low = Vec3(max(child.low.x, parent.low.x) - blend, max(child.low.y, parent.low.y) - blend, max(child.low.z, parent.low.z) - blend)
            val high = Vec3(minOf(child.high.x, parent.high.x) + blend, minOf(child.high.y, parent.high.y) + blend, minOf(child.high.z, parent.high.z) + blend)
            val size = high - low
            if (size.x <= 0f || size.y <= 0f || size.z <= 0f) return null
            val step = max(blend / STEPS_PER_BLEND, maxOf(size.x, size.y, size.z) / (MOST_STEPS - 1))
            val nx = ceil(size.x / step).toInt() + 1
            val ny = ceil(size.y / step).toInt() + 1
            val nz = ceil(size.z / step).toInt() + 1
            val shares = FloatArray(nx * ny * nz) { 1f }
            val near = blend + MARGIN_STEPS * step
            val h = step * GRADIENT_SHARE
            for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
                val p = Vec3(low.x + i * step, low.y + j * step, low.z + k * step)
                val a = child.own(p)
                val b = parent.own(p)
                if (kotlin.math.abs(a - b) > near || minOf(a, b) > blend + near || maxOf(a, b) < -(blend + near)) continue
                shares[i + nx * (j + ny * k)] = angleShare(cosine(gradient(child, p, h), gradient(parent, p, h)))
            }
            return JoinAngles(low, step, nx, ny, nz, shares)
        }

        private fun gradient(member: JoinedMember, p: Vec3, h: Float): Vec3 = Vec3(
            member.own(Vec3(p.x + h, p.y, p.z)) - member.own(Vec3(p.x - h, p.y, p.z)),
            member.own(Vec3(p.x, p.y + h, p.z)) - member.own(Vec3(p.x, p.y - h, p.z)),
            member.own(Vec3(p.x, p.y, p.z + h)) - member.own(Vec3(p.x, p.y, p.z - h)),
        )

        /** The cosine of the angle between [a] and [b]; 1, the angle 0, where either vanishes. */
        private fun cosine(a: Vec3, b: Vec3): Float {
            val lengths = sqrt((a dot a) * (b dot b))
            return if (lengths > TINY) (a dot b) / lengths else 1f
        }
    }
}
