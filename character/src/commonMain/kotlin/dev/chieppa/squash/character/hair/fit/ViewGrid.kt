package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val DIAGONAL = 1.4142135f

/** A grid of square cells [cell] across over a box of a view's flat frame, its lowest corner at [low], [width] by [height] cells. */
class ViewGrid(val low: Vec2, val cell: Float, val width: Int, val height: Int) {
    val size: Int get() = width * height

    /** The cell holding [p], or -1 off the grid. */
    fun index(p: Vec2): Int {
        val i = floor((p.x - low.x) / cell).toInt()
        val j = floor((p.y - low.y) / cell).toInt()
        return if (i in 0 until width && j in 0 until height) j * width + i else -1
    }

    /** The middle of the cell at column [i] and row [j]. */
    fun centre(i: Int, j: Int): Vec2 = Vec2(low.x + (i + 0.5f) * cell, low.y + (j + 0.5f) * cell)

    /** How far [p] lies outside the grid's box, 0 on it. */
    fun beyond(p: Vec2): Float {
        val dx = max(max(low.x - p.x, p.x - (low.x + width * cell)), 0f)
        val dy = max(max(low.y - p.y, p.y - (low.y + height * cell)), 0f)
        return sqrt(dx * dx + dy * dy)
    }

    /** The cells whose middles lie inside [outline]. */
    fun inside(outline: FlatOutline): BooleanArray = BooleanArray(size) { k -> outline.contains(centre(k % width, k / width)) }

    /**
     * How far each cell lies from the nearest cell [mask] holds, in flat units, 0 on one: two passes over the grid carrying the nearest
     * distance by steps of one cell and of a diagonal.
     */
    fun distanceTo(mask: BooleanArray): FloatArray {
        val far = (width + height).toFloat() * 2f
        val d = FloatArray(size) { if (mask[it]) 0f else far }
        fun relax(k: Int, i: Int, j: Int, step: Float) {
            if (i !in 0 until width || j !in 0 until height) return
            val v = d[j * width + i] + step
            if (v < d[k]) d[k] = v
        }
        for (j in 0 until height) for (i in 0 until width) {
            val k = j * width + i
            relax(k, i - 1, j, 1f)
            relax(k, i, j - 1, 1f)
            relax(k, i - 1, j - 1, DIAGONAL)
            relax(k, i + 1, j - 1, DIAGONAL)
        }
        for (j in height - 1 downTo 0) for (i in width - 1 downTo 0) {
            val k = j * width + i
            relax(k, i + 1, j, 1f)
            relax(k, i, j + 1, 1f)
            relax(k, i + 1, j + 1, DIAGONAL)
            relax(k, i - 1, j + 1, DIAGONAL)
        }
        for (k in d.indices) d[k] *= cell
        return d
    }

    companion object {
        /** A grid over [low] to [high] widened by [margin] each way, about [cells] cells along its longer side. */
        fun around(low: Vec2, high: Vec2, margin: Float, cells: Int): ViewGrid {
            val l = Vec2(low.x - margin, low.y - margin)
            val spanX = high.x - low.x + 2f * margin
            val spanY = high.y - low.y + 2f * margin
            val cell = max(spanX, spanY) / cells
            return ViewGrid(l, cell, ceil(spanX / cell).toInt().coerceAtLeast(1), ceil(spanY / cell).toInt().coerceAtLeast(1))
        }
    }
}

/** For each cell of [grid], the nearest surface a view sees there: how far toward the viewer it lies, and a tag for what it is (-1 none). */
class DepthRaster(val grid: ViewGrid) {
    val near = FloatArray(grid.size) { Float.NEGATIVE_INFINITY }
    val tag = IntArray(grid.size) { -1 }

    /** Draws [part]'s mesh as [view] sees it, tagged [tag]. */
    fun draw(part: PlacedPart, view: HeadView, tag: Int) {
        val mesh = part.surface.mesh
        val count = mesh.vertexCount
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val ds = FloatArray(count)
        for (v in 0 until count) {
            val p = part.origin + mesh.position(v)
            val f = view.flat(p)
            xs[v] = f.x
            ys[v] = f.y
            ds[v] = view.depth(p)
        }
        val indices = mesh.indices
        for (t in 0 until indices.size / 3) {
            val a = indices[3 * t]
            val b = indices[3 * t + 1]
            val c = indices[3 * t + 2]
            triangle(xs[a], ys[a], ds[a], xs[b], ys[b], ds[b], xs[c], ys[c], ds[c], tag)
        }
    }

    private fun triangle(ax: Float, ay: Float, ad: Float, bx: Float, by: Float, bd: Float, cx: Float, cy: Float, cd: Float, tag: Int) {
        val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if (area * area < 1e-20f) return
        val cell = grid.cell
        val i0 = floor((min(ax, min(bx, cx)) - grid.low.x) / cell - 0.5f).toInt().coerceAtLeast(0)
        val i1 = ceil((max(ax, max(bx, cx)) - grid.low.x) / cell - 0.5f).toInt().coerceAtMost(grid.width - 1)
        val j0 = floor((min(ay, min(by, cy)) - grid.low.y) / cell - 0.5f).toInt().coerceAtLeast(0)
        val j1 = ceil((max(ay, max(by, cy)) - grid.low.y) / cell - 0.5f).toInt().coerceAtMost(grid.height - 1)
        if (i0 > i1 || j0 > j1) return
        for (j in j0..j1) {
            val y = grid.low.y + (j + 0.5f) * cell
            for (i in i0..i1) {
                val x = grid.low.x + (i + 0.5f) * cell
                val wb = ((x - ax) * (cy - ay) - (cx - ax) * (y - ay)) / area
                val wc = ((bx - ax) * (y - ay) - (x - ax) * (by - ay)) / area
                val wa = 1f - wb - wc
                if (wa < 0f || wb < 0f || wc < 0f) continue
                val d = wa * ad + wb * bd + wc * cd
                val k = j * grid.width + i
                if (d > near[k]) {
                    near[k] = d
                    this.tag[k] = tag
                }
            }
        }
    }
}
