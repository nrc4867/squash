package dev.chieppa.squash.geom.blend

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Below this two samples are one point, as a pole's are. */
private const val SAME_POINT = 1e-6f

/** Below this a normal is too short to give a direction. */
private const val NO_NORMAL = 1e-12f

/** Points on a patch with their outward unit normals, spread evenly over its grid. */
class PatchSamples(val points: List<Vec3>, val normals: List<Vec3>) {
    /** These samples moved by [place], which takes a point, and [turn], which takes a direction. */
    fun moved(place: (Vec3) -> Vec3, turn: (Vec3) -> Vec3) = PatchSamples(points.map(place), normals.map(turn))

    companion object {
        /** About [target] samples of [patch]: every few rings and every few points round them, a pole once. */
        fun of(patch: Patch, target: Int): PatchSamples {
            val stride = sqrt(patch.nu * patch.nv / target.toFloat()).coerceAtLeast(1f)
            val rings = spread(patch.nu, ceil(patch.nu / stride).toInt().coerceAtLeast(2), inclusive = true)
            val around = spread(patch.nv, ceil(patch.nv / stride).toInt().coerceAtLeast(3), inclusive = !patch.closedV)
            val points = mutableListOf<Vec3>()
            val normals = mutableListOf<Vec3>()
            for (ring in rings) {
                for (segment in around) {
                    val point = patch.point(patch.index(ring, segment))
                    if (points.any { (it - point).length < SAME_POINT }) continue
                    val normal = normalAt(patch, ring, segment) ?: continue
                    points += point
                    normals += normal
                }
            }
            return PatchSamples(points, normals)
        }

        /** [count] indices from 0 to [size] - 1, evenly apart, reaching the last when [inclusive]. */
        private fun spread(size: Int, count: Int, inclusive: Boolean): List<Int> {
            val wanted = count.coerceAtMost(size)
            if (wanted <= 1) return listOf(0)
            val span = if (inclusive) (size - 1).toFloat() / (wanted - 1) else size.toFloat() / wanted
            return (0 until wanted).map { (it * span).roundToInt().coerceAtMost(size - 1) }.distinct()
        }

        /** The outward unit normal of [patch]'s grid at a point, its rings' direction across its segments'; at a pole, away from the next ring. */
        fun normalAt(patch: Patch, ring: Int, segment: Int): Vec3? {
            if (patch.isPole(ring)) {
                val next = if (ring == 0) 1 else ring - 1
                val centre = (0 until patch.nv).fold(Vec3.ZERO) { sum, s -> sum + patch.point(patch.index(next, s)) } * (1f / patch.nv)
                return (patch.point(patch.index(ring, 0)) - centre).unitOrNull()
            }
            val ahead = patch.point(patch.index((ring + 1).coerceAtMost(patch.nu - 1), segment))
            val behind = patch.point(patch.index((ring - 1).coerceAtLeast(0), segment))
            val (left, right) = if (patch.closedV) {
                (segment + patch.nv - 1) % patch.nv to (segment + 1) % patch.nv
            } else {
                (segment - 1).coerceAtLeast(0) to (segment + 1).coerceAtMost(patch.nv - 1)
            }
            val across = patch.point(patch.index(ring, right)) - patch.point(patch.index(ring, left))
            return ((ahead - behind) cross across).unitOrNull()
        }

        private fun Vec3.unitOrNull(): Vec3? = if ((this dot this) < NO_NORMAL) null else normalized()
    }
}
