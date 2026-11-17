package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import kotlin.math.hypot
import kotlin.math.sin

/** How many samples a hand stroke lays between two of its shape's points, as a mouse reports a steady drag. */
private const val SAMPLES_PER_SPAN = 14

/** The hand's wobble across the stroke as a share of its length, and its two rates along it, per sample. */
private const val WOBBLE_OF_LENGTH = 0.004f
private const val WOBBLE_RATE = 0.37f
private const val WOBBLE_FAST_RATE = 1.9f
private const val WOBBLE_FAST_SHARE = 0.4f

/**
 * A freehand stroke as a hand draws one in a view's plane: a smooth line through [shape], points given along the part from its
 * socket (x 0 to 1) and across it, scaled by [length], with a small steady wobble across it. Its ends start just behind the
 * socket plane, as a drawn limb's do.
 */
class HandStroke(val name: String, val view: View, val length: Float, private val shape: List<Pair<Float, Float>>) {
    /** The same stroke drawn [length] long. */
    fun scaled(length: Float) = HandStroke(name, view, length, shape)

    fun points(): List<CurvePoint> {
        val through = shape.map { (x, y) -> CurvePoint(x * length, y * length) }
        val smooth = (0 until through.lastIndex).flatMap { i ->
            val p0 = through[(i - 1).coerceAtLeast(0)]
            val p1 = through[i]
            val p2 = through[i + 1]
            val p3 = through[(i + 2).coerceAtMost(through.lastIndex)]
            List(SAMPLES_PER_SPAN) { k -> catmullRom(p0, p1, p2, p3, k.toFloat() / SAMPLES_PER_SPAN) }
        } + through.last()
        val amplitude = WOBBLE_OF_LENGTH * length
        return smooth.mapIndexed { i, p ->
            if (i == 0 || i == smooth.lastIndex) return@mapIndexed p
            val a = smooth[i - 1]
            val b = smooth[i + 1]
            val run = hypot(b.x - a.x, b.y - a.y).takeIf { it > 0f } ?: return@mapIndexed p
            val wobble = amplitude * (sin(i * WOBBLE_RATE) + WOBBLE_FAST_SHARE * sin(i * WOBBLE_FAST_RATE))
            CurvePoint(p.x - (b.y - a.y) / run * wobble, p.y + (b.x - a.x) / run * wobble)
        }
    }

    private fun catmullRom(p0: CurvePoint, p1: CurvePoint, p2: CurvePoint, p3: CurvePoint, t: Float): CurvePoint {
        val t2 = t * t
        val t3 = t2 * t
        fun axis(a: Float, b: Float, c: Float, d: Float) =
            0.5f * (2f * b + (c - a) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (3f * b - a - 3f * c + d) * t3)
        return CurvePoint(axis(p0.x, p1.x, p2.x, p3.x), axis(p0.y, p1.y, p2.y, p3.y))
    }
}

/** A dog's front leg from the side: thigh, a knee, a narrow shin and a round paw. */
val LEG_STROKE = HandStroke(
    "leg", View.SIDE, 0.55f,
    listOf(
        -0.02f to 0.13f, 0.2f to 0.125f, 0.4f to 0.105f, 0.55f to 0.12f, 0.75f to 0.095f, 0.88f to 0.1f, 0.97f to 0.07f,
        1f to 0f, 0.97f to -0.06f, 0.85f to -0.08f, 0.6f to -0.075f, 0.4f to -0.1f, 0.2f to -0.12f, -0.02f to -0.13f,
    ),
)

/** A tall ear from the side, wide at its root and narrowing to its tip. */
val EAR_STROKE = HandStroke(
    "ear", View.SIDE, 0.36f,
    listOf(-0.02f to 0.2f, 0.3f to 0.22f, 0.6f to 0.15f, 0.85f to 0.06f, 1f to 0f, 0.85f to -0.05f, 0.6f to -0.13f, 0.3f to -0.2f, -0.02f to -0.2f),
)

/** A tail from the side, curling up to a round tip. */
val TAIL_STROKE = HandStroke(
    "tail", View.SIDE, 0.55f,
    listOf(
        -0.02f to 0.08f, 0.25f to 0.12f, 0.5f to 0.18f, 0.75f to 0.26f, 0.92f to 0.3f, 1f to 0.26f, 0.95f to 0.16f,
        0.75f to 0.12f, 0.5f to 0.05f, 0.25f to -0.02f, -0.02f to -0.08f,
    ),
)
