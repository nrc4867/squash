package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.document.LONGEST_LOCK
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.SHORTEST_LOCK
import dev.chieppa.squash.geom.Vec2
import kotlin.math.roundToInt

/** The least and most of a lock's length that lies along the head before it lifts. */
internal const val LEAST_LIE = 0.05f
internal const val MOST_LIE = 0.95f

/**
 * What a fit gives one lock, each value null where it leaves the lock's own: its [length] and [width] in body units, its [rise] in
 * degrees, its [sweep] and [fall], how much of it [lie]s along the head before it lifts, and how far its root moves [around] and [up] the
 * head in degrees.
 */
data class FittedLock(
    val length: Float? = null,
    val rise: Float? = null,
    val sweep: Float? = null,
    val fall: Float? = null,
    val around: Float? = null,
    val up: Float? = null,
    val width: Float? = null,
    val lie: Float? = null,
    val tip: LockTip? = null,
) {
    /** These values over [base], the lock's override at [index] with nothing fitted, or none. */
    fun over(base: LockOverride?, index: Int): LockOverride = (base ?: LockOverride(index)).copy(
        length = length ?: base?.length,
        rise = rise ?: base?.rise,
        sweep = sweep ?: base?.sweep,
        fall = fall ?: base?.fall,
        around = around ?: base?.around,
        up = up ?: base?.up,
        width = width ?: base?.width,
        lie = lie ?: base?.lie,
        tip = tip ?: base?.tip,
    )

    /** These values rounded, so a fit given the same outline twice writes the same values and nothing builds again. */
    fun rounded(): FittedLock = FittedLock(
        length?.let { round(it, 0.0005f).coerceIn(SHORTEST_LOCK, LONGEST_LOCK) },
        rise?.let { round(it, 0.5f) },
        sweep?.let { round(it, 0.01f).coerceIn(-1f, 1f) },
        fall?.let { round(it, 0.01f).coerceIn(0f, 1f) },
        around?.let { round(it, 0.5f) },
        up?.let { round(it, 0.5f) },
        width?.let { round(it, 0.0005f) },
        lie?.let { round(it, 0.01f).coerceIn(LEAST_LIE, MOST_LIE) },
        tip,
    )
}

private fun round(value: Float, step: Float): Float = (value / step).roundToInt() * step

/**
 * Where one lock is fitted to in one [view]: its tip on [tip], its sides along the outline's [edge] round it, [width] across in body
 * units where the outline gives it, and its end [shape] where the outline draws one, a flick.
 */
class LockTarget(val view: FitView, val tip: Vec2, val edge: List<Vec2>, val width: Float?, val shape: LockTip? = null)
