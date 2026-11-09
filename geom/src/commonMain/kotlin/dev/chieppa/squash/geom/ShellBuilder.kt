package dev.chieppa.squash.geom

import kotlin.math.abs

/** A body ring smaller than this across both ways is a pole, and the shell over it closes there too. */
private const val POLE_SIZE = 1e-4f

/**
 * A shell lofted over a swept patch: [patch], open at both ends and closed round, and for each of its rings the ring of the body
 * patch it was grown from, nearest in u, so a shell vertex can read the body vertex under it at the same v.
 */
class Shell(val patch: Patch, val bodyRings: IntArray)

/** How a shell ring is pushed out past its ease: a height or width it must reach and how far its centre moves to meet it. */
data class ShellReach(val height: Float? = null, val width: Float? = null, val up: Float = 0f, val side: Float = 0f)

/** Builds shells over a body's slices. See HLD: data-model, Garments. */
object ShellBuilder {
    /**
     * A shell named [name] over [body] from [from] to [to] in u in [rings] rings evenly spaced, each the body's slice there grown by
     * [ease] on every side. Past either end of [body] the shell carries on along its end slice's frame at the size of the ring nearest
     * that end that is not a pole. [reach] may take a ring further out, never further in than the ease. A pole of the body stays a pole.
     * [body] must be swept.
     */
    fun shell(name: String, body: Patch, from: Float, to: Float, rings: Int, ease: Float, reach: (Slice) -> ShellReach = { ShellReach() }): Shell {
        val slices = requireNotNull(body.slices) { "Patch ${body.name} is not swept" }
        require(rings >= 2) { "A shell needs two rings, not $rings" }
        val perU = body.lengthPerU()
        val bodyRings = IntArray(rings)
        val grown = List(rings) { i ->
            val u = from + (to - from) * i / (rings - 1)
            bodyRings[i] = nearestRing(slices, u)
            val under = sliceAt(slices, u, perU)
            if (under.isPole) under else grownBy(under, ease, reach(under.copy(height = under.height + 2f * ease, width = under.width + 2f * ease)))
        }
        return Shell(SliceBuilder.loft(name, grown, body.nv), bodyRings)
    }

    private fun grownBy(slice: Slice, ease: Float, reach: ShellReach): Slice {
        val eased = slice.copy(height = slice.height + 2f * ease, width = slice.width + 2f * ease)
        val height = maxOf(eased.height, reach.height ?: 0f)
        val width = maxOf(eased.width, reach.width ?: 0f)
        val up = if (height > eased.height) reach.up else 0f
        val side = if (width > eased.width) reach.side else 0f
        val frame = eased.frame
        val center = frame.center + frame.up * up + frame.side * side
        return eased.copy(frame = frame.copy(center = center), height = height, width = width)
    }

    /** The body's slice at [u]: between its two neighbours, or carried on from the end slice past either end. */
    private fun sliceAt(slices: List<Slice>, u: Float, perU: Float): Slice {
        val first = slices.first()
        val last = slices.last()
        if (u <= first.u) return carried(first, slices.firstOrNull { !it.isPole } ?: first, u, perU)
        if (u >= last.u) return carried(last, slices.lastOrNull { !it.isPole } ?: last, u, perU)
        val next = slices.indexOfFirst { it.u >= u }
        val a = slices[next - 1]
        val b = slices[next]
        val t = if (b.u > a.u) (u - a.u) / (b.u - a.u) else 0f
        val frame = Frame(
            a.frame.center + (b.frame.center - a.frame.center) * t,
            (a.frame.tangent + (b.frame.tangent - a.frame.tangent) * t).normalized(),
            (a.frame.up + (b.frame.up - a.frame.up) * t).normalized(),
        )
        val near = if (t < 0.5f) a else b
        return Slice(u, frame, a.height + (b.height - a.height) * t, a.width + (b.width - a.width) * t, near.section, near.proxyNormalFrame)
    }

    /** [end]'s frame carried on to [u], at the size and section of [sized], the ring nearest the end that is not a pole. */
    private fun carried(end: Slice, sized: Slice, u: Float, perU: Float): Slice {
        val frame = end.frame.copy(center = end.frame.center + end.frame.tangent * ((u - end.u) * perU))
        return sized.copy(u = u, frame = frame)
    }

    private val Slice.isPole: Boolean get() = height < POLE_SIZE && width < POLE_SIZE

    private fun nearestRing(slices: List<Slice>, u: Float): Int = slices.indices.minBy { abs(slices[it].u - u) }
}
