package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Immutable
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.acos

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()

/**
 * Which camera bin an angle falls in: [bins] in rising angle, with [edges] the degrees between each and the next. A bin holds
 * until the angle has gone [hysteresis] degrees past its edge, so a view lingering at an edge does not flip back and forth.
 */
@Immutable
data class AngleBins(val bins: List<CameraBin>, val edges: List<Float>, val hysteresis: Float) {
    init {
        require(bins.size == edges.size + 1) { "${bins.size} bins need ${bins.size - 1} edges, not ${edges.size}" }
    }

    /** The bin at [degrees], for a view that showed [current], or with none whichever [degrees] falls in. */
    fun binFor(degrees: Float, current: CameraBin? = null): CameraBin {
        val held = bins.indexOf(current)
        if (held >= 0) {
            val low = edges.getOrNull(held - 1)?.minus(hysteresis) ?: Float.NEGATIVE_INFINITY
            val high = edges.getOrNull(held)?.plus(hysteresis) ?: Float.POSITIVE_INFINITY
            if (degrees in low..high) return bins[held]
        }
        return bins[edges.count { degrees >= it }]
    }
}

/** The degrees between [forward] and [toCamera]. */
fun degreesBetween(forward: Vec3, toCamera: Vec3): Float =
    acos((forward.normalized() dot toCamera.normalized()).coerceIn(-1f, 1f)) * DEGREES_PER_RADIAN
