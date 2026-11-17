package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.PlacedPart
import kotlin.math.abs
import kotlin.math.floor

private const val SEARCH_STEPS = 48
private const val REFINE_STEPS = 8
private const val REFINE_ROUNDS = 3

/** How many places along a line of constant u or v the search for the one nearest a pixel first tries. */
private const val LINE_STEPS = 96

/** How many times the search along a line narrows in on the nearest place, each time to a third. */
private const val LINE_ROUNDS = 40

/** How far a line of constant u or v may miss its target, in pixels, and still stand for a drag held to a straight line. */
private const val ON_LINE_PIXELS = 0.01f

/** How many Newton steps a place takes to land exactly on its target pixel, and the step in u and v its slopes are read over. */
private const val NEWTON_STEPS = 8
private const val NEWTON_DELTA = 1e-3f

/** A place on a part's surface. */
data class SurfacePlace(val u: Float, val v: Float)

/** Which of a surface's two coordinates a search holds still. */
enum class SurfaceCoordinate { U, V }

/** How far the place at [u] and [v] on [part] shows from [pixel], or null where it faces away from the viewer or shows nowhere. */
private fun distanceAt(part: PlacedPart, u: Float, v: Float, pixel: Offset, view: HandleView): Float? {
    val point = part.pointOn(u, v) ?: return null
    val normal = part.normalAt(u, v) ?: return null
    if ((normal dot view.toViewer(point)) < 0f) return null
    return (view.toPixel(point) ?: return null).minus(pixel).getDistance()
}

/** The place on [part]'s surface facing the viewer that shows closest to [pixel], or null when none faces the viewer. */
fun surfaceUnder(part: PlacedPart, pixel: Offset, view: HandleView): SurfacePlace? {
    var best: SurfacePlace? = null
    var bestDistance = Float.POSITIVE_INFINITY
    fun consider(u: Float, v: Float) {
        val distance = distanceAt(part, u, v, pixel, view) ?: return
        if (distance < bestDistance) {
            bestDistance = distance
            best = SurfacePlace(u, v)
        }
    }
    for (i in 0..SEARCH_STEPS) for (j in 0 until SEARCH_STEPS) consider(i.toFloat() / SEARCH_STEPS, j.toFloat() / SEARCH_STEPS)
    var step = 1f / SEARCH_STEPS
    repeat(REFINE_ROUNDS) {
        val centre = best ?: return null
        for (i in -REFINE_STEPS..REFINE_STEPS) {
            for (j in -REFINE_STEPS..REFINE_STEPS) {
                val u = (centre.u + step * i / REFINE_STEPS).coerceIn(0f, 1f)
                val v = centre.v + step * j / REFINE_STEPS
                consider(u, v - floor(v))
            }
        }
        step /= REFINE_STEPS / 2f
    }
    return best
}

/**
 * The place facing the viewer on [part]'s line where [held] keeps [value], that shows closest to [pixel], or null where none of
 * that line faces the viewer. The held coordinate is [value] exactly.
 */
fun surfaceAlong(part: PlacedPart, held: SurfaceCoordinate, value: Float, pixel: Offset, view: HandleView): SurfacePlace? {
    fun place(t: Float) = if (held == SurfaceCoordinate.U) SurfacePlace(value, t - floor(t)) else SurfacePlace(t.coerceIn(0f, 1f), value)
    fun distance(t: Float): Float = place(t).let { distanceAt(part, it.u, it.v, pixel, view) } ?: Float.POSITIVE_INFINITY
    val starts = (0..LINE_STEPS).map { it.toFloat() / LINE_STEPS }
    var best = starts.minBy(::distance)
    if (distance(best).isInfinite()) return null
    var reach = 1f / LINE_STEPS
    repeat(LINE_ROUNDS) {
        val low = best - reach
        val high = best + reach
        best = listOf(low, (low + best) / 2f, best, (best + high) / 2f, high).minBy(::distance)
        reach /= 3f
    }
    return place(best)
}

/**
 * The place on [part] that shows exactly at [pixel], starting from [near]: the place [surfaceAlong] finds on [near]'s line of
 * constant u, or of constant v, when that line passes through [pixel], so the coordinate it holds stays exactly as it was; else
 * the place Newton's method settles on from the nearest of the search. Null where nothing facing the viewer shows near [pixel].
 */
fun surfaceAt(part: PlacedPart, pixel: Offset, view: HandleView, near: SurfacePlace?): SurfacePlace? {
    if (near != null) {
        for (held in SurfaceCoordinate.entries) {
            val value = if (held == SurfaceCoordinate.U) near.u else near.v
            val along = surfaceAlong(part, held, value, pixel, view) ?: continue
            val miss = distanceAt(part, along.u, along.v, pixel, view) ?: continue
            if (miss <= ON_LINE_PIXELS) return along
        }
    }
    val seed = surfaceUnder(part, pixel, view) ?: return null
    return newton(part, seed, pixel, view)
}

/** [seed] moved by Newton's method until it shows at [pixel], keeping each step that brings it nearer and facing the viewer. */
private fun newton(part: PlacedPart, seed: SurfacePlace, pixel: Offset, view: HandleView): SurfacePlace {
    fun shown(place: SurfacePlace): Offset? = part.pointOn(place.u, place.v)?.let(view::toPixel)
    var place = seed
    var miss = distanceAt(part, place.u, place.v, pixel, view) ?: return seed
    repeat(NEWTON_STEPS) {
        val at = shown(place) ?: return place
        val du = (shown(place.copy(u = (place.u + NEWTON_DELTA).coerceAtMost(1f))) ?: return place) - at
        val dv = (shown(place.copy(v = place.v + NEWTON_DELTA)) ?: return place) - at
        val scaleU = (place.u + NEWTON_DELTA).coerceAtMost(1f) - place.u
        if (scaleU == 0f) return place
        val a = du.x / scaleU
        val b = dv.x / NEWTON_DELTA
        val c = du.y / scaleU
        val d = dv.y / NEWTON_DELTA
        val determinant = a * d - b * c
        if (abs(determinant) < 1e-6f) return place
        val error = pixel - at
        val stepU = (d * error.x - b * error.y) / determinant
        val stepV = (a * error.y - c * error.x) / determinant
        val next = SurfacePlace((place.u + stepU).coerceIn(0f, 1f), (place.v + stepV).let { it - floor(it) })
        val nextMiss = distanceAt(part, next.u, next.v, pixel, view) ?: return place
        if (nextMiss >= miss) return place
        place = next
        miss = nextMiss
    }
    return place
}
