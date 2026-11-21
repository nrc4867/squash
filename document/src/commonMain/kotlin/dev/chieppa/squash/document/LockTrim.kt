package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/**
 * A plane lock tips are cut to, for blunt ends and flat tops: level with the skull's middle raised [level] of the head's reach, tilted
 * [tilt] degrees so its front stands higher (positive) or lower. A lock is cut where it first crosses it from the side its root lies on;
 * a [blunt] cut ends it square, as wide as it was there.
 */
@Serializable
@Doc("A plane the group's lock tips are cut to: a blunt bob's hem, blunt bangs, a flat top.")
data class LockTrim(
    @Doc("How high the cut lies above the skull's middle, a share of the head's reach from its middle: negative below it, toward the jaw; about 1.1 over the top of the head.") @Range(min = -3.0, max = 2.0)
    val level: Float = -0.6f,
    @Doc("Degrees the cut tilts about the head's side to side: positive stands its front higher, a bob longer at the back.") @Range(min = -60.0, max = 60.0)
    val tilt: Float = 0f,
    @Doc("Whether a cut lock ends square, as wide as it was where it was cut; a lock not cut keeps its own tip.")
    val blunt: Boolean = true,
) {
    init {
        require(level in -3f..2f) { "A trim lies -3 to 2 of the head's reach above its middle, not $level" }
        require(tilt in -60f..60f) { "A trim tilts -60 to 60 degrees, not $tilt" }
    }
}

/** The most turns a tied lock coils round its tie into a bun. */
const val MOST_LOCK_COIL = 3f
