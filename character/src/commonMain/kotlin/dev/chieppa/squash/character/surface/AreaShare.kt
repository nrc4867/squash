package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.geom.UvLevel
import kotlin.math.sqrt

/** How steeply an area's blurred cells rise across its edge, per cell: a step blurred by [CLOTH_BLUR_CELLS] cells. */
private val EDGE_SLOPE = 1f / (CLOTH_BLUR_CELLS * sqrt(2f * kotlin.math.PI.toFloat()))

/**
 * Where area [area] of [cloth] holds the cloth, read as a signed distance: how far its own blurred cells stand above the most of every
 * other area's and of the hem level, turned into body units by the slope across an edge and a cell's size there. Zero along the seams
 * where it meets another area, as [ClothSeams] reads them, and along its hem.
 */
data class AreaShare(val cloth: ClothField, val area: Int) : UvLevel {
    override fun at(u: Float, v: Float, uScale: Float, vScale: Float): Float {
        var others = CLOTH_HEM_LEVEL
        for (other in 0 until cloth.areaCount) if (other != area) others = maxOf(others, cloth.areaAt(other, u, v))
        val cell = (uScale / CHART_ROWS + vScale / CHART_COLUMNS) / 2f
        return (cloth.areaAt(area, u, v) - others) / EDGE_SLOPE * cell
    }
}
