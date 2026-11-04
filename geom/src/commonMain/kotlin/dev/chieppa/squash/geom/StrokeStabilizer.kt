package dev.chieppa.squash.geom

/** How long the string between the pointer and the line is at a Stabilizer of 100%, in screen pixels. */
private const val MAX_STRING_PIXELS = 24f

/** The board's default Stabilizer, from 0 to 1. */
const val DEFAULT_STROKE_STABILIZER = 0.2f

/** The length of the string at a [stabilizer] from 0 to 1, for a screen pixel of size [pixel]. */
fun stabilizerString(stabilizer: Float, pixel: Float): Float = stabilizer * MAX_STRING_PIXELS * pixel

/**
 * Where a line trailing the pointer on a string of length [string] goes as the pointer reaches [pointer]: it stays at
 * [line] while the string is slack, and otherwise moves straight toward the pointer until the string is taut. Only the
 * pointer's positions move it, never the time between them, so a pointer held still leaves the line still.
 */
fun trailed(line: Vec2, pointer: Vec2, string: Float): Vec2 {
    val gap = pointer - line
    val distance = gap.magnitude
    return if (distance <= string) line else pointer - gap * (string / distance)
}
