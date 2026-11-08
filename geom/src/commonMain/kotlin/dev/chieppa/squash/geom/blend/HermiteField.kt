package dev.chieppa.squash.geom.blend

import dev.chieppa.squash.geom.Vec3
import kotlin.math.sqrt

/** Values per centre: one weight on the kernel and three on its gradient. */
private const val PER_CENTRE = 4

/** The linear part's unknowns: a constant and a gradient. */
private const val LINEAR = 4

/**
 * A smooth field that is zero on a surface and grows outward through it, interpolating points on the surface and their outward
 * normals: a Hermite radial basis function on the kernel r³ with a linear part. Near the surface it is close to the signed
 * distance, negative inside. See docs/work/spike-implicit-blend.md.
 */
class HermiteField private constructor(
    private val centres: DoubleArray,
    private val weights: DoubleArray,
    private val linear: DoubleArray,
    /** The smallest box holding the centres, low corner then high. */
    val low: Vec3,
    val high: Vec3,
) {
    /** Whether [p] lies within [margin] of the centres' box. */
    fun near(p: Vec3, margin: Float): Boolean =
        p.x >= low.x - margin && p.y >= low.y - margin && p.z >= low.z - margin &&
            p.x <= high.x + margin && p.y <= high.y + margin && p.z <= high.z + margin

    /** The field at [p], with its gradient written into [gradient], three values. */
    fun at(p: Vec3, gradient: DoubleArray): Double {
        val px = p.x.toDouble()
        val py = p.y.toDouble()
        val pz = p.z.toDouble()
        var value = linear[0] + linear[1] * px + linear[2] * py + linear[3] * pz
        var gx = linear[1]
        var gy = linear[2]
        var gz = linear[3]
        val count = centres.size / 3
        for (k in 0 until count) {
            val wx = px - centres[k * 3]
            val wy = py - centres[k * 3 + 1]
            val wz = pz - centres[k * 3 + 2]
            val r = sqrt(wx * wx + wy * wy + wz * wz)
            if (r == 0.0) continue
            val alpha = weights[k * PER_CENTRE]
            val bx = weights[k * PER_CENTRE + 1]
            val by = weights[k * PER_CENTRE + 2]
            val bz = weights[k * PER_CENTRE + 3]
            val wb = wx * bx + wy * by + wz * bz
            value += alpha * r * r * r + 3.0 * r * wb
            gx += 3.0 * (alpha * r * wx + r * bx + wx * wb / r)
            gy += 3.0 * (alpha * r * wy + r * by + wy * wb / r)
            gz += 3.0 * (alpha * r * wz + r * bz + wz * wb / r)
        }
        gradient[0] = gx
        gradient[1] = gy
        gradient[2] = gz
        return value
    }

    companion object {
        /** The field through [points] with outward unit [normals], or null when they give no single solution. */
        fun fit(points: List<Vec3>, normals: List<Vec3>): HermiteField? {
            require(points.size == normals.size) { "${points.size} points for ${normals.size} normals" }
            val m = points.size
            if (m < 2) return null
            val n = PER_CENTRE * m + LINEAR
            val a = DoubleArray(n * n)
            val b = DoubleArray(n)
            fun set(row: Int, column: Int, value: Double) {
                a[row * n + column] = value
            }
            val g = DoubleArray(3)
            val h = DoubleArray(9)
            for (j in 0 until m) {
                val vj = points[j]
                val valueRow = PER_CENTRE * j
                for (k in 0 until m) {
                    val vk = points[k]
                    val wx = (vj.x - vk.x).toDouble()
                    val wy = (vj.y - vk.y).toDouble()
                    val wz = (vj.z - vk.z).toDouble()
                    kernel(wx, wy, wz, g, h)
                    val r = sqrt(wx * wx + wy * wy + wz * wz)
                    val column = PER_CENTRE * k
                    set(valueRow, column, r * r * r)
                    for (i in 0 until 3) {
                        set(valueRow, column + 1 + i, g[i])
                        set(valueRow + 1 + i, column, g[i])
                        for (l in 0 until 3) set(valueRow + 1 + i, column + 1 + l, h[i * 3 + l])
                    }
                }
                set(valueRow, PER_CENTRE * m, 1.0)
                set(valueRow, PER_CENTRE * m + 1, vj.x.toDouble())
                set(valueRow, PER_CENTRE * m + 2, vj.y.toDouble())
                set(valueRow, PER_CENTRE * m + 3, vj.z.toDouble())
                for (i in 0 until 3) set(valueRow + 1 + i, PER_CENTRE * m + 1 + i, 1.0)
                val normal = normals[j]
                b[valueRow + 1] = normal.x.toDouble()
                b[valueRow + 2] = normal.y.toDouble()
                b[valueRow + 3] = normal.z.toDouble()
            }
            // The weights take nothing a linear field could: their sum is zero, as is each moment less the gradient weights.
            val sides = PER_CENTRE * m
            for (k in 0 until m) {
                val vk = points[k]
                set(sides, PER_CENTRE * k, 1.0)
                set(sides + 1, PER_CENTRE * k, vk.x.toDouble())
                set(sides + 2, PER_CENTRE * k, vk.y.toDouble())
                set(sides + 3, PER_CENTRE * k, vk.z.toDouble())
                for (i in 0 until 3) set(sides + 1 + i, PER_CENTRE * k + 1 + i, -1.0)
            }
            val x = solveDense(a, b, n) ?: return null
            if (x.any { !it.isFinite() }) return null
            val centres = DoubleArray(m * 3) { points[it / 3].component(it % 3).toDouble() }
            val low = Vec3(points.minOf { it.x }, points.minOf { it.y }, points.minOf { it.z })
            val high = Vec3(points.maxOf { it.x }, points.maxOf { it.y }, points.maxOf { it.z })
            return HermiteField(centres, x.copyOf(PER_CENTRE * m), x.copyOfRange(PER_CENTRE * m, n), low, high)
        }

        /** The kernel r³'s gradient at offset w into [g] and its Hessian into [h], both zero at w = 0. */
        private fun kernel(wx: Double, wy: Double, wz: Double, g: DoubleArray, h: DoubleArray) {
            val r = sqrt(wx * wx + wy * wy + wz * wz)
            if (r == 0.0) {
                g.fill(0.0)
                h.fill(0.0)
                return
            }
            val w = doubleArrayOf(wx, wy, wz)
            for (i in 0 until 3) {
                g[i] = 3.0 * r * w[i]
                for (l in 0 until 3) h[i * 3 + l] = 3.0 * ((if (i == l) r else 0.0) + w[i] * w[l] / r)
            }
        }

        private fun Vec3.component(i: Int): Float = when (i) {
            0 -> x
            1 -> y
            else -> z
        }
    }
}
