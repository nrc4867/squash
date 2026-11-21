package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.LockTrim
import dev.chieppa.squash.geom.AxisSpline
import dev.chieppa.squash.geom.ClumpShape
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES = (PI / 180.0).toFloat()

/** The places a guide is read at to find where it crosses a trim. */
private const val TRIM_READS = 96

/** The least share of its length a cut lock keeps, so it never shrinks to nothing. */
private const val LEAST_KEPT = 0.04f

/** A blunt end's cap, as a share of the lock's thickness, and the shortest and longest share of its length that takes. */
private const val BLUNT_CAP = 0.3f
private const val SHORTEST_CAP = 0.02f
private const val LONGEST_CAP = 0.15f

/** A trim as a plane in body space: through [point], facing [normal]; a [blunt] cut ends a lock square. */
data class TrimPlane(val point: Vec3, val normal: Vec3, val blunt: Boolean) {
    /** How far [p] lies on the side [normal] faces. */
    fun side(p: Vec3): Float = (p - point) dot normal

    companion object {
        /** [trim] over [scalp]: level with its middle raised [LockTrim.level] of the head's reach, its front tilted up [LockTrim.tilt]. */
        fun of(trim: LockTrim, scalp: Scalp): TrimPlane {
            val reach = headReach(scalp)
            val t = trim.tilt * DEGREES
            return TrimPlane(scalp.middle + Vec3(0f, trim.level * reach, 0f), Vec3(-sin(t), cos(t), 0f), trim.blunt)
        }
    }
}

/** A guide cut to a trim: the [guide] kept, and the [share] of the grown guide's length it keeps. */
class TrimmedGuide(val guide: List<Vec3>, val share: Float)

/**
 * [guide] cut where it first crosses [plane] from the side its root lies on, spaced evenly again in as many points; null where it never
 * crosses it, so the lock keeps its whole length.
 */
fun trimmed(guide: List<Vec3>, plane: TrimPlane): TrimmedGuide? {
    if (guide.size < 2) return null
    val axis = AxisSpline(guide)
    val reads = List(TRIM_READS + 1) { axis.pointAt(it.toFloat() / TRIM_READS) }
    val rootSide = plane.side(reads.first())
    if (rootSide == 0f) return null
    var total = 0f
    for (k in 1 until reads.size) total += (reads[k] - reads[k - 1]).length
    var kept = 0f
    for (k in 1 until reads.size) {
        val before = plane.side(reads[k - 1])
        val after = plane.side(reads[k])
        val step = (reads[k] - reads[k - 1]).length
        if ((after > 0f) != (rootSide > 0f)) {
            val f = (before / (before - after)).coerceIn(0f, 1f)
            val cut = reads[k - 1] + (reads[k] - reads[k - 1]) * f
            val share = ((kept + step * f) / total.coerceAtLeast(1e-6f))
            if (share < LEAST_KEPT) return TrimmedGuide(evenly(reads.subList(0, 2), guide.size), LEAST_KEPT)
            return TrimmedGuide(evenly(reads.subList(0, k) + cut, guide.size.coerceAtLeast(3)), share)
        }
        kept += step
    }
    return null
}

/**
 * [shape], a lock's shape grown whole, ended square where a trim keeps [share] of it: as wide as the whole lock was there, closing over a
 * short round cap.
 */
fun bluntAt(shape: ClumpShape, share: Float, length: Float): ClumpShape = shape.copy(
    taper = (1f + (shape.taper - 1f) * share).coerceIn(shape.taper, 1f),
    tipRound = 1f,
    tipReach = (shape.thickness * BLUNT_CAP / length.coerceAtLeast(1e-4f)).coerceIn(SHORTEST_CAP, LONGEST_CAP),
    points = 1,
    notch = 0f,
)
