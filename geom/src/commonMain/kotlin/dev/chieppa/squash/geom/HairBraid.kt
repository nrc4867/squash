package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2.0 * PI).toFloat()

/** The share of a braid's length its loose tail takes past the tie. */
const val BRAID_TAIL = 0.16f

/** How far along a braid, as a share of its width, one strand's full cycle runs: three crossings, one plait each side and back. */
private const val PLAIT_PITCH = 2.6f

/** How far each strand swings to either side of the braid's middle, and how far in front and behind, as shares of its width and thickness. */
private const val SWING = 0.32f
private const val DEPTH = 0.4f

/** A strand's width and thickness, as shares of the braid's. */
private const val STRAND_WIDTH = 0.5f
private const val STRAND_THICKNESS = 0.5f

/** The share of the braid's length before the tie over which its strands draw in together under it. */
private const val GATHER = 0.08f

/** The share of the braid's length at its root over which its strands rise out of the scalp to their full size. */
private const val ROOT_RISE = 0.04f

/** The tie's radius and thickness, as shares of the braid's width where it is tied. */
private const val TIE_RADIUS = 0.36f
private const val TIE_THICKNESS = 0.3f

/** The tail's width at the tie, as a share of the braid's there, and how much of it it keeps toward its tip. */
private const val TAIL_WIDTH = 0.8f
private const val TAIL_TAPER = 0.25f

/** The rings along each strand per plait, the fewest, and the most. */
private const val RINGS_PER_PLAIT = 6
private const val LEAST_STRAND_RINGS = 12
private const val MOST_STRAND_RINGS = 96

/** One braid as built: its three [strands], the [tie] round its end and the loose [tail] past it, each a patch of its own. */
class BraidPatches(val strands: List<Patch>, val tie: Patch, val tail: Patch) {
    val all: List<Patch> get() = strands + tie + tail
}

/**
 * Builds a braid along a lock's guide: three strands, each swinging side to side across the braid's middle and passing in front on its
 * way in and behind on its way out, a third of a cycle apart, so each in turn crosses over the middle one; tapering from [width] across
 * and [thickness] through at its root to [taper] of that at its tie, where the strands draw in under a band, and a short loose tail past
 * it. Every ring's u is its place along the whole lock, so a chain along the guide carries strands, tie and tail alike.
 */
object BraidBuilder {
    fun patches(name: String, guide: List<Vec3>, up: Vec3, width: Float, thickness: Float, taper: Float, segments: Int): BraidPatches {
        require(guide.size >= 2) { "A braid's guide needs a root and a tip" }
        val axis = AxisSpline(guide)
        val length = LockBuilder.lengthOf(guide)
        val tied = 1f - BRAID_TAIL
        val plaits = (length * tied / (width * PLAIT_PITCH).coerceAtLeast(1e-4f)).coerceAtLeast(1f)
        val rings = (plaits * RINGS_PER_PLAIT).toInt().coerceIn(LEAST_STRAND_RINGS, MOST_STRAND_RINGS)
        val us = List(rings) { tied * it / (rings - 1) }
        val frames = SliceBuilder.frames(axis, us, up)
        fun widthAt(u: Float) = width * (1f + (taper - 1f) * u / tied)
        fun thicknessAt(u: Float) = thickness * (1f + (taper - 1f) * u / tied)
        val section = SectionProfile.CIRCLE.spline(segments)
        val strands = List(3) { strand ->
            val phase = TWO_PI * strand / 3f
            val centers = us.mapIndexed { ring, u ->
                val frame = frames[ring]
                val angle = TWO_PI * plaits * u / tied + phase
                val drawn = 1f - smooth((u - (tied - GATHER)) / GATHER) * 0.85f
                val across = widthAt(u) * SWING * sin(angle) * drawn
                val depth = -thicknessAt(u) * DEPTH * sin(2f * angle) * drawn
                frame.center + frame.side * across + frame.up * depth
            }
            val slices = us.mapIndexed { ring, u ->
                val frame = frames[ring]
                val ahead = centers[(ring + 1).coerceAtMost(rings - 1)] - centers[(ring - 1).coerceAtLeast(0)]
                val tangent = if (ahead.length > 1e-6f) ahead.normalized() else frame.tangent
                val strandUp = (frame.up - tangent * (frame.up dot tangent)).let { if (it.length > 1e-6f) it.normalized() else frame.up }
                val size = endScale(ring, rings, u)
                val ring3 = Frame(centers[ring], tangent, strandUp)
                Slice(u, ring3, thicknessAt(u) * STRAND_THICKNESS * size, widthAt(u) * STRAND_WIDTH * size, section, ring3.rotation)
            }
            val lofted = SliceBuilder.loft(if (strand == 0) name else "$name.s$strand", slices, segments)
            closedEnds(lofted, centers.first(), centers.last())
        }
        val tieAt = axis.pointAt(tied)
        val tieWay = axis.tangentAt(tied)
        val tie = BandBuilder.patch("$name.tie", tieAt, tieWay, widthAt(tied) * TIE_RADIUS, widthAt(tied) * TIE_THICKNESS).let { band ->
            Patch(band.name, band.nu, band.nv, band.closedV, null, band.points, boneU = tied, us = FloatArray(band.nu) { tied * (0.98f + 0.02f * it / (band.nu - 1)) })
        }
        val tail = tailPatch("$name.tail", axis, up, tied, widthAt(tied), thicknessAt(tied), segments)
        return BraidPatches(strands, tie, tail)
    }

    /** The loose tail from just inside the tie to the tip, its rings' us running on from the tie's. */
    private fun tailPatch(name: String, axis: AxisSpline, up: Vec3, from: Float, width: Float, thickness: Float, segments: Int): Patch {
        val start = from - GATHER * 0.25f
        val points = List(6) { axis.pointAt(start + (1f - start) * it / 5f) }
        val shape = ClumpShape(width * TAIL_WIDTH, thickness * TAIL_WIDTH, taper = TAIL_TAPER, swell = 0.35f, tipReach = 0.45f, flare = 0f)
        val patch = LockBuilder.patch(name, points, up, shape, LockProfile.CLUMP, 12, segments)
        val us = FloatArray(patch.nu) { start + (1f - start) * patch.ringUs[it] }
        return Patch(patch.name, patch.nu, patch.nv, patch.closedV, null, patch.points, proxyNormals = patch.proxyNormals, us = us)
    }

    /** A strand's size at [ring] of [rings], at [u]: rising out of the scalp at its root, closing to a point at each end. */
    private fun endScale(ring: Int, rings: Int, u: Float): Float {
        if (ring == 0 || ring == rings - 1) return 0f
        return 0.35f + 0.65f * sqrt(smooth(u / ROOT_RISE))
    }

    /** [patch] with its first and last rings drawn to the points [first] and [last], so each end closes. */
    private fun closedEnds(patch: Patch, first: Vec3, last: Vec3): Patch {
        val points = patch.points.copyOf()
        for (column in 0 until patch.nv) {
            for ((ring, at) in listOf(0 to first, patch.nu - 1 to last)) {
                val i = patch.index(ring, column) * Patch.COMPONENTS
                points[i] = at.x
                points[i + 1] = at.y
                points[i + 2] = at.z
            }
        }
        return patch.withPoints(points, patch.proxyNormals)
    }

    private fun smooth(x: Float): Float {
        val a = x.coerceIn(0f, 1f)
        return a * a * (3f - 2f * a)
    }
}
