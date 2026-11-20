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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Steps a ray takes out to its reach. */
private const val RAY_STEPS = 24

/** Halvings of the bracket round a crossing. */
private const val HALVINGS = 12

/** Steps a point sliding along a wall takes, each as long as a ray's. */
private const val SLIDE_STEPS = 48

/** Steps from the last ring along the axis looking for the tip, each an eighth of the digit's radius. */
private const val TIP_STEPS = 64

/** How wide a ring buried whole is, in the digit's radii: a ring kept round so the run's start has a surface to measure on. */
private const val BURIED_RING = 0.5f

/** How far past a wall, as a share of the reach, a slid point carries on over the surface. */
private const val OVERLAP = 0.06f

/** Steps along the gradient that carry a point onto the surface. */
private const val PROJECTIONS = 4

/** A ring no wider than this share of the digit's radius is a pole. */
private const val POLE_SHARE = 1e-3f

/**
 * A ring whose nearest two neighbouring points lie closer than this share of its mean spacing is crowded: its axis runs so near the
 * surface on one side that the rays there all meet it in one small patch.
 */
private const val CROWDED = 0.05f

/**
 * A digit's run over a field it is joined to: planar rings across the digit's own axis at given shares along it, each the part of the
 * field's section that is the digit's. From the axis a ray at each fixed v angle (v = 0 toward the part's up, 0.25 toward its side, both
 * turned by the digit's roll) goes out to where it first leaves the solid; one that first reaches a wall, where another digit's axis lies
 * nearer, stops there inside the solid, and of a stretch of such rays the first and the last slide along the wall to where it meets the
 * surface, so two digits' runs meet on the line between them, a web's middle. A ray still inside at the run's reach, and the rest of a
 * stretch at a wall, lie inside, buried, and no face is built on them: the field's run shows the surface there. The run ends in a pole at the digit's tip. Its slices pass through its points, and its shading normals are the
 * field's gradient, as [FieldLoft]'s.
 */
object DigitLoft {
    /**
     * The patch [name] of digit [index] of [digits] over [field], in the part's own axes and placed by [axes], a ring at each of [shares]
     * along it with the u of [us], [segments] round, out to [reach] of the digit's radii from its axis, turned [roll] radians about it.
     * Rings before [buriedBefore], a share, are buried whole, small rings round the axis. With [begins], another digit walls this one's rays in only past its own
     * share there, where its run shows the surface. [checkCancelled] runs between rings.
     */
    fun loft(
        name: String,
        field: BlendedField,
        axes: PartAxes,
        digits: List<DigitAxis>,
        index: Int,
        shares: FloatArray,
        us: FloatArray,
        segments: Int,
        reach: Float,
        roll: Float = 0f,
        buriedBefore: Float = Float.NEGATIVE_INFINITY,
        begins: FloatArray? = null,
        checkCancelled: () -> Unit = {},
    ): Patch {
        require(shares.size == us.size && shares.size >= 2) { "A digit's run needs a u a ring" }
        val digit = digits[index]
        val frame = RingFrame.of(digit.direction, roll)
        val rays = Rays(field, digits, index, digit.radius * reach, begins)
        val tangentWorld = axes.point(digit.direction.x, digit.direction.y, digit.direction.z)
        val upWorld = axes.point(frame.up.x, frame.up.y, frame.up.z)
        val slices = ArrayList<Slice>(shares.size)
        val points = ArrayList<Vec3>(shares.size * segments)
        val buried = BooleanArray(shares.size * segments)
        val slid = BooleanArray(shares.size * segments)
        var section: ClosedSpline? = null
        for (ring in shares.indices) {
            checkCancelled()
            val last = ring == shares.size - 1
            val centre = if (last) tip(field, digit, shares[ring - 1]) else digit.at(shares[ring])
            val found = when {
                last -> null
                shares[ring] < buriedBefore -> List(segments) { j -> RayHit(centre + frame.ray(2.0 * PI * j / segments) * (digit.radius * BURIED_RING), true) }
                field.value(centre) >= 0f -> null
                else -> rays.evenRing(centre, frame, segments)
            }
            val flat = found?.map { frame.flat(it.point - centre) }
            val box = flat?.let { spanOf(it) }
            if (found == null || box == null || maxOf(box.second.x, box.second.y) < digit.radius * POLE_SHARE) {
                val world = axes.point(centre.x, centre.y, centre.z)
                slices += Slice(us[ring], Frame(world, tangentWorld, upWorld), 0f, 0f, section ?: circle(segments), Frame(world, tangentWorld, upWorld).rotation)
                repeat(segments) { points += centre }
                if (shares[ring] < buriedBefore) for (j in 0 until segments) buried[ring * segments + j] = true
                continue
            }
            val (middle, found2) = box
            val spans = Vec2(maxOf(found2.x, digit.radius * POLE_SHARE), maxOf(found2.y, digit.radius * POLE_SHARE))
            val spline = FieldLoft.through(flat.map { Vec2((it.x - middle.x) / (spans.x / 2f), (it.y - middle.y) / (spans.y / 2f)) })
            section = spline
            val middle3 = centre + frame.side * middle.x + frame.up * middle.y
            val world = axes.point(middle3.x, middle3.y, middle3.z)
            slices += Slice(us[ring], Frame(world, tangentWorld, upWorld), spans.y, spans.x, spline, Frame(world, tangentWorld, upWorld).rotation)
            found.forEachIndexed { j, hit ->
                points += hit.point
                buried[ring * segments + j] = hit.buried || shares[ring] < buriedBefore
                slid[ring * segments + j] = hit.slid
            }
        }
        val lofted = SliceBuilder.loft(name, slices, segments)
        val step = digit.radius * GRADIENT_SHARE
        buryCoincident(points, buried, slid, shares.size, segments, digit.radius * POLE_SHARE)
        buryFolds(points, buried, slid, shares.size, segments, field, step)
        val normals = FloatArray(points.size * Patch.COMPONENTS)
        points.forEachIndexed { i, local ->
            val g = field.gradient(local, step)
            val length = g.length
            val n = if (length > 0f) axes.point(g.x / length, g.y / length, g.z / length) else tangentWorld
            normals[i * 3] = n.x
            normals[i * 3 + 1] = n.y
            normals[i * 3 + 2] = n.z
        }
        return Patch(
            name, lofted.nu, lofted.nv, lofted.closedV, lofted.slices, lofted.points, proxyNormals = normals, openEnds = true,
            buried = buried.takeIf { flags -> flags.any { it } },
        )
    }

    /**
     * Buries every slid point lying within [gap] of an unburied neighbour on its ring, where two slides along one wall met the surface at one
     * place: a face on the two would have no area.
     */
    private fun buryCoincident(points: List<Vec3>, buried: BooleanArray, slid: BooleanArray, rings: Int, segments: Int, gap: Float) {
        for (ring in 0 until rings) for (j in 0 until segments) {
            val at = ring * segments + j
            if (!slid[at] || buried[at]) continue
            val before = ring * segments + (j - 1 + segments) % segments
            val after = ring * segments + (j + 1) % segments
            fun meets(other: Int) = !buried[other] && (points[at] - points[other]).length < gap
            if (meets(before) || meets(after)) buried[at] = true
        }
    }

    /**
     * Buries the slid points of every built face turned against the field's gradient, where a slide along a wall overshot its
     * neighbours, until none is left: a fold would ink a line across the surface.
     */
    private fun buryFolds(points: List<Vec3>, buried: BooleanArray, slid: BooleanArray, rings: Int, segments: Int, field: BlendedField, step: Float) {
        repeat(FOLD_PASSES) {
            var any = false
            for (ring in 0 until rings - 1) for (j in 0 until segments) {
                val next = (j + 1) % segments
                val corners = intArrayOf(ring * segments + j, ring * segments + next, (ring + 1) * segments + j, (ring + 1) * segments + next)
                if (corners.any { buried[it] }) continue
                val a = points[corners[0]]
                // The part's own axes turn the other way from body space, so an outward face's cross product here points in.
                val normal = (points[corners[3]] - a) cross (points[corners[1]] - a)
                if (normal.length < FOLD_AREA || (normal dot field.gradient(a, step)) <= 0f) continue
                corners.filter { slid[it] }.forEach {
                    buried[it] = true
                    any = true
                }
            }
            if (!any) return
        }
    }

    private const val FOLD_PASSES = 4
    private const val FOLD_AREA = 1e-12f

    /** The digit's tip: where the solid ends along its axis, looked for from the ring at share [from]. */
    private fun tip(field: BlendedField, digit: DigitAxis, from: Float): Vec3 {
        val step = digit.radius / 8f
        var inside = digit.at(from)
        var outside = inside
        for (k in 1..TIP_STEPS) {
            val p = digit.at(from) + digit.direction * (step * k)
            if (field.value(p) >= 0f) {
                outside = p
                break
            }
            inside = p
        }
        repeat(HALVINGS) {
            val mid = (inside + outside) * 0.5f
            if (field.value(mid) < 0f) inside = mid else outside = mid
        }
        return (inside + outside) * 0.5f
    }

    private const val GRADIENT_SHARE = 1e-2f

    private fun circle(segments: Int) = ClosedSpline(List(segments) { Vec2(sin(2.0 * PI * it / segments).toFloat(), cos(2.0 * PI * it / segments).toFloat()) })
}

/** The middle of the box round [points] and its spans. */
internal fun spanOf(points: List<Vec2>): Pair<Vec2, Vec2> {
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    return Vec2((minX + maxX) / 2f, (minY + maxY) / 2f) to Vec2(maxX - minX, maxY - minY)
}

/** A ring's axes in the part's own frame: across it [side] and [up], [tangent] along the digit, so v = 0.25 lies toward side. */
class RingFrame(val tangent: Vec3, val up: Vec3, val side: Vec3) {
    internal fun ray(angle: Double): Vec3 = up * cos(angle).toFloat() + side * sin(angle).toFloat()

    /** [offset], a vector in the ring's plane, as (side, up). */
    internal fun flat(offset: Vec3): Vec2 = Vec2(offset dot side, offset dot up)

    companion object {
        /** The frame across [tangent] whose up is the part's up made square to it, turned [roll] radians about it. */
        fun of(tangent: Vec3, roll: Float): RingFrame {
            val reference = if (abs(tangent.y) > NEAR_UP) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)
            val up0 = (reference - tangent * (reference dot tangent)).normalized()
            val side0 = tangent cross up0
            val up = up0 * cos(roll) + side0 * sin(roll)
            return RingFrame(tangent, up, tangent cross up)
        }

        private const val NEAR_UP = 0.95f
    }
}

/** A ray's end: on the surface, or [buried] inside it. */
internal class RayHit(val point: Vec3, val buried: Boolean, val slid: Boolean = false)

/** The rays of digit [index]'s rings over [field], each out to [reach] from the axis. */
private class Rays(
    private val field: BlendedField,
    private val digits: List<DigitAxis>,
    private val index: Int,
    private val reach: Float,
    private val begins: FloatArray?,
) {
    private val step = reach / RAY_STEPS

    /** How far into another digit's cell [p] lies: positive past the wall. */
    private fun pastWall(p: Vec3): Float {
        val own = digits[index].distance(p)
        var nearest = Float.MAX_VALUE
        digits.forEachIndexed { i, digit ->
            if (i != index && (begins == null || digit.share(p) >= begins[i])) nearest = minOf(nearest, digit.distance(p))
        }
        return own - nearest
    }

    private sealed interface Out {
        class Surface(val point: Vec3) : Out
        class Wall(val point: Vec3, val normal: Vec3) : Out
        class Reach(val point: Vec3) : Out
    }

    /**
     * The ring cast from [centre], or, where that ring is crowded, the ring cast again from the middle of its box inside the solid when
     * that one spreads its points more evenly and buries no more of them.
     */
    fun evenRing(centre: Vec3, frame: RingFrame, segments: Int): List<RayHit> {
        val hits = ring(centre, frame, segments)
        val crowding = crowding(hits)
        if (crowding >= CROWDED) return hits
        val (middle, _) = spanOf(hits.map { frame.flat(it.point - centre) })
        val from = centre + frame.side * middle.x + frame.up * middle.y
        if (field.value(from) >= 0f) return hits
        val again = ring(from, frame, segments)
        val buriesNoMore = again.count { it.buried } <= hits.count { it.buried }
        return if (buriesNoMore && crowding(again) > crowding) again else hits
    }

    /** The least gap between neighbouring points of [hits] as a share of their mean gap: 1 for points evenly spaced, near 0 where some crowd. */
    private fun crowding(hits: List<RayHit>): Float {
        val gaps = hits.indices.map { j -> (hits[(j + 1) % hits.size].point - hits[j].point).length }
        val mean = gaps.average().toFloat()
        return if (mean > 0f) gaps.min() / mean else 1f
    }

    fun ring(centre: Vec3, frame: RingFrame, segments: Int): List<RayHit> {
        val outs = List(segments) { j -> cast(centre, frame.ray(2.0 * PI * j / segments), frame.tangent) }
        return outs.indices.map { j ->
            when (val out = outs[j]) {
                is Out.Surface -> RayHit(out.point, false)
                is Out.Reach -> RayHit(out.point, true)
                is Out.Wall -> {
                    val before = outs[(j - 1 + segments) % segments]
                    val after = outs[(j + 1) % segments]
                    val toward = (before as? Out.Surface)?.point ?: (after as? Out.Surface)?.point
                    toward?.let { slide(out, it, frame.tangent) }?.let { RayHit(it, false, slid = true) } ?: RayHit(out.point, true)
                }
            }
        }
    }

    private fun cast(centre: Vec3, direction: Vec3, tangent: Vec3): Out {
        var inner = 0f
        for (k in 1..RAY_STEPS) {
            val r = step * k
            val p = centre + direction * r
            if (pastWall(p) > 0f) {
                val at = bisect(inner, r) { pastWall(centre + direction * it) <= 0f }
                val w = centre + direction * at
                if (field.value(w) >= 0f) return Out.Surface(surfaceOn(centre, direction, inner, at))
                return Out.Wall(w, wallNormal(w, tangent))
            }
            if (field.value(p) >= 0f) return Out.Surface(surfaceOn(centre, direction, inner, r))
            inner = r
        }
        return Out.Reach(centre + direction * reach)
    }

    private fun surfaceOn(centre: Vec3, direction: Vec3, inside: Float, outside: Float): Vec3 =
        centre + direction * bisect(inside, outside) { field.value(centre + direction * it) < 0f }

    /** The wall's normal at [p] in the ring's plane, square to [tangent]. */
    private fun wallNormal(p: Vec3, tangent: Vec3): Vec3 {
        val h = step / 4f
        val g = Vec3(
            pastWall(Vec3(p.x + h, p.y, p.z)) - pastWall(Vec3(p.x - h, p.y, p.z)),
            pastWall(Vec3(p.x, p.y + h, p.z)) - pastWall(Vec3(p.x, p.y - h, p.z)),
            pastWall(Vec3(p.x, p.y, p.z + h)) - pastWall(Vec3(p.x, p.y, p.z - h)),
        )
        val inPlane = g - tangent * (g dot tangent)
        return if (inPlane.length > 0f) inPlane.normalized() else g
    }

    /**
     * From [wall] along the wall in the ring's plane toward [toward] to where it meets the surface, then on over the surface past the
     * wall by [OVERLAP] of the reach, so the runs either side of a web overlap rather than leave a sliver between them; null if it stays
     * inside.
     */
    private fun slide(wall: Out.Wall, toward: Vec3, tangent: Vec3): Vec3? {
        var along = (tangent cross wall.normal).normalized()
        if ((along dot (toward - wall.point)) < 0f) along = along * -1f
        var inner = 0f
        for (k in 1..SLIDE_STEPS) {
            val r = step * k
            if (field.value(wall.point + along * r) >= 0f) {
                val met = wall.point + along * bisect(inner, r) { field.value(wall.point + along * it) < 0f }
                return onSurface(met + wall.normal * (reach * OVERLAP))
            }
            inner = r
        }
        return null
    }

    /** [p] carried along the field's gradient onto its surface. */
    private fun onSurface(p: Vec3): Vec3 {
        var q = p
        repeat(PROJECTIONS) {
            val g = field.gradient(q, step / 8f)
            val g2 = g dot g
            if (g2 <= 0f) return q
            q = q - g * (field.value(q) * (2f * step / 8f) / g2)
        }
        return q
    }

    /** The point between [inside] and [outside] where [isInside] turns false. */
    private inline fun bisect(inside: Float, outside: Float, isInside: (Float) -> Boolean): Float {
        var a = inside
        var b = outside
        repeat(HALVINGS) {
            val mid = (a + b) / 2f
            if (isInside(mid)) a = mid else b = mid
        }
        return (a + b) / 2f
    }
}
