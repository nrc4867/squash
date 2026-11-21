package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.WIDEST_LOCK
import dev.chieppa.squash.geom.Vec2
import kotlin.math.ceil

/** How many cells the front view's grid holds across the head. */
private const val GAP_CELLS = 200

/** How far below the mass's edge, as a share of the head's height, skin counts as at the hairline. */
private const val HAIRLINE_BAND = 0.05f

/** How low on the head, as a share of its height over its origin, the hairline is looked for: above the brow. */
private const val FOREHEAD_LOW = 0.45f

/** How far behind the skin, as a share of the head's height, the mass may lie and still cover it, where the two meet. */
private const val COVER_DEPTH = 0.005f

/** How much further than half a gap each of its neighbours reaches into it, as a share of the gap, so the two overlap. */
private const val GAP_OVERLAP = 0.25f

/** The most times its own width a lock is widened to close a gap beside it; a gap wider than that closes is bare scalp, left as it is. */
private const val MOST_WIDENED = 2f

/**
 * The locks of [locks] either side of a gap at the hairline, widened so the two overlap there: a gap is skin of [head], as [front] sees
 * it, just below where the hair [mass] covers it, that no lock in front of it covers, with a lock in front either side of it near enough
 * that neither need be more than [MOST_WIDENED] times as wide. Locks beside no such gap are not in the map.
 */
internal fun hairlineGapsClosed(locks: List<SettledLock>, front: HeadView, head: PlacedPart, mass: List<PointGrid>): Map<Pair<LockGroupId, Int>, FittedLock> {
    val mesh = head.surface.mesh
    val flat = List(mesh.vertexCount) { front.flat(head.origin + mesh.position(it)) }
    if (flat.isEmpty() || mass.isEmpty()) return emptyMap()
    val low = Vec2(flat.minOf { it.x }, flat.minOf { it.y })
    val high = Vec2(flat.maxOf { it.x }, flat.maxOf { it.y })
    val grid = ViewGrid.around(low, high, 0f, GAP_CELLS)
    val skin = DepthRaster(grid).apply { draw(head, front, 0) }
    val hair = DepthRaster(grid).apply { mass.forEach { draw(it, front, 0) } }
    val height = high.y - low.y
    val slack = height * COVER_DEPTH
    fun covered(k: Int) = hair.tag[k] >= 0 && (skin.tag[k] < 0 || hair.near[k] >= skin.near[k] - slack)
    fun skinAt(at: Vec2): Float = grid.index(at).let { k -> if (k >= 0 && skin.tag[k] >= 0) skin.near[k] else Float.NEGATIVE_INFINITY }
    val band = ceil(height * HAIRLINE_BAND / grid.cell).toInt()
    val ribbons = locks.map { lock -> lock to Ribbon.of(lock, front) }
    val widen = HashMap<Pair<LockGroupId, Int>, Float>()
    for (j in 0 until grid.height) for (i in 0 until grid.width) {
        val k = j * grid.width + i
        if (skin.tag[k] < 0 || covered(k)) continue
        val at = grid.centre(i, j)
        if (at.y < high.y * FOREHEAD_LOW) continue
        if ((1..band).none { up -> j + up < grid.height && covered(k + up * grid.width) }) continue
        if (ribbons.any { (_, ribbon) -> ribbon.covers(at, skin.near[k] - slack) }) continue
        val crossing = ribbons.mapNotNull { (lock, ribbon) -> ribbon.across(at.y)?.takeIf { it.depth >= skinAt(it.at) - slack }?.let { lock to it } }
        val left = crossing.filter { it.second.at.x < at.x }.maxByOrNull { it.second.at.x } ?: continue
        val right = crossing.filter { it.second.at.x > at.x }.minByOrNull { it.second.at.x } ?: continue
        val gap = right.second.at.x - right.second.half - (left.second.at.x + left.second.half)
        val share = gap * (0.5f + GAP_OVERLAP)
        val factors = listOf(left, right).map { (_, cross) -> (cross.half + share) / cross.half.coerceAtLeast(1e-5f) }
        if (factors.any { it > MOST_WIDENED }) continue
        for ((side, factor) in listOf(left, right).zip(factors)) {
            val key = side.first.key
            if (factor > (widen[key] ?: 1f)) widen[key] = factor
        }
    }
    return locks.filter { it.key in widen }.associate { lock ->
        lock.key to lock.own.copy(width = (lock.spec().width * widen.getValue(lock.key)).coerceAtMost(WIDEST_LOCK)).rounded()
    }
}

/** Where a lock crosses a height as a view sees it: its middle [at], its [half] width and how far toward the viewer it lies ([depth]). */
private class Crossing(val at: Vec2, val half: Float, val depth: Float)

/** A lock as a view sees it: the middle of each place along it, its half width there and its depth, in drawn units. */
private class Ribbon(val centre: List<Vec2>, val half: List<Float>, val depth: List<Float>) {
    /** Whether the lock covers [at], lying nearer the viewer than [behind]. */
    fun covers(at: Vec2, behind: Float): Boolean = (0 until centre.size - 1).any { k ->
        maxOf(depth[k], depth[k + 1]) >= behind && toSegment(at, centre[k], centre[k + 1]) <= maxOf(half[k], half[k + 1])
    }

    /** Where the lock first crosses the height [y] from its root, or null where it never does. */
    fun across(y: Float): Crossing? {
        for (k in 0 until centre.size - 1) {
            val a = centre[k]
            val b = centre[k + 1]
            if ((a.y - y) * (b.y - y) > 0f || a.y == b.y) continue
            val t = (y - a.y) / (b.y - a.y)
            return Crossing(Vec2(a.x + (b.x - a.x) * t, y), half[k] + (half[k + 1] - half[k]) * t, depth[k] + (depth[k + 1] - depth[k]) * t)
        }
        return null
    }

    companion object {
        fun of(lock: SettledLock, view: HeadView): Ribbon {
            val spec = lock.spec()
            val along = lock.along(spec)
            val n = along.size
            return Ribbon(
                along.map(view::flat),
                List(n) { k -> view.drawn(spec.width) / 2f * lockShare(k.toFloat() / (n - 1), spec.taper) },
                along.map(view::depth),
            )
        }
    }
}
