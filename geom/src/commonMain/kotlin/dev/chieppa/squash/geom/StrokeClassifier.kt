package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Smoothing applied before a stroke is read, whatever the drawer's Smooth, so hand jitter does not read as shape. */
private const val READING_SMOOTH = 0.5f

/** A stroke shorter than this on screen has no shape to read. */
private const val MIN_SHAPE_PIXELS = 32f

/** A line's chord is at least this share of the stroke, and the stroke strays from it at most this share of the chord or these pixels. */
private const val MIN_CHORD_OF_LENGTH = 0.9f
private const val LINE_STRAY_OF_CHORD = 0.03f
private const val LINE_STRAY_PIXELS = 3f

/** A stroke that does not cross itself still reads as closed when its ends are this share of its length apart. */
private const val LOOP_GAP_OF_LENGTH = 0.12f

/** How far a closed stroke may stray from a shape, as shares of the square root of what it encloses or in pixels. */
private const val RMS_STRAY_OF_SIZE = 0.022f
private const val WORST_STRAY_OF_SIZE = 0.07f
private const val RMS_STRAY_PIXELS = 1.5f
private const val WORST_STRAY_PIXELS = 3f

/** A capsule is chosen over an ellipse that also fits only when it strays this much less, so a plain oval stays an ellipse. */
private const val CAPSULE_PREFERENCE = 0.75f

/** A capsule's straight run is at least this share of its radius; shorter is an ellipse. */
private const val MIN_CAPSULE_RUN_OF_RADIUS = 0.25f

/** A uniform ellipse's second moment along a half axis is a quarter of that half axis squared. */
private const val ELLIPSE_MOMENT_DIVISOR = 4f

/**
 * The clean shape a stroke best reads as, or null if it should stay freehand. [samples] are in the plane and [pixel] is a
 * screen pixel's size there. An open stroke lying along its chord is a line. A stroke that comes back to its start is
 * compared with the ellipse of the same area and moments and with the capsule on the same axis, by how far the stroke
 * strays from each.
 */
fun classifyStroke(samples: List<Vec2>, pixel: Float): StrokeShape? {
    val line = smoothedOpenLine(samples, pixel, READING_SMOOTH) ?: return null
    val length = arcLength(line)
    if (length < MIN_SHAPE_PIXELS * pixel) return null
    return straightLine(line, length, pixel) ?: closedShape(line, length, pixel)
}

/** [this] as points for the pipeline a stroke takes, at the spacing strokes are resampled to for a screen pixel of size [pixel]. */
fun StrokeShape.samples(pixel: Float): List<Vec2> = outline(strokeSpacing(pixel))

private fun straightLine(line: List<Vec2>, length: Float, pixel: Float): LineShape? {
    val from = line.first()
    val to = line.last()
    val chord = (to - from).magnitude
    if (chord < MIN_CHORD_OF_LENGTH * length) return null
    val allowed = max(LINE_STRAY_OF_CHORD * chord, LINE_STRAY_PIXELS * pixel)
    return if (line.all { distanceToSegment(it, from, to) <= allowed }) LineShape(from, to) else null
}

private fun closedShape(line: List<Vec2>, length: Float, pixel: Float): StrokeShape? {
    val spacing = strokeSpacing(pixel)
    val gap = (line.last() - line.first()).magnitude
    val ring = findLoop(line, closeGap(pixel), spacing)?.line
        ?: if (gap <= LOOP_GAP_OF_LENGTH * length) chordLoop(line, spacing).line else return null
    val moments = RingMoments.of(ring) ?: return null
    val size = sqrt(moments.area)
    fun Stray.fits() = rms <= max(RMS_STRAY_OF_SIZE * size, RMS_STRAY_PIXELS * pixel) &&
        worst <= max(WORST_STRAY_OF_SIZE * size, WORST_STRAY_PIXELS * pixel)
    val ellipse = moments.ellipse(line.first())
    val capsule = moments.capsule(ring, line.first())
    val ellipseStray = Stray.of(ring, ellipse.outline(spacing))
    val capsuleStray = capsule?.let { Stray.of(ring, it.outline(spacing)) }
    return when {
        capsuleStray != null && capsuleStray.fits() && (!ellipseStray.fits() || capsuleStray.rms < CAPSULE_PREFERENCE * ellipseStray.rms) -> capsule
        ellipseStray.fits() -> ellipse
        else -> null
    }
}

/** How far a stroke's points lie from a polyline. */
private class Stray(val rms: Float, val worst: Float) {
    companion object {
        fun of(points: List<Vec2>, outline: List<Vec2>): Stray {
            var squares = 0f
            var worst = 0f
            for (point in points) {
                val distance = (1 until outline.size).minOf { distanceToSegment(point, outline[it - 1], outline[it]) }
                squares += distance * distance
                worst = max(worst, distance)
            }
            return Stray(sqrt(squares / points.size), worst)
        }
    }
}

/** A closed polyline's enclosed [area], its centre and principal [axis], and its second area moments along and across that axis. */
private class RingMoments(
    val area: Float,
    val counterClockwise: Boolean,
    val center: Vec2,
    val axis: Vec2,
    val majorMoment: Float,
    val minorMoment: Float,
) {
    fun ellipse(start: Vec2): EllipseShape {
        val major = sqrt(ELLIPSE_MOMENT_DIVISOR * majorMoment)
        val minor = sqrt(ELLIPSE_MOMENT_DIVISOR * minorMoment)
        return EllipseShape(center, axis * major, minor, start, counterClockwise)
    }

    /** The capsule whose caps reach [ring]'s extent along [axis] and whose radius is the ring's mean distance from the axis along its run, or null if that leaves no run. */
    fun capsule(ring: List<Vec2>, start: Vec2): CapsuleShape? {
        val across = Vec2(-axis.y, axis.x)
        val alongs = ring.map { (it - center) dotWith axis }
        val low = alongs.min()
        val high = alongs.max()
        val widest = ring.maxOf { abs((it - center) dotWith across) }
        val middle = (low + high) / 2f
        val half = (high - low) / 2f
        val inRun = ring.filterIndexed { i, _ -> abs(alongs[i] - middle) <= half - widest }
        if (inRun.isEmpty()) return null
        val radius = inRun.sumOf { abs((it - center) dotWith across).toDouble() }.toFloat() / inRun.size
        val run = half - radius
        if (run < MIN_CAPSULE_RUN_OF_RADIUS * radius) return null
        val mid = center + axis * middle
        return CapsuleShape(mid - axis * run, mid + axis * run, radius, start, counterClockwise)
    }

    companion object {
        /** The moments of [ring], first point repeated last, by Green's theorem; null if it encloses nothing. */
        fun of(ring: List<Vec2>): RingMoments? {
            var twiceArea = 0f
            var cx = 0f
            var cy = 0f
            for (i in 1 until ring.size) {
                val a = ring[i - 1]
                val b = ring[i]
                val cross = a.x * b.y - b.x * a.y
                twiceArea += cross
                cx += (a.x + b.x) * cross
                cy += (a.y + b.y) * cross
            }
            if (twiceArea == 0f) return null
            val center = Vec2(cx / (3f * twiceArea), cy / (3f * twiceArea))
            var xx = 0f
            var xy = 0f
            var yy = 0f
            for (i in 1 until ring.size) {
                val a = ring[i - 1] - center
                val b = ring[i] - center
                val cross = a.x * b.y - b.x * a.y
                xx += (a.x * a.x + a.x * b.x + b.x * b.x) * cross
                yy += (a.y * a.y + a.y * b.y + b.y * b.y) * cross
                xy += (a.x * b.y + 2f * a.x * a.y + 2f * b.x * b.y + b.x * a.y) * cross
            }
            val area = twiceArea / 2f
            val perArea = 1f / (12f * area)
            val cxx = xx * perArea
            val cyy = yy * perArea
            val cxy = xy * perArea / 2f
            val angle = atan2(2f * cxy, cxx - cyy) / 2f
            val spread = sqrt((cxx - cyy) * (cxx - cyy) / 4f + cxy * cxy)
            val mean = (cxx + cyy) / 2f
            if (mean - spread <= 0f) return null
            return RingMoments(abs(area), area > 0f, center, Vec2(cos(angle), sin(angle)), mean + spread, mean - spread)
        }
    }
}
