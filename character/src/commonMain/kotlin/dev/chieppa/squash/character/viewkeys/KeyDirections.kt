package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.document.KeyView
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.field.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()

/** Degrees between neighbouring key views round the head. */
private const val VIEW_STEP = 45f

/**
 * Below this share of the way to the camera lying level with the head (the cosine of how far the camera looks down or up at it) the
 * keys fade out; above [LEVEL_FULL] they hold whole. A camera straight above or below the head has no side, so it shows the head as it
 * rests.
 */
private const val LEVEL_NONE = 0.25f
private const val LEVEL_FULL = 0.7f

/** A direction a head is keyed at: a [view] and, off the head's middle line, the camera's [side] of the head. */
data class KeyDirection(val view: KeyView, val side: Side?) {
    /** Whether a key for this direction is the left's mirrored: the camera on the head's right. */
    val mirrored: Boolean get() = side == Side.RIGHT
}

/** The eight key directions round a head, from the front round its left to the back and on round its right. */
val KEY_DIRECTIONS: List<KeyDirection> = listOf(
    KeyDirection(KeyView.FRONT, null),
    KeyDirection(KeyView.THREE_QUARTER, Side.LEFT),
    KeyDirection(KeyView.SIDE, Side.LEFT),
    KeyDirection(KeyView.THREE_QUARTER_BACK, Side.LEFT),
    KeyDirection(KeyView.BACK, null),
    KeyDirection(KeyView.THREE_QUARTER_BACK, Side.RIGHT),
    KeyDirection(KeyView.SIDE, Side.RIGHT),
    KeyDirection(KeyView.THREE_QUARTER, Side.RIGHT),
)

/** Where [view] on [side] lies in [KEY_DIRECTIONS]. */
fun directionIndex(view: KeyView, side: Side?): Int {
    val step = view.ordinal
    return if (view.centred || side != Side.RIGHT) step else KEY_DIRECTIONS.size - step
}

/**
 * How much each of [KEY_DIRECTIONS] holds for a camera lying [forward], [up] and [left] of a head in its own directions (any length): the
 * two key views either side of the camera's way round the head's facing, eased smoothly between them, so a sweep never jumps; all
 * fading toward none as the camera rises over or sinks under the head. The weights sum to at most one; what they leave is the head at rest.
 */
fun keyWeights(forward: Float, up: Float, left: Float): FloatArray {
    val weights = FloatArray(KEY_DIRECTIONS.size)
    val length = sqrt(forward * forward + up * up + left * left)
    if (length == 0f) return weights
    val level = smoothstep(LEVEL_NONE, LEVEL_FULL, sqrt(forward * forward + left * left) / length)
    if (level == 0f) return weights
    val round = atan2(left, forward) * DEGREES_PER_RADIAN
    val side = if (round >= 0f) Side.LEFT else Side.RIGHT
    val degrees = abs(round).coerceAtMost(KeyView.BACK.degrees)
    val below = (degrees / VIEW_STEP).toInt().coerceAtMost(KeyView.entries.size - 2)
    val ease = smoothstep(0f, 1f, (degrees - below * VIEW_STEP) / VIEW_STEP)
    weights[directionIndex(KeyView.entries[below], side)] += (1f - ease) * level
    weights[directionIndex(KeyView.entries[below + 1], side)] += ease * level
    return weights
}
