package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max

/** Where along the lid, a share of the way to a corner, a fading lid colour starts fading out toward that corner. */
const val CORNER_FADE_FROM = 0.7f

/**
 * A mark on an eye's upper lid, placed on the lid so it moves with it: rooted [along] the lid (-1 its back corner, 1 its front) and
 * [above] heights over the lid's edge there, a stroke [reachX] widths forward and [reachY] heights up to its tip, [halfWidth] heights
 * thick either side at its root and tapering to a point, its way up bent by [bend] so it runs out first and lifts at its tip.
 */
data class ChartLidMark(
    val along: Float,
    val above: Float,
    val reachX: Float,
    val reachY: Float,
    val halfWidth: Float,
    val bend: Float,
)

/**
 * Paint on an eye's upper lid, read in its opening's chart and riding the lid's edge where it stands (see [ChartLids]): with no [mark],
 * the lid's skin, from its edge up to where the open lid lies and on [above] heights over that at its middle, thinning to the corners,
 * fading out over [fade] heights up from the edge, and toward the corners, where [fade] is above 0; with a [mark], that mark, kept off the open part of the eye.
 * Widths are counted as [aspect] heights.
 */
data class ChartLidPaint(
    val above: Float = 0f,
    val fade: Float = 0f,
    val mark: ChartLidMark? = null,
    val aspect: Float = LID_ASPECT,
) {
    /** How far outside the paint ([u], [v]) lies about [opening], in its heights: at most zero inside. */
    fun level(opening: ChartAlmond, u: Float, v: Float): Float {
        val x = u / opening.width
        val y = v / opening.height
        val a = x.coerceIn(-1f, 1f)
        val mark = mark ?: return areaLevel(opening, x, y, a) * opening.height
        val root = mark.along.coerceIn(-1f, 1f)
        val stroke = TaperedStroke(mark.along, opening.topEdge(root) + mark.above, mark.reachX, mark.reachY, mark.halfWidth, 0f, mark.bend, aspect)
        return max(stroke.level(x, y), -opening.level(u, v) / opening.height) * opening.height
    }

    /** How much of the paint shows at ([u], [v]): 1 at the lid's edge, falling off over [fade] heights above it and toward the corners. */
    fun share(opening: ChartAlmond, u: Float, v: Float): Float {
        if (mark != null || fade <= 0f) return 1f
        val x = u / opening.width
        val a = x.coerceIn(-1f, 1f)
        return (1f - smoothstep(0f, fade, v / opening.height - opening.topEdge(a))) * (1f - smoothstep(CORNER_FADE_FROM, 1f, abs(x)))
    }

    private fun areaLevel(opening: ChartAlmond, x: Float, y: Float, a: Float): Float {
        val roof = opening.openTop(a) + above * max(1f - a * a, 0f)
        return max(max(opening.topEdge(a) - y, y - roof), (abs(x) - 1f) * aspect)
    }
}
