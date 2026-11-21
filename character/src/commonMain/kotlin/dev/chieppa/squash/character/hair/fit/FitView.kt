package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3

/** How many cells a fit's grid holds along the longer side of its outline. */
private const val FIT_CELLS = 160

/** How far a fit's grid reaches past its outline each way, as a share of the outline's height. */
private const val FIT_MARGIN = 0.35f

/**
 * How far, as a share of a view's height, a lock may stray outside its region: the front outline's, another drawn view's, and the margin
 * round the mass in a view with no outline.
 */
const val FRONT_SLACK = 0.02f
const val DRAWN_SLACK = 0.06f
private const val AROUND_SLACK = 0.02f

/**
 * One view of a hair outline set up for fitting: the [head] as that view sees it, the [outline], the region the hair may cover ([hair]),
 * how far each cell lies outside that region ([outside]), and what else of the body the view sees nearest ([body]), so a lock hidden
 * behind it may lie outside the region. A [drawn] view's region is inside its outline; a view with no outline drawn holds the hair within
 * a margin of the fitted mass and the skull instead, its outline only the box round that, with no tips.
 */
class FitView private constructor(
    val head: HeadView,
    val outline: FlatOutline,
    val grid: ViewGrid,
    val hair: BooleanArray,
    occluders: List<PlacedPart>,
    val drawn: Boolean,
    slackShare: Float,
) {
    /** How tall the outline stands, the unit its errors are weighed in. */
    val height: Float = (outline.high.y - outline.low.y).coerceAtLeast(1e-4f)

    /** How far outside the region, in drawn units, a lock may stray in this view before it is shortened. */
    val slack: Float = height * slackShare

    val outside: FloatArray = grid.distanceTo(hair)
    val body: DepthRaster = DepthRaster(grid).apply { occluders.forEachIndexed { i, part -> draw(part, head, i) } }

    /** Whether the body covers [at]. */
    fun onBody(at: Vec2): Boolean = grid.index(at).let { it >= 0 && body.tag[it] >= 0 }

    /**
     * How far a piece of hair at [at] in this view, [depth] toward the viewer, shows outside the region: 0 inside it or hidden behind the
     * body, else its distance to the region, in drawn units.
     */
    fun stray(at: Vec2, depth: Float): Float {
        val k = grid.index(at)
        if (k < 0) return grid.beyond(at) + height * FIT_MARGIN
        if (hair[k]) return 0f
        if (body.tag[k] >= 0 && depth < body.near[k]) return 0f
        return outside[k]
    }

    /** [stray] for a place in body space. */
    fun stray(p: Vec3): Float = stray(head.flat(p), head.depth(p))

    companion object {
        /**
         * [outline] as [head] sees it, its inside the region; a lock may stray [slack] of its height outside it: little from the front, the
         * view a sheet draws truest, more in the others, which seldom agree with it to the pixel.
         */
        fun of(head: HeadView, outline: FlatOutline, occluders: List<PlacedPart>, slack: Float = DRAWN_SLACK): FitView {
            val height = (outline.high.y - outline.low.y).coerceAtLeast(1e-4f)
            val grid = ViewGrid.around(outline.low, outline.high, height * FIT_MARGIN, FIT_CELLS)
            return FitView(head, outline, grid, grid.inside(outline), occluders, drawn = true, slackShare = slack)
        }

        /**
         * A view with no outline drawn: the region within [margin] drawn units of [points] (the mass and the skull, in body space) as
         * [head] sees them, and up to [rise] drawn units above that, as far as the drawn views' spikes stand over the mass, holes in it
         * filled. Null with no points.
         */
        fun around(head: HeadView, points: List<Vec3>, margin: Float, rise: Float, occluders: List<PlacedPart>): FitView? {
            if (points.isEmpty()) return null
            val flats = points.map(head::flat)
            val low = Vec2(flats.minOf { it.x } - margin, flats.minOf { it.y } - margin)
            val high = Vec2(flats.maxOf { it.x } + margin, flats.maxOf { it.y } + margin + rise)
            val box = FlatOutline(listOf(low, Vec2(high.x, low.y), high, Vec2(low.x, high.y)), emptyList(), emptyList())
            val grid = ViewGrid.around(low, high, (high.y - low.y) * FIT_MARGIN, FIT_CELLS)
            val seeds = BooleanArray(grid.size)
            for (f in flats) grid.index(f).let { if (it >= 0) seeds[it] = true }
            val near = grid.distanceTo(seeds)
            val region = BooleanArray(grid.size) { near[it] <= margin }
            val up = (rise / grid.cell).toInt()
            if (up > 0) {
                for (i in 0 until grid.width) {
                    var reach = -1
                    for (j in 0 until grid.height) {
                        val k = j * grid.width + i
                        if (region[k]) reach = j + up else if (j <= reach) region[k] = true
                    }
                }
            }
            return FitView(head, box, grid, grid.filled(region), occluders, drawn = false, slackShare = AROUND_SLACK)
        }
    }
}
