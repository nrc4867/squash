package dev.chieppa.squash.geom.field

/**
 * A [SweptSolid]'s [count] rings as flat arrays: per ring its middle, tangent and up, three values each, its u, the gap to the next
 * ring's middle (0 at the last), its widest radius ([reaches]), its radius in each of [ANGLES] directions round its plane and its
 * section's v there, and whether it has a section ([hasV], false for a ring closed to a point); and the run's length per u at its first
 * and last pair of rings.
 */
class RunRings(
    val count: Int,
    val middles: FloatArray,
    val tangents: FloatArray,
    val ups: FloatArray,
    val us: FloatArray,
    val gaps: FloatArray,
    val reaches: FloatArray,
    val radii: FloatArray,
    val vs: FloatArray,
    val hasV: BooleanArray,
    val startPerU: Float,
    val endPerU: Float,
) {
    companion object {
        /** Directions round a ring its radius and v are held at. */
        const val ANGLES: Int = RING_ANGLES
    }
}
