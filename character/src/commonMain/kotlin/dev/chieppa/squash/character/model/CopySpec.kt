package dev.chieppa.squash.character.model

/** How a socket lays out the copies of the one part on it: scattered over an area of its part, or repeated along a path on it. */
enum class CopyLayout { AREA, PATH }

/** A place on a part's surface, [u] along it and [v] round it. */
data class SurfacePlace(val u: Float, val v: Float)

/**
 * The copies a socket shows of the one part placed on it, each an instance of that part's one build. [count] copies, or along a path
 * with a [pitch] as many as fit, over [area] or along [path] on the socket's part. [seed] picks the same places and jitter every time.
 * Each copy starts at [size] times the part's own size, falling to [falloff] of that toward an area's edges over its outer [edge]
 * share, then varies by [sizeJitter] either way; it leans [leanJitter] degrees at most from the surface's normal, tilted [tilt] degrees
 * toward the part's end, and turns [turnJitter] degrees at most either way about itself. Over an area no two copies stand nearer than
 * [spacing], in body units. Along a path each copy rolls [roll] degrees more than the one before it, every other one turned, and with
 * [sag] the path droops that far at its middle, in body units, as a chain hangs between two anchors, lying on the part where it meets it.
 * A copy sinks [sink] times the part's own depth into the surface, or stands [outset] off it. With [patch] the area or path lies on that
 * patch of the part, a digit, and not on its first run. See HLD: data-model, Copies.
 */
data class CopySpec(
    val layout: CopyLayout,
    val count: Int,
    val area: CopyArea = CopyArea.WHOLE,
    val path: List<SurfacePlace> = emptyList(),
    val seed: Int = 0,
    val spacing: Float = 0f,
    val size: Float = 1f,
    val falloff: Float = 1f,
    val edge: Float = DEFAULT_EDGE,
    val sizeJitter: Float = 0f,
    val leanJitter: Float = 0f,
    val turnJitter: Float = 0f,
    val tilt: Float = 0f,
    val sink: Float = 1f,
    val outset: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val sag: Float = 0f,
    val patch: String? = null,
) {
    companion object {
        /** The most copies one socket shows. */
        const val MAX_COPIES = 1000

        /** The outer share of an area its copies' size falls off over when no edge is given. */
        const val DEFAULT_EDGE = 0.35f
    }
}

/**
 * An area of a part's surface: [u] from [fromU] to [toU] along it and [v] from [fromV] to [toV] round it, running on past 1 to wrap round
 * the part; a whole turn round it is a band.
 */
data class CopyArea(val fromU: Float, val toU: Float, val fromV: Float, val toV: Float) {
    /** Whether it runs a whole turn round the part, so no edge bounds it in v. */
    val aroundWhole: Boolean get() = kotlin.math.abs(toV - fromV) >= 1f - WHOLE_TURN

    companion object {
        private const val WHOLE_TURN = 1e-3f

        /** The whole surface. */
        val WHOLE = CopyArea(0f, 1f, 0f, 1f)
    }
}
