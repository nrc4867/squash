package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.PaintLayer
import kotlin.math.sqrt

/**
 * How far, in cells along the surface, [layer] reaches past the zero of [section], one value a vertex of [surface]'s drawn mesh: over
 * each side of the surface's mesh that the section crosses from its own side, where the layer paints the own end, the length from the
 * section's zero to the layer's, each read as a straight mix of the side's ends. The most of them is [most], and where it lies [at].
 */
class SectionOverreach(layer: PaintLayer, section: FloatArray, surface: CharacterSurface) {
    val most: Float
    val at: String

    init {
        val mesh = surface.mesh
        val t = mesh.triangles
        var worst = 0f
        var where = "nowhere"
        for (tri in 0 until t.size / 3) for (k in 0 until 3) {
            val (a, b) = t[tri * 3 + k] to t[tri * 3 + (k + 1) % 3]
            for ((own, out) in listOf(a to b, b to a)) {
                if (section[own] < 0f || section[out] >= 0f || layer.values[own] < 0f) continue
                val zero = section[own] / (section[own] - section[out])
                val painted = if (layer.values[out] >= 0f) 1f else layer.values[own] / (layer.values[own] - layer.values[out])
                val reach = (painted - zero) * length(mesh.positions, own, out) / mesh.cell
                if (reach > worst) {
                    worst = reach
                    val p = surface.render.position(own)
                    where = "%.2f, %.2f, %.2f".format(p.x, p.y, p.z)
                }
            }
        }
        most = worst
        at = where
    }

    private fun length(positions: FloatArray, a: Int, b: Int): Float {
        val dx = positions[a * 3] - positions[b * 3]
        val dy = positions[a * 3 + 1] - positions[b * 3 + 1]
        val dz = positions[a * 3 + 2] - positions[b * 3 + 2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
