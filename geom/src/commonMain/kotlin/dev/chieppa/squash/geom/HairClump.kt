package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The share of a clump's length its root flares over. */
private const val FLARE_REACH = 0.18f

/** Where along a clump its swell peaks, as the share of its length past which half of [ClumpShape.swell] is spent. */
private const val SWELL_EXPONENT = 0.6f

/** How round a clump's tip is in its thickness at the least, so a pointed tip still closes as a rounded edge seen side on. */
private const val ROUND_THICKNESS = 0.5f

/** How much of its taper a clump's thickness takes, so a flat lock stays full toward its tip. */
private const val THICKNESS_TAPER = 0.6f

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
private const val TWO_PI = (2.0 * PI).toFloat()

/** How near its tip's end, as a share of the tip, a clump counts as closed, so its last ring is a point whatever the rounding. */
private const val CLOSED = 1e-4f

/** The share of a lobed clump's length over which its tip closes to a point after its lobes. */
private const val LOBED_END = 0.04f

/**
 * How a clump of hair is shaped along its axis, in body units at its root: [width] across and [thickness] through, its rings ellipses.
 * Its width swells by [swell] toward its first third and tapers to [taper] of the root's toward its tip, which closes over the last
 * [tipReach] of its length, pointed at [tipRound] 0 and round at 1. With [points] above 1 the tip keeps its width and ends in that many
 * lobes, cut back [notch] of the length between them. It turns [twist] degrees about itself over its length and waves sideways [waves]
 * times, [wave] of its width either way, growing toward the tip. Its root flares [flare] wider, so it rises out of what it sits on, and
 * its section curls toward its top by [cup], as a ribbon curving round the head.
 */
data class ClumpShape(
    val width: Float,
    val thickness: Float,
    val taper: Float = 0.35f,
    val swell: Float = 0.15f,
    val tipRound: Float = 0f,
    val tipReach: Float = 0.3f,
    val points: Int = 1,
    val notch: Float = 0f,
    val twist: Float = 0f,
    val wave: Float = 0f,
    val waves: Float = 1f,
    val flare: Float = 0.3f,
    val cup: Float = 0f,
) {
    /** How far into its tip [t] lies, 0 before the tip starts closing and 1 at the end. */
    fun intoTip(t: Float): Float = ((t - (1f - tipReach)) / tipReach.coerceAtLeast(1e-3f)).coerceIn(0f, 1f)

    /** The clump's full width at [t] of its length, before the axis's bend holds it. */
    fun widthAt(t: Float): Float = width * body(t) * flareAt(t) * if (points > 1) lobedEnd(t) else closing(intoTip(t), tipRound)

    /**
     * A lobed tip keeps its width to its very end, then closes over the last [LOBED_END] of the length, so its last ring is a point; its
     * lobes' cut back eases out there too, so that point stays one.
     */
    internal fun lobedEnd(t: Float): Float = ((1f - t) / LOBED_END).coerceIn(0f, 1f).let { sqrt(it) }

    /** The clump's full thickness at [t] of its length, before the axis's bend holds it. */
    fun thicknessAt(t: Float): Float {
        val taperShare = 1f + (taper - 1f) * t * THICKNESS_TAPER
        return thickness * swellAt(t) * taperShare * sqrt(flareAt(t)) * closing(intoTip(t), max(tipRound, ROUND_THICKNESS))
    }

    /** How far the clump's middle sways sideways at [t], in body units. */
    fun swayAt(t: Float): Float = if (wave == 0f) 0f else wave * width * sin(TWO_PI * waves * t) * t

    /** How far the clump has turned about itself at [t], in radians. */
    fun turnAt(t: Float): Float = twist * DEGREES_TO_RADIANS * t

    /**
     * How far back along the clump a point of its last rings lies at [x] across it, -1 to 1, as a share of its length: none at a lobe's
     * middle and [notch] between two lobes, pointed or round as the tip is, growing over the tip.
     */
    fun cutBack(t: Float, x: Float): Float {
        if (points < 2 || notch <= 0f) return 0f
        val s = intoTip(t)
        if (s <= 0f) return 0f
        val phase = points * (x.coerceIn(-1f, 1f) + 1f) / 2f
        val f = phase - floor(phase)
        val pointed = abs(2f * f - 1f)
        val round = (1f + cos(TWO_PI * f)) / 2f
        return notch * (pointed + (round - pointed) * tipRound) * s
    }

    private fun swellAt(t: Float): Float = 1f + swell * sin(PI.toFloat() * t.coerceIn(0f, 1f).pow(SWELL_EXPONENT))

    private fun body(t: Float): Float = swellAt(t) * (1f + (taper - 1f) * t)

    private fun flareAt(t: Float): Float {
        val a = (t / FLARE_REACH).coerceIn(0f, 1f)
        return 1f + flare * (1f - a * a * (3f - 2f * a))
    }

    private fun closing(s: Float, round: Float): Float {
        if (s >= 1f - CLOSED) return 0f
        val pointed = 1f - s
        val rounded = sqrt(max(0f, 1f - s * s))
        return pointed + (rounded - pointed) * round
    }
}

/** Builds a clump of hair's rings and the patch lofted from them; see [ClumpShape]. */
object ClumpBuilder {
    /** The clump [shape] along [spec]'s axis: its rings carried along it without twisting but by the clump's own turn, held within its bends. */
    fun slices(spec: TubeSpec, shape: ClumpShape): List<Slice> {
        val us = SliceBuilder.ringUs(spec)
        val frames = SliceBuilder.frames(spec.axis, us, spec.up)
        val span = (spec.uEnd - spec.uStart).coerceAtLeast(1e-6f)
        val ts = us.map { ((it - spec.uStart) / span).coerceIn(0f, 1f) }
        val widths = FloatArray(us.size) { shape.widthAt(ts[it]) }
        val thicknesses = FloatArray(us.size) { shape.thicknessAt(ts[it]) }
        val extents = FloatArray(us.size) { max(widths[it], thicknesses[it]) }
        val held = SliceBuilder.heldToBends(extents, SliceBuilder.bendRadii(frames), 1f)
        val section = spec.section
        return us.indices.map { ring ->
            val t = ts[ring]
            val share = if (extents[ring] > 0f) min(1f, held[ring] / extents[ring]) else 1f
            val onSocket = ring == 0 && spec.startClosure == EndClosure.OPEN
            val base = SliceBuilder.placed(frames[ring], 0f, spec.socketNormal.takeIf { onSocket })
            val frame = turned(base, shape.turnAt(t), shape.swayAt(t))
            Slice(us[ring], frame, thicknesses[ring] * share, widths[ring] * share, section, frame.rotation)
        }
    }

    /** The clump's ring at [u], as [slices] builds it there. */
    fun ringAt(spec: TubeSpec, shape: ClumpShape, u: Float): Slice {
        val span = (spec.uEnd - spec.uStart).coerceAtLeast(1e-6f)
        val t = ((u - spec.uStart) / span).coerceIn(0f, 1f)
        val frame = turned(SliceBuilder.frames(spec.axis, SliceBuilder.ringUs(spec).filter { it < u } + u, spec.up).last(), shape.turnAt(t), shape.swayAt(t))
        return Slice(u, frame, shape.thicknessAt(t), shape.widthAt(t), spec.section, frame.rotation)
    }

    /** The clump's patch: its rings lofted, with the points of its last rings cut back between its tip's lobes. */
    fun patch(spec: TubeSpec, shape: ClumpShape): Patch {
        val slices = slices(spec, shape)
        val lofted = SliceBuilder.loft(spec.name, slices, spec.segments)
        if (shape.points < 2 || shape.notch <= 0f) return lofted
        val length = slices.zipWithNext { a, b -> (b.frame.center - a.frame.center).length }.sum()
        val span = (spec.uEnd - spec.uStart).coerceAtLeast(1e-6f)
        val across = spec.section.sample(spec.segments).map { it.x }
        val points = lofted.points.copyOf()
        for ((ring, slice) in slices.withIndex()) {
            val t = (slice.u - spec.uStart) / span
            if (shape.intoTip(t) <= 0f) continue
            val tangent = slice.frame.tangent
            for (column in 0 until spec.segments) {
                val back = shape.cutBack(t, across[column]) * length * shape.lobedEnd(t)
                if (back == 0f) continue
                val i = (ring * spec.segments + column) * Patch.COMPONENTS
                points[i] -= tangent.x * back
                points[i + 1] -= tangent.y * back
                points[i + 2] -= tangent.z * back
            }
        }
        return Patch(lofted.name, lofted.nu, lofted.nv, lofted.closedV, lofted.slices, points, proxyNormals = lofted.proxyNormals)
    }

    /** [frame] turned [turn] radians about its tangent and moved [sway] toward its side. */
    private fun turned(frame: Frame, turn: Float, sway: Float): Frame {
        val up = if (turn == 0f) frame.up else (frame.up * cos(turn) + frame.side * sin(turn)).normalized()
        val turnedFrame = Frame(frame.center, frame.tangent, up)
        return if (sway == 0f) turnedFrame else Frame(frame.center + turnedFrame.side * sway, frame.tangent, up)
    }
}
