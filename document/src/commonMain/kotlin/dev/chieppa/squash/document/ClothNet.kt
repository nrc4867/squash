package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A new net's spacing between threads, in body units: a fishnet's holes at a character's scale. */
const val DEFAULT_NET_HOLE = 0.03f

/** A new net's thread width, in body units. */
const val DEFAULT_NET_THREAD = 0.004f

/** A new diamond net's angle: each thread's slant from the length of the part, in degrees. */
const val DEFAULT_NET_ANGLE = 40f

/** The least spacing between a net's threads, in body units. */
const val LEAST_NET_HOLE = 0.004f

/** The widest a net's holes are, in body units. */
const val MOST_NET_HOLE = 0.2f

/** The thinnest a net's thread is, in body units. */
const val LEAST_NET_THREAD = 0.0005f

/** The most a net's angle turns, in degrees. */
const val MOST_NET_ANGLE = 90f

/** How a net's two sets of threads cross. */
@Serializable
enum class NetPattern {
    /** Both slant from the part's length by the net's angle, one each way, so the holes are diamonds: a fishnet. */
    @SerialName("diamond") DIAMOND,

    /** One runs along the part turned by the net's angle and the other square to it, so the holes are squares: a mesh. */
    @SerialName("square") SQUARE,
}

/**
 * Cloth knitted as a net: two sets of threads [thread] wide, [hole] apart, crossing as [pattern] at [angle] degrees, laid on the body's
 * own coordinates under the cloth, so the net stretches with the skin in every pose; its holes show what lies under it (HLD: look,
 * See-through cloth).
 */
@Serializable
data class ClothNet(
    val pattern: NetPattern = NetPattern.DIAMOND,
    val hole: Float = DEFAULT_NET_HOLE,
    val thread: Float = DEFAULT_NET_THREAD,
    val angle: Float = DEFAULT_NET_ANGLE,
) {
    init {
        require(hole in LEAST_NET_HOLE..MOST_NET_HOLE) { "A net's hole lies outside $LEAST_NET_HOLE to $MOST_NET_HOLE" }
        require(thread >= LEAST_NET_THREAD && thread < hole) { "A net's thread runs from $LEAST_NET_THREAD to under its hole" }
        require(angle in 0f..MOST_NET_ANGLE) { "A net's angle lies outside 0 to $MOST_NET_ANGLE" }
    }

    companion object {
        /** The widest a thread is, as a share of its spacing. */
        const val MOST_THREAD_SHARE = 0.9f

        /** [pattern] with [hole], [thread] and [angle] held to their ranges. */
        fun of(pattern: NetPattern, hole: Float, thread: Float, angle: Float): ClothNet {
            val spacing = hole.coerceIn(LEAST_NET_HOLE, MOST_NET_HOLE)
            return ClothNet(pattern, spacing, thread.coerceIn(LEAST_NET_THREAD, spacing * MOST_THREAD_SHARE), angle.coerceIn(0f, MOST_NET_ANGLE))
        }
    }
}
