package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** How near a rail's own height the membrane swells inside it, as a share of that height. */
private const val FILLET = 0.85f

/** Where the swell toward a rail starts to ease off and where it is gone, in that rail's half heights from its middle. */
private const val FILLET_HELD = 0.2f
private const val FILLET_GONE = 1.4f

/** The share of the membrane's rings, at its free edge, over which it rounds off to its lip. */
private const val EDGE_REACH = 0.3f

/** How thick the lip at the free edge stays, as a share of the membrane's thickness there. */
private const val LIP = 0.25f

private const val TINY = 1e-6f

/** Half way from the rails' roots to the free edge, where each end of the membrane closes to a point. */
private const val MIDDLE = 0.5f

/** The least a rail's half height counts for where its root rounds off, as a share of its widest. */
private const val ROUND_FLOOR = 0.6f

/** How far from a rail's middle the ring next to it sits, in that rail's half heights. */
private const val END_RING = 0.35f

/** One side of a membrane: a swept patch and the stretch of its u the membrane runs along, from its root at [from] to its edge at [to]. */
class MembraneRail(val patch: Patch, val from: Float, val to: Float) {
    init {
        requireNotNull(patch.slices) { "A membrane's rail ${patch.name} has no slices" }
    }
}

/** How a membrane is shaped: [thickness] of its rails' half height half way between them, its free edge dipping [sag] of its reach toward its middle. */
data class MembraneShape(val thickness: Float, val sag: Float)

/** A rail's ring at one u: its middle, the way its up points and its half height. */
private class RailRing(val middle: Vec3, val up: Vec3, val half: Float)

/**
 * A surface spanned between two rails, a web between two digits. Its rings run across it from the first rail to the second, the
 * first and last a point inside each rail; each goes along one face from the rails' roots to the free edge, round the edge and back
 * along the other face, so it is closed in v and its root lies inside whatever the rails grow from. It is thin half way between the
 * rails and swells toward each, leaving it with a fillet, and its free edge rounds off to a thin lip, dipping toward its middle.
 * Every vertex rides both rails ([Spanned]), so bones that move the rails move it.
 */
object MembraneBuilder {
    const val MIN_RINGS = 4
    const val MIN_SEGMENTS = 6

    fun membrane(name: String, first: MembraneRail, second: MembraneRail, rings: Int, segments: Int, shape: MembraneShape): Patch {
        require(rings >= MIN_RINGS && segments >= MIN_SEGMENTS && segments % 2 == 0) { "$name: a membrane needs $MIN_RINGS rings and an even $MIN_SEGMENTS segments" }
        val half = segments / 2
        val firstHalf = first.halfHeight()
        val secondHalf = second.halfHeight()
        val web = shape.thickness * minOf(firstHalf, secondHalf)
        val gap = (second.ringAt(second.at(MIDDLE)).middle - first.ringAt(first.at(MIDDLE)).middle).length
        val across = acrossShares(rings, gap, firstHalf, secondHalf)
        val facing = facing(first, second)
        val count = rings * segments
        val points = FloatArray(count * Patch.COMPONENTS)
        val firstUs = FloatArray(count)
        val secondUs = FloatArray(count)
        val shares = FloatArray(count)
        for (ring in 0 until rings) {
            val s = across[ring]
            val pole = ring == 0 || ring == rings - 1
            for (column in 0 until segments) {
                val onFirstFace = column < half
                val step = (column % half).toFloat() / (half - 1)
                val t = if (pole) MIDDLE else if (onFirstFace) step else 1f - step
                val reach = t * (1f - shape.sag * sin(PI.toFloat() * s))
                val uA = first.at(reach)
                val uB = second.at(reach)
                val a = first.ringAt(uA)
                val b = second.ringAt(uB)
                val between = b.middle - a.middle
                val point = if (pole) {
                    if (ring == 0) a.middle else b.middle
                } else {
                    val up = normalTo(a.up + b.up, between)
                    val width = between.length
                    val ownA = maxOf(a.half, ROUND_FLOOR * firstHalf, TINY)
                    val ownB = maxOf(b.half, ROUND_FLOOR * secondHalf, TINY)
                    val thin = minOf(web, FILLET * minOf(ownA, ownB))
                    val swell = (FILLET * ownA - thin).coerceAtLeast(0f) * fillet(s * width / ownA) + (FILLET * ownB - thin).coerceAtLeast(0f) * fillet((1f - s) * width / ownB)
                    val thick = (thin + swell).coerceAtMost(FILLET * maxOf(ownA, ownB)) * edgeScale(t)
                    a.middle + between * s + up * (if (onFirstFace == facing) thick else -thick)
                }
                val i = ring * segments + column
                points[i * Patch.COMPONENTS] = point.x
                points[i * Patch.COMPONENTS + 1] = point.y
                points[i * Patch.COMPONENTS + 2] = point.z
                firstUs[i] = uA
                secondUs[i] = uB
                shares[i] = smoothStep(s)
            }
        }
        return Patch(name, rings, segments, closedV = true, slices = null, points = points, spanned = Spanned(first.patch.name, second.patch.name, firstUs, secondUs, shares))
    }

    /**
     * Where each ring sits across from the first rail (0) to the second (1): the rings next to the ends close enough to the rails'
     * middles that the point each end closes to stays inside its rail, the rest evenly between.
     */
    private fun acrossShares(rings: Int, gap: Float, firstHalf: Float, secondHalf: Float): FloatArray {
        val even = 1f / (rings - 1)
        val near = if (gap > TINY) minOf(even, END_RING * firstHalf / gap) else even
        val far = 1f - if (gap > TINY) minOf(even, END_RING * secondHalf / gap) else even
        return FloatArray(rings) { ring ->
            when (ring) {
                0 -> 0f
                rings - 1 -> 1f
                else -> near + (far - near) * (ring - 1) / (rings - 3).coerceAtLeast(1)
            }
        }
    }

    private fun MembraneRail.at(reach: Float): Float = from + (to - from) * reach

    /**
     * Whether the first face lies on the rails' up side for the membrane to face out: true when the rails run toward their edge, the
     * first rail to the second and their up turn the way the build's columns wind.
     */
    private fun facing(first: MembraneRail, second: MembraneRail): Boolean {
        val root = first.ringAt(first.at(0f)).middle
        val edge = first.ringAt(first.at(1f)).middle
        val middle = first.ringAt(first.at(MIDDLE))
        val across = second.ringAt(second.at(MIDDLE)).middle - middle.middle
        return ((edge - root) cross across) dot middle.up <= 0f
    }

    /** How thick the membrane is at [t] from its root toward its edge, as a share: whole, then rounding off to the lip. */
    private fun edgeScale(t: Float): Float {
        val into = (t - (1f - EDGE_REACH)) / EDGE_REACH
        if (into <= 0f) return 1f
        return maxOf(LIP, sqrt(1f - into * into))
    }

    /** How much of its swell toward a rail the membrane has at [x] of that rail's half heights from its middle. */
    private fun fillet(x: Float): Float = 1f - smoothStep((x - FILLET_HELD) / (FILLET_GONE - FILLET_HELD))

    private fun smoothStep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /** [direction] with its part along [across] taken out, as a unit vector; any direction square to [across] if nothing is left. */
    private fun normalTo(direction: Vec3, across: Vec3): Vec3 {
        if (across.length < TINY) return direction.normalized()
        val along = across.normalized()
        val left = direction - along * (direction dot along)
        if (left.length > TINY) return left.normalized()
        return PartAxes.of(along).up
    }

    /** The widest half height of this rail's rings over its stretch. */
    private fun MembraneRail.halfHeight(): Float {
        val slices = requireNotNull(patch.slices)
        val low = minOf(from, to)
        val high = maxOf(from, to)
        return slices.filter { it.u in low..high }.maxOfOrNull { it.height / 2f } ?: (ringOf(slices, (low + high) / 2f).let { it.height / 2f })
    }

    private fun MembraneRail.ringAt(u: Float): RailRing {
        val slices = requireNotNull(patch.slices)
        val slice = ringOf(slices, u)
        return RailRing(slice.frame.center, slice.frame.up, slice.height / 2f)
    }

    /** The ring of [slices] at [u], its middle, up and height read straight between the rings either side. */
    private fun ringOf(slices: List<Slice>, u: Float): Slice {
        val next = slices.indexOfFirst { it.u >= u }
        if (next <= 0) return slices[if (next == 0) 0 else slices.lastIndex]
        val a = slices[next - 1]
        val b = slices[next]
        val t = if (b.u > a.u) (u - a.u) / (b.u - a.u) else 0f
        val frame = Frame(a.frame.center + (b.frame.center - a.frame.center) * t, a.frame.tangent, (a.frame.up + (b.frame.up - a.frame.up) * t).normalized())
        return Slice(u, frame, a.height + (b.height - a.height) * t, a.width + (b.width - a.width) * t, a.section, a.proxyNormalFrame)
    }
}
