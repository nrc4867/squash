package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.ClosedSpline
import dev.chieppa.squash.geom.Frame
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Points across a plane, each way, where a section is looked for; a second pass as fine looks again over what the first found. */
private const val SEARCH_GRID = 14

/** Steps from a ray's far end in toward its start in which the outermost crossing is first bracketed. */
private const val MARCH_STEPS = 64

/** Halvings of the bracket round the outermost crossing. */
private const val HALVINGS = 14

/** Halvings of the reach along the axis where the section first or last has any inside. */
private const val EXTENT_HALVINGS = 18

/** A section no wider than this share of the field's box across is a pole. */
private const val POLE_SHARE = 1e-3f

/** The gradient's difference step as a share of the field's box across. */
private const val GRADIENT_SHARE = 1e-3f

/** How a drawn Side or Top resizes the ring at a u: its height, width and, where Side is drawn, its middle. Null keeps the field's. */
data class DrawnRing(val height: Float?, val width: Float?, val middle: Vec3?)

/**
 * A swept patch over a [BlendedField]: planar rings across the part's axis, each found as a star-shaped section. The field's reach
 * along the axis is found first; the rings stand there closer together toward either end, as the latitudes of a sphere do, so the
 * ends close round, and the first and last are poles. In each ring's plane the section's middle is the middle of the inside of a
 * grid laid over the field's box; from it a ray at each of the fixed v angles (v = 0 toward up, 0.25 toward side) is marched in
 * from the box's edge to the outermost crossing of the field and halved down onto it. The ring's slice is fitted through those
 * points exactly, so a shell grown from the slices and the patch agree, and its shading normals are the field's gradient.
 */
object FieldLoft {
    /**
     * The patch [name] of [field], given in the part's own axes (x along, y up, z side), placed by [axes], on a grid of [rings] by
     * [segments], with each ring's u from its distance [uOf] along the axis. [drawn] may resize each ring by its u. [checkCancelled]
     * runs between rings. With [end] the run stops open at that distance along the axis, its rings closer together toward its start
     * alone. With [firstCrossing] each ray stops where it first leaves the solid going out from the middle, so solids standing apart
     * from the section's middle, digits joined to the field, are left to runs of their own. Shading normals are the gradient of
     * [shadedBy], the field itself unless another surface it meets is cut through a fuller one.
     */
    fun loft(
        name: String,
        field: BlendedField,
        axes: PartAxes,
        rings: Int,
        segments: Int,
        uOf: (Float) -> Float,
        drawn: (Float) -> DrawnRing? = { null },
        checkCancelled: () -> Unit = {},
        end: Float? = null,
        firstCrossing: Boolean = false,
        shadedBy: BlendedField = field,
    ): Patch {
        val own = if (firstCrossing) field.withoutDigits() else field
        val (first, reached) = reach(own)
        val last = end ?: reached
        val box = (field.high - field.low).length
        val slices = ArrayList<Slice>(rings)
        val points = ArrayList<Vec3>(rings * segments)
        var section: ClosedSpline? = null
        for (ring in 0 until rings) {
            checkCancelled()
            val share = if (end == null) (1f - cos(PI * ring / (rings - 1)).toFloat()) / 2f else 1f - cos(PI / 2.0 * ring / (rings - 1)).toFloat()
            val x = first + (last - first) * share
            val u = uOf(x)
            val middle = middleAt(own, x)
            val pole = ring == 0 || (ring == rings - 1 && end == null)
            val hits = if (pole || middle == null) null else hitsAt(field, x, middle, segments, firstCrossing)
            val size = hits?.let { spanOf(it) }
            if (hits == null || size == null || max(size.second.x, size.second.y) < box * POLE_SHARE) {
                val at = middle ?: Vec2(field.low.y + (field.high.y - field.low.y) / 2f, field.low.z + (field.high.z - field.low.z) / 2f)
                val centre = drawn(u)?.middle ?: axes.point(x, at.x, at.y)
                slices += Slice(u, Frame(centre, axes.along, axes.up), 0f, 0f, section ?: circle(segments), Frame(centre, axes.along, axes.up).rotation)
                repeat(segments) { points += Vec3(x, at.x, at.y) }
                continue
            }
            val (middleOfHits, spans) = size
            val unit = hits.map { Vec2((it.x - middleOfHits.x) / (spans.x / 2f), (it.y - middleOfHits.y) / (spans.y / 2f)) }
            val spline = through(unit)
            section = spline
            val sized = drawn(u)
            val height = sized?.height ?: spans.y
            val width = sized?.width ?: spans.x
            val centre = sized?.middle ?: axes.point(x, middleOfHits.y, middleOfHits.x)
            slices += Slice(u, Frame(centre, axes.along, axes.up), height, width, spline, Frame(centre, axes.along, axes.up).rotation)
            hits.forEach { points += Vec3(x, it.y, it.x) }
        }
        val filled = slices.mapIndexed { i, slice -> if (slice.height == 0f && slice.width == 0f) slice.copy(section = nearestSection(slices, i, segments)) else slice }
        val patch = SliceBuilder.loft(name, filled, segments)
        val step = box * GRADIENT_SHARE
        val normals = FloatArray(points.size * Patch.COMPONENTS)
        points.forEachIndexed { i, local ->
            val g = shadedBy.gradient(local, step)
            val length = g.length
            val n = if (length > 0f) axes.point(g.x / length, g.y / length, g.z / length) else axes.along
            normals[i * 3] = n.x
            normals[i * 3 + 1] = n.y
            normals[i * 3 + 2] = n.z
        }
        return patch.withProxyNormals(normals)
    }

    /** Where along the axis the field's solid starts and ends. */
    fun reach(field: BlendedField): Pair<Float, Float> {
        val deepest = deepestAlong(field)
        return edge(field, field.low.x, deepest) to edge(field, field.high.x, deepest)
    }

    /** The point along the axis, between [outside] (no section) and [inside] (a section), where the section begins. */
    private fun edge(field: BlendedField, outside: Float, inside: Float): Float {
        var out = outside
        var inn = inside
        repeat(EXTENT_HALVINGS) {
            val mid = (out + inn) / 2f
            if (middleAt(field, mid) != null) inn = mid else out = mid
        }
        return inn
    }

    private fun deepestAlong(field: BlendedField): Float {
        var best = field.low.x
        var bestValue = Float.MAX_VALUE
        for (i in 0..SEARCH_GRID * 2) {
            val x = field.low.x + (field.high.x - field.low.x) * i / (SEARCH_GRID * 2)
            val value = deepestIn(field, x, field.low.y, field.high.y, field.low.z, field.high.z).second
            if (value < bestValue) {
                bestValue = value
                best = x
            }
        }
        return best
    }

    /** The deepest grid point of the plane at [x] within the given box, as (y, z), and the field there. */
    private fun deepestIn(field: BlendedField, x: Float, y0: Float, y1: Float, z0: Float, z1: Float): Pair<Vec2, Float> {
        var best = Vec2(y0, z0)
        var bestValue = Float.MAX_VALUE
        for (i in 0..SEARCH_GRID) for (j in 0..SEARCH_GRID) {
            val y = y0 + (y1 - y0) * i / SEARCH_GRID
            val z = z0 + (z1 - z0) * j / SEARCH_GRID
            val value = field.value(Vec3(x, y, z))
            if (value < bestValue) {
                bestValue = value
                best = Vec2(y, z)
            }
        }
        return best to bestValue
    }

    /**
     * The middle of the section at [x] as (y, z): the mean of the grid points inside it, looked for again on a finer grid round the
     * deepest point when the first grid finds none; null when the plane has no inside.
     */
    fun middleAt(field: BlendedField, x: Float): Vec2? {
        val coarse = insideMean(field, x, field.low.y, field.high.y, field.low.z, field.high.z)
        if (coarse != null) return coarse
        val (deep, value) = deepestIn(field, x, field.low.y, field.high.y, field.low.z, field.high.z)
        if (value < 0f) return deep
        val cellY = (field.high.y - field.low.y) / SEARCH_GRID
        val cellZ = (field.high.z - field.low.z) / SEARCH_GRID
        val (fine, fineValue) = deepestIn(field, x, deep.x - cellY, deep.x + cellY, deep.y - cellZ, deep.y + cellZ)
        return if (fineValue < 0f) fine else null
    }

    private fun insideMean(field: BlendedField, x: Float, y0: Float, y1: Float, z0: Float, z1: Float): Vec2? {
        var sumY = 0f
        var sumZ = 0f
        var count = 0
        for (i in 0..SEARCH_GRID) for (j in 0..SEARCH_GRID) {
            val y = y0 + (y1 - y0) * i / SEARCH_GRID
            val z = z0 + (z1 - z0) * j / SEARCH_GRID
            if (field.value(Vec3(x, y, z)) < 0f) {
                sumY += y
                sumZ += z
                count++
            }
        }
        if (count == 0) return null
        val mean = Vec2(sumY / count, sumZ / count)
        return if (field.value(Vec3(x, mean.x, mean.y)) < 0f) mean else deepestIn(field, x, y0, y1, z0, z1).first
    }

    /** The outermost crossing along each of [segments] rays from [middle] in the plane at [x], as (z, y): side, then up. */
    private fun hitsAt(field: BlendedField, x: Float, middle: Vec2, segments: Int, firstCrossing: Boolean): List<Vec2> {
        val reachY = max(field.high.y - middle.x, middle.x - field.low.y)
        val reachZ = max(field.high.z - middle.y, middle.y - field.low.z)
        val far = sqrt(reachY * reachY + reachZ * reachZ)
        return List(segments) { j ->
            val angle = 2.0 * PI * j / segments
            val up = cos(angle).toFloat()
            val side = sin(angle).toFloat()
            fun at(r: Float) = field.value(Vec3(x, middle.x + up * r, middle.y + side * r))
            var outer = far
            var inner = far
            if (firstCrossing) {
                inner = 0f
                for (step in 1..MARCH_STEPS) {
                    val r = far * step.toFloat() / MARCH_STEPS
                    if (at(r) >= 0f) {
                        outer = r
                        break
                    }
                    inner = r
                }
            } else for (step in 1..MARCH_STEPS) {
                val r = far * (1f - step.toFloat() / MARCH_STEPS)
                if (at(r) < 0f) {
                    inner = r
                    break
                }
                outer = r
            }
            if (!firstCrossing && inner == far) inner = 0f
            repeat(HALVINGS) {
                val mid = (outer + inner) / 2f
                if (at(mid) < 0f) inner = mid else outer = mid
            }
            val r = (outer + inner) / 2f
            Vec2(middle.y + side * r, middle.x + up * r)
        }
    }

    /** The middle of the box round [hits] and its spans, both as (side, up). */
    private fun spanOf(hits: List<Vec2>): Pair<Vec2, Vec2> {
        val minX = hits.minOf { it.x }
        val maxX = hits.maxOf { it.x }
        val minY = hits.minOf { it.y }
        val maxY = hits.maxOf { it.y }
        return Vec2((minX + maxX) / 2f, (minY + maxY) / 2f) to Vec2(maxX - minX, maxY - minY)
    }

    private fun nearestSection(slices: List<Slice>, i: Int, segments: Int): ClosedSpline {
        val sized = slices.indices.filter { slices[it].height > 0f || slices[it].width > 0f }
        val nearest = sized.minByOrNull { kotlin.math.abs(it - i) } ?: return circle(segments)
        return slices[nearest].section
    }

    /** The closed spline whose samples at its own count of evenly spaced v pass through [points] exactly. */
    fun through(points: List<Vec2>): ClosedSpline {
        val n = points.size
        val xs = FloatArray(n) { points[it].x }
        val ys = FloatArray(n) { points[it].y }
        val cx = xs.copyOf()
        val cy = ys.copyOf()
        repeat(THROUGH_SWEEPS) {
            for (j in 0 until n) {
                val before = (j - 1 + n) % n
                val after = (j + 1) % n
                cx[j] = (6f * xs[j] - cx[before] - cx[after]) / 4f
                cy[j] = (6f * ys[j] - cy[before] - cy[after]) / 4f
            }
        }
        return ClosedSpline(List(n) { Vec2(cx[it], cy[it]) })
    }

    private const val THROUGH_SWEEPS = 40
    private fun circle(segments: Int) = ClosedSpline(List(segments) { Vec2(sin(2.0 * PI * it / segments).toFloat(), cos(2.0 * PI * it / segments).toFloat()) })
}
