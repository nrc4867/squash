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

/**
 * One side of a membrane, read from its root (reach 0) to where its free edge meets it (reach 1): the ring there, the u a vertex there
 * rides at on the patch [name], and the widest half height over the stretch.
 */
interface MembraneRail {
    val name: String

    /** The widest half height of its rings over its stretch. */
    val halfHeight: Float

    fun ringAt(reach: Float): RailRing

    /** The u a vertex at [reach] rides at on [name]. */
    fun uAt(reach: Float): Float
}

/** A swept patch as a membrane's rail: the stretch of its u from its root at [from] to its edge at [to]. */
fun MembraneRail(patch: Patch, from: Float, to: Float): MembraneRail = PatchRail(patch, from, to)

/**
 * A straight rail named [name] from [root] to [tip], its up [up], its half height [rootHalf] at the root narrowing straight to [tipHalf],
 * its u running from [fromU] to [toU]: a digit's axis.
 */
class LineRail(
    override val name: String,
    private val root: Vec3,
    private val tip: Vec3,
    private val up: Vec3,
    private val rootHalf: Float,
    private val tipHalf: Float,
    private val fromU: Float,
    private val toU: Float,
) : MembraneRail {
    override val halfHeight: Float get() = maxOf(rootHalf, tipHalf)

    override fun ringAt(reach: Float) = RailRing(root + (tip - root) * reach, up, rootHalf + (tipHalf - rootHalf) * reach)

    override fun uAt(reach: Float) = fromU + (toU - fromU) * reach
}

/** A rail read through [ring] and [u], named [name], [halfHeight] at its widest: a chain of edges over several parts. */
class ReadRail(override val name: String, override val halfHeight: Float, private val ring: (Float) -> RailRing, private val u: (Float) -> Float) : MembraneRail {
    override fun ringAt(reach: Float) = ring(reach)

    override fun uAt(reach: Float) = u(reach)
}

/**
 * How a membrane is shaped: [thickness] of its rails' half height half way between them, its free edge dipping [sag] of its reach toward
 * its middle, and its middle standing [billow] of the gap between its rails off the plane between them, nothing at its root and most at
 * its free edge: toward [down] where the sheet lies more level than [STEEP], else toward [away].
 */
data class MembraneShape(val thickness: Float, val sag: Float, val billow: Float = 0f, val down: Vec3? = null, val away: Vec3? = null) {
    companion object {
        /** How far a sheet's up may turn from [down], as the cosine between them, before it billows toward [away] instead. */
        const val STEEP = 0.4f
    }
}

/** A rail's ring at one reach: its middle, the way its up points and its half height. */
class RailRing(val middle: Vec3, val up: Vec3, val half: Float)

/** A swept patch's stretch of u from [from] to [to], read between its rings. */
private class PatchRail(val patch: Patch, val from: Float, val to: Float) : MembraneRail {
    init {
        requireNotNull(patch.slices) { "A membrane's rail ${patch.name} has no slices" }
    }

    override val name: String get() = patch.name

    override fun uAt(reach: Float): Float = from + (to - from) * reach

    override val halfHeight: Float by lazy {
        val slices = requireNotNull(patch.slices)
        val low = minOf(from, to)
        val high = maxOf(from, to)
        slices.filter { it.u in low..high }.maxOfOrNull { it.height / 2f } ?: (ringOf(slices, (low + high) / 2f).height / 2f)
    }

    override fun ringAt(reach: Float): RailRing {
        val slice = ringOf(requireNotNull(patch.slices), uAt(reach))
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
        val firstHalf = first.halfHeight
        val secondHalf = second.halfHeight
        val web = shape.thickness * minOf(firstHalf, secondHalf)
        val gap = (second.ringAt(MIDDLE).middle - first.ringAt(MIDDLE).middle).length
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
                val uA = first.uAt(reach)
                val uB = second.uAt(reach)
                val a = first.ringAt(reach)
                val b = second.ringAt(reach)
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
                    a.middle + between * s + up * (if (onFirstFace == facing) thick else -thick) + billowAt(shape, up, width, s, t)
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
        return Patch(name, rings, segments, closedV = true, slices = null, points = points, spanned = Spanned(first.name, second.name, firstUs, secondUs, shares))
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

    /** How far the membrane's middle stands off the plane between its rails at [s] across and [t] toward its free edge, along [up] turned toward the shape's down. */
    private fun billowAt(shape: MembraneShape, up: Vec3, width: Float, s: Float, t: Float): Vec3 {
        if (shape.billow == 0f) return Vec3.ZERO
        val down = shape.down
        val lean = if (down != null && kotlin.math.abs(up dot down) >= MembraneShape.STEEP) down else shape.away ?: down
        val toward = lean?.let { if ((up dot it) < 0f) up * -1f else up } ?: up
        val outward = 1f - (1f - t) * (1f - t)
        return toward * (shape.billow * width * sin(PI.toFloat() * s) * outward)
    }

    /**
     * Whether the first face lies on the rails' up side for the membrane to face out: true when the rails run toward their edge, the
     * first rail to the second and their up turn the way the build's columns wind.
     */
    private fun facing(first: MembraneRail, second: MembraneRail): Boolean {
        val root = first.ringAt(0f).middle
        val edge = first.ringAt(1f).middle
        val middle = first.ringAt(MIDDLE)
        val across = second.ringAt(MIDDLE).middle - middle.middle
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
}
