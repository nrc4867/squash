package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3

/** How many cells a fit's grid holds along the longer side of its outline. */
private const val FIT_CELLS = 160

/** How far a fit's grid reaches past its outline each way, as a share of the outline's height. */
private const val FIT_MARGIN = 0.35f

/**
 * One view of a hair outline set up for fitting: the [head] as that view sees it, the [outline], the region inside it ([hair]), how far
 * each cell lies outside that region ([outside]), and what else of the body the view sees nearest ([body]), so a lock hidden behind it
 * may lie outside the outline.
 */
class FitView(val head: HeadView, val outline: FlatOutline, occluders: List<PlacedPart>) {
    /** How tall the outline stands, the unit its errors are weighed in. */
    val height: Float = (outline.high.y - outline.low.y).coerceAtLeast(1e-4f)

    val grid: ViewGrid = ViewGrid.around(outline.low, outline.high, height * FIT_MARGIN, FIT_CELLS)
    val hair: BooleanArray = grid.inside(outline)
    val outside: FloatArray = grid.distanceTo(hair)
    val body: DepthRaster = DepthRaster(grid).apply { occluders.forEachIndexed { i, part -> draw(part, head, i) } }

    /** Whether the body covers [at]. */
    fun onBody(at: Vec2): Boolean = grid.index(at).let { it >= 0 && body.tag[it] >= 0 }

    /**
     * How far a piece of hair at [at] in this view, [depth] toward the viewer, shows outside the outline: 0 inside it or hidden behind the
     * body, else its distance to the outline's region, in drawn units.
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
}
