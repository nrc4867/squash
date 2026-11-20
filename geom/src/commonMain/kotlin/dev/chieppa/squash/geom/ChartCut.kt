package dev.chieppa.squash.geom

/**
 * A second shape a chart fill is kept inside, or outside where [outside]: [oval], read turned as the fill's oval is, or with none the
 * fill's opening moved [du] forward and [dv] up, read turned as the opening is. Its edge fades over [soft] of the chart's units on top of
 * a pixel.
 */
data class ChartCut(val oval: ChartOval?, val du: Float = 0f, val dv: Float = 0f, val outside: Boolean = false, val soft: Float = 0f) {
    /** How far outside the kept side of the cut ([u], [v]) lies, the opening being [opening]: at most zero on the kept side. */
    fun level(opening: ChartAlmond, u: Float, v: Float): Float {
        val raw = oval?.level(u, v) ?: opening.level(u - du, v - dv)
        return if (outside) -raw else raw
    }
}
