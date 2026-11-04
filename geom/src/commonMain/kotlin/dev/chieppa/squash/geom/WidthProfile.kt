package dev.chieppa.squash.geom

private const val STATIONS = 65
private const val PRESSURE_SMOOTH_PASSES = 2

/** How much of the nearest asked width an open start keeps, since it joins the body and never closes. */
private const val OPEN_START_SHARE = 0.6f

/** How far along a pen's stroke its light landing is read as the width it reached by then. */
private const val LANDING = 0.1f

/** A half-width asked for at a place along a stroke, 0 to 1 from its start. */
data class WidthSample(val at: Float, val halfWidth: Float)

/** A half-width at every place along a stroke. */
class WidthProfile internal constructor(private val stations: FloatArray) {
    fun at(place: Float): Float {
        val position = place.coerceIn(0f, 1f) * (STATIONS - 1)
        val i = position.toInt().coerceAtMost(STATIONS - 2)
        return stations[i] + (stations[i + 1] - stations[i]) * (position - i)
    }
}

private fun place(station: Int) = station.toFloat() / (STATIONS - 1)

/**
 * The widths a pen's [samples] ask for, lightly smoothed, since pressure jitters more than the hand means. With [fromPlane]
 * the stroke starts on a socket plane, where a pen landing light would pinch the part against the body, so the first stretch
 * holds the most it reached there. Null with no samples.
 */
fun pressureProfile(samples: List<WidthSample>, fromPlane: Boolean): WidthProfile? {
    if (samples.isEmpty()) return null
    val sorted = samples.sortedBy { it.at }
    val line = List(STATIONS) { Vec2(place(it), between(sorted, place(it))) }
    val widths = smooth(line, PRESSURE_SMOOTH_PASSES).map { it.y }.toFloatArray()
    if (fromPlane) {
        val landed = widths.indices.filter { place(it) <= LANDING }
        val held = landed.maxOf { widths[it] }
        landed.forEach { widths[it] = held }
    }
    return WidthProfile(widths)
}

private fun between(sorted: List<WidthSample>, at: Float): Float {
    val next = sorted.indexOfFirst { it.at >= at }
    if (next < 0) return sorted.last().halfWidth
    if (next == 0) return sorted.first().halfWidth
    val a = sorted[next - 1]
    val b = sorted[next]
    return if (b.at == a.at) b.halfWidth else a.halfWidth + (b.halfWidth - a.halfWidth) * (at - a.at) / (b.at - a.at)
}

/**
 * The widths a drag's [samples] ask for: exactly those at their places, easing between them, and from the first and the last
 * to the stroke's ends, where it closes to nothing unless [fromPlane] holds its start open at the body. Null with no samples.
 */
fun dragProfile(samples: List<WidthSample>, fromPlane: Boolean): WidthProfile? {
    if (samples.isEmpty()) return null
    val sorted = samples.sortedBy { it.at }
    val knots = buildList {
        if (sorted.first().at > 0f) add(WidthSample(0f, if (fromPlane) sorted.first().halfWidth * OPEN_START_SHARE else 0f))
        addAll(sorted)
        if (sorted.last().at < 1f) add(WidthSample(1f, 0f))
    }
    return WidthProfile(FloatArray(STATIONS) { eased(knots, place(it)) })
}

private fun eased(knots: List<WidthSample>, at: Float): Float {
    if (knots.size == 1) return knots.single().halfWidth
    val next = knots.indexOfFirst { it.at >= at }.coerceAtLeast(1)
    val a = knots[next - 1]
    val b = knots[next]
    if (b.at == a.at) return b.halfWidth
    val t = ((at - a.at) / (b.at - a.at)).coerceIn(0f, 1f)
    return a.halfWidth + (b.halfWidth - a.halfWidth) * t * t * (3f - 2f * t)
}
