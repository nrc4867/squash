package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * How far a garment stands off a part at [u] along it, in body units: [ease] at its front, and [back] at its back where that differs,
 * the sides between the two.
 */
@Serializable
data class EaseKey(val u: Float, val ease: Float, val back: Float? = null) {
    init {
        require(ease >= 0f && (back == null || back >= 0f)) { "An ease key at u $u stands off by a negative ease" }
    }

    /** Its ease at the back: [back], else [ease]. */
    val atBack: Float get() = back ?: ease
}

/**
 * A garment's stand-off along one covered part: [keys] in order along u, read as one smooth curve through them, with no overshoot
 * between keys and flat past the first and last (see [at]); the front and the back each have their own curve. See HLD: data-model,
 * Garments.
 */
@Serializable
data class EaseProfile(val keys: List<EaseKey>) {
    init {
        require(keys.isNotEmpty()) { "An ease profile has no keys" }
        require(keys.zipWithNext().all { (a, b) -> a.u < b.u }) { "An ease profile's keys do not run along u in order" }
    }

    private val us: FloatArray get() = FloatArray(keys.size) { keys[it].u }

    /** The ease at [u] along the part, [back] of the way from its front (0) to its back (1). */
    fun at(u: Float, back: Float = 0f): Float {
        val front = smoothThrough(us, FloatArray(keys.size) { keys[it].ease }, u)
        if (keys.none { it.back != null }) return front
        val rear = smoothThrough(us, FloatArray(keys.size) { keys[it].atBack }, u)
        val t = back.coerceIn(0f, 1f)
        return front + (rear - front) * t
    }

    /** The least ease anywhere along it, front or back: no curve between two keys dips below the lower. */
    val least: Float get() = keys.minOf { minOf(it.ease, it.atBack) }

    /** The most ease anywhere along it, front or back. */
    val most: Float get() = keys.maxOf { maxOf(it.ease, it.atBack) }

    /** Whether it stands off the same everywhere. */
    val uniform: Boolean get() = least == most

    companion object {
        /** A profile standing off by [ease] everywhere. */
        fun flat(ease: Float): EaseProfile = EaseProfile(listOf(EaseKey(0f, ease)))
    }
}

/**
 * The monotone cubic through ([xs], [ys]) at [x] (Fritsch and Carlson): smooth through every point, never past either neighbour between
 * two, flat past the ends.
 */
internal fun smoothThrough(xs: FloatArray, ys: FloatArray, x: Float): Float {
    val n = xs.size
    if (n == 1 || x <= xs[0]) return ys[0]
    if (x >= xs[n - 1]) return ys[n - 1]
    var i = 0
    while (x > xs[i + 1]) i++
    val h = xs[i + 1] - xs[i]
    val t = (x - xs[i]) / h
    val m0 = slopeAt(xs, ys, i)
    val m1 = slopeAt(xs, ys, i + 1)
    val t2 = t * t
    val t3 = t2 * t
    return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * m0 + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * m1
}

/** The curve's slope at point [i]: zero at an end, at a peak or trough, and otherwise the harmonic mean of the slopes either side. */
private fun slopeAt(xs: FloatArray, ys: FloatArray, i: Int): Float {
    if (i == 0 || i == xs.size - 1) return 0f
    val before = (ys[i] - ys[i - 1]) / (xs[i] - xs[i - 1])
    val after = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i])
    if (before * after <= 0f) return 0f
    return 2f / (1f / before + 1f / after)
}
