package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.TriangleSplits
import dev.chieppa.squash.geom.UvShapeDistance
import dev.chieppa.squash.geom.surface.HELD
import kotlin.math.abs
import kotlin.math.floor

/** How many pieces along each side a triangle a color shape's border may cross is split into. */
const val PAINT_SPLITS = 4

/**
 * A color shape's layer split finer where its border may cross: [splits] of the drawn mesh's triangles, and the layer's value at the
 * mesh's vertices and then at each of the splits' extra vertices, [values].
 */
class PaintSplits(val splits: TriangleSplits, val values: FloatArray) {
    companion object {
        /**
         * The triangles among [triangles] of [chart]'s drawn mesh that a shape's border may cross, a corner's value [values] less than the
         * triangle's longest side from zero, each split [PAINT_SPLITS] by [PAINT_SPLITS], with the layer's value at each extra vertex: the
         * mix of its corners' [eased] and [section] values, the eased value moved by how far [distance], read at the vertex's own (u, v)
         * on a run of [members] its corners all hold, lies from the mix of the corners' own distances; so a border narrower than a cell,
         * or bending inside one, is cut where the shape puts it. Null where no triangle is split.
         */
        fun of(
            chart: SurfaceChart,
            members: List<Int>,
            triangles: IntArray,
            distance: UvShapeDistance,
            closedV: Boolean,
            eased: FloatArray,
            section: FloatArray,
            values: FloatArray,
        ): PaintSplits? {
            val mesh = chart.mesh
            val indices = chart.indices
            val split = ArrayList<Int>()
            for (tri in triangles) {
                val a = indices[tri * 3]
                val b = indices[tri * 3 + 1]
                val c = indices[tri * 3 + 2]
                if (a >= chart.corners || b >= chart.corners || c >= chart.corners) continue
                val longest = maxOf((mesh.position(a) - mesh.position(b)).length, (mesh.position(b) - mesh.position(c)).length, (mesh.position(c) - mesh.position(a)).length)
                if (minOf(abs(values[a]), abs(values[b]), abs(values[c])) < longest) split += tri
            }
            if (split.isEmpty()) return null
            val splits = TriangleSplits.of(indices, chart.count, split.toIntArray(), PAINT_SPLITS)
            val out = splits.extended(values)
            val corners = IntArray(3)
            val weights = FloatArray(3)
            val slots = IntArray(3)
            for (k in 0 until splits.extraCount) {
                corners[0] = splits.a[k]
                corners[1] = splits.b[k]
                corners[2] = splits.c[k]
                weights[1] = splits.wb[k]
                weights[2] = splits.wc[k]
                weights[0] = 1f - weights[1] - weights[2]
                val member = members.firstOrNull { m -> (0 until 3).all { i -> slotOf(chart, corners[i], m) >= 0 } } ?: continue
                for (i in 0 until 3) slots[i] = slotOf(chart, corners[i], member)
                val scales = chart.scales(member)
                val firstV = mesh.heldV[corners[0] * HELD + slots[0]]
                var u = 0f
                var v = 0f
                var mixed = 0f
                var easedMix = 0f
                var sectionMix = 0f
                for (i in 0 until 3) {
                    val cu = mesh.heldU[corners[i] * HELD + slots[i]]
                    var cv = mesh.heldV[corners[i] * HELD + slots[i]]
                    if (closedV) cv = firstV + wrapped(cv - firstV)
                    u += cu * weights[i]
                    v += cv * weights[i]
                    mixed += distance.at(cu, cv, scales.along(cu), scales.around(cu)) * weights[i]
                    easedMix += eased[corners[i]] * weights[i]
                    sectionMix += section[corners[i]] * weights[i]
                }
                val own = distance.at(u, v, scales.along(u), scales.around(u))
                out[splits.base + k] = minOf(easedMix + own - mixed, sectionMix)
            }
            return PaintSplits(splits, out)
        }

        /** The held slot of [vertex] that holds run [member], -1 where none does. */
        private fun slotOf(chart: SurfaceChart, vertex: Int, member: Int): Int {
            for (k in 0 until HELD) if (chart.mesh.held[vertex * HELD + k] == member) return k
            return -1
        }

        /** [dv] carried into the half turn either side of zero. */
        private fun wrapped(dv: Float): Float = dv - floor(dv + 0.5f)
    }
}
