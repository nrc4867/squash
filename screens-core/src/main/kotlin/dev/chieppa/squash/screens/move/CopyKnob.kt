package dev.chieppa.squash.screens.move

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.model.CopySpec

/** What a press of Add socket places: a socket or a row of them, one showing copies over an area, or one showing copies along a path. */
enum class SocketLayout { SOCKETS, SPREAD, PATH }

/**
 * One number of a socket that shows copies, as Add socket's fields edit it: the parameter it is held in, [key] of the socket's id, what it
 * is when unset, its range, and the [layouts] that hold it. [percent] shows it as a percentage and [degrees] in degrees.
 */
enum class CopyKnob(
    val key: (Int) -> String,
    val default: Float,
    val min: Float,
    val max: Float,
    val layouts: Set<SocketLayout>,
    val percent: Boolean = false,
    val degrees: Boolean = false,
    val whole: Boolean = false,
) {
    SPREAD_COUNT(HandSockets::spread, 60f, 1f, CopySpec.MAX_COPIES.toFloat(), setOf(SocketLayout.SPREAD), whole = true),
    PATH_COUNT(HandSockets::repeat, 8f, 0f, CopySpec.MAX_COPIES.toFloat(), setOf(SocketLayout.PATH), whole = true),
    SEED(HandSockets::seed, 1f, 0f, 9999f, setOf(SocketLayout.SPREAD), whole = true),
    FROM_U(HandSockets::u, 0f, 0f, 1f, setOf(SocketLayout.SPREAD)),
    TO_U(HandSockets::to, 1f, 0f, 1f, setOf(SocketLayout.SPREAD)),
    FROM_V(HandSockets::v, 0f, -1f, 1f, setOf(SocketLayout.SPREAD)),
    TO_V(HandSockets::toV, 1f, 0f, 2f, setOf(SocketLayout.SPREAD)),
    SPACING(HandSockets::spacing, 0f, 0f, 0.5f, setOf(SocketLayout.SPREAD)),
    SIZE(HandSockets::size, 1f, 0.1f, 2.5f, setOf(SocketLayout.SPREAD, SocketLayout.PATH), percent = true),
    FALLOFF(HandSockets::falloff, 1f, 0.1f, 1f, setOf(SocketLayout.SPREAD), percent = true),
    SIZE_JITTER(HandSockets::sizeJitter, 0.2f, 0f, 0.9f, setOf(SocketLayout.SPREAD), percent = true),
    LEAN_JITTER(HandSockets::leanJitter, 10f, 0f, 60f, setOf(SocketLayout.SPREAD), degrees = true),
    TURN_JITTER(HandSockets::turnJitter, 180f, 0f, 180f, setOf(SocketLayout.SPREAD), degrees = true),
    PITCH(HandSockets::pitch, 0f, 0f, 0.5f, setOf(SocketLayout.PATH)),
    ROLL(HandSockets::roll, 0f, -180f, 180f, setOf(SocketLayout.PATH), degrees = true),
    SAG(HandSockets::sag, 0f, 0f, 0.5f, setOf(SocketLayout.PATH)),
    ;

    /** [value] held to this knob's range, and to a whole number where it counts. */
    fun held(value: Float): Float = value.coerceIn(min, max).let { if (whole) kotlin.math.round(it) else it }

    companion object {
        /** The layout a socket holding [params] shows, by socket [id]: spread or path where it shows copies, else sockets. */
        fun layoutOf(params: Map<String, Float>, id: Int): SocketLayout = when {
            HandSockets.spread(id) in params -> SocketLayout.SPREAD
            HandSockets.repeat(id) in params -> SocketLayout.PATH
            else -> SocketLayout.SOCKETS
        }
    }
}
