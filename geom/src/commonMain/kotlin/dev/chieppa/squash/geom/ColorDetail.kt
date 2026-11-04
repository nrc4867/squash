package dev.chieppa.squash.geom

/** How fine a color shape's curves are flattened, as a step set by how far a body unit spans on screen. Each step doubles it. */
object ColorDetail {
    /** How far a flattened curve may lie from the true one, in pixels; under this an edge looks like the curve. */
    private const val PIXELS_OFF_CURVE = 0.25f

    /** The steps, as powers of two of the pixels per body unit they serve. */
    const val COARSEST = 4
    const val FINEST = 16

    /** The step before any view has said what it needs. */
    const val START = 10

    /** How many steps above what the view needs a step may stay. */
    private const val STEPS_TO_DROP = 2

    /** The tolerance in body units that [step] flattens to. */
    fun tolerance(step: Int): Float = PIXELS_OFF_CURVE / (1 shl step).toFloat()

    /**
     * The step to show [pixelsPerUnit] at, coming from [current]. It rises as soon as the view needs more and falls only once the
     * view is well below it, so a zoom crossing a step's edge back and forth does not flatten again each time.
     */
    fun stepFor(pixelsPerUnit: Float, current: Int): Int {
        var needed = COARSEST
        while (needed < FINEST && (1 shl needed).toFloat() < pixelsPerUnit) needed++
        return if (needed > current || needed <= current - STEPS_TO_DROP) needed else current
    }
}
