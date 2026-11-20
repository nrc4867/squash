package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.sqrt

/** A solid that also reads where on its run a point lies: [place] writes the field, u and v at [PLACE_FIELD], [PLACE_U] and [PLACE_V]. */
interface PlacingSolid : Solid {
    fun place(p: Vec3, out: FloatArray)
}

/** [solid], read in a part's own space, placed in body space: a body point reaches it by taking off [origin] and, when [mirrored], reflecting it. */
class BodySolid(val solid: Solid, val origin: Vec3, val mirrored: Boolean) : Solid {
    /** [p], a body point, in the part's own space. */
    fun local(p: Vec3): Vec3 {
        val q = p - origin
        return if (mirrored) q.mirrored else q
    }

    override fun distance(p: Vec3): Float = solid.distance(local(p))

    override val low: Vec3 = if (mirrored) Vec3(solid.low.x, solid.low.y, -solid.high.z) + origin else solid.low + origin
    override val high: Vec3 = if (mirrored) Vec3(solid.high.x, solid.high.y, -solid.low.z) + origin else solid.high + origin
}

/** A hollow cut into a part: the [hollow]'s solid in body space and the [wall] kept clear round it, which its lining fills again. */
class Hollow(val hollow: Solid, val wall: Float) {
    /** How far, at most, the cut reaches past the hollow's box. */
    internal fun outside(p: Vec3): Float {
        val dx = maxOf(hollow.low.x - p.x, 0f, p.x - hollow.high.x)
        val dy = maxOf(hollow.low.y - p.y, 0f, p.y - hollow.high.y)
        val dz = maxOf(hollow.low.z - p.z, 0f, p.z - hollow.high.z)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** The cut's field at body point [p]: positive where the part is cut away, within the hollow and its wall. */
    fun cut(p: Vec3): Float = if (outside(p) > wall) -Float.MAX_VALUE else wall - hollow.distance(p)
}

/**
 * [solid], read in its part's own space, with [hollows] cut into it: the larger of its field and each hollow's cut, so nothing of it lies
 * within a hollow or its wall. [origin] and [mirrored] take a point of the part's own space into body space, where the hollows lie. It
 * places a point as [solid] does.
 */
class CarvedSolid(val solid: Solid, val hollows: List<Hollow>, val origin: Vec3, val mirrored: Boolean) : PlacingSolid {
    override val low: Vec3 get() = solid.low
    override val high: Vec3 get() = solid.high

    private fun body(q: Vec3): Vec3 = (if (mirrored) q.mirrored else q) + origin

    private fun cut(q: Vec3): Float {
        val p = body(q)
        var most = -Float.MAX_VALUE
        for (hollow in hollows) most = maxOf(most, hollow.cut(p))
        return most
    }

    override fun distance(p: Vec3): Float = maxOf(solid.distance(p), cut(p))

    override fun place(p: Vec3, out: FloatArray) {
        val placing = solid as? PlacingSolid
        if (placing != null) {
            placing.place(p, out)
        } else {
            out[PLACE_FIELD] = solid.distance(p)
            out[PLACE_U] = 0f
            out[PLACE_V] = 0f
        }
        out[PLACE_FIELD] = maxOf(out[PLACE_FIELD], cut(p))
    }
}

/**
 * The lining of a hollow cut into the solids it sits in: a shell [wall] thick round [hollow], read in its part's own space, kept to where
 * [hosts], the solids it is cut into as they were before the cut, read in body space, reach, with a rolled rim of radius [rim] round its
 * mouth, the curve where the hollow meets their surface, lying just outside the hollow and joined to the shell by a smooth union. Its
 * inner face is the hollow's surface and its rim stands half above the hosts' surface, so with the hosts cut by [Hollow]s of the same
 * hollow and wall it gives a recess with a rolled lip. [origin] and [mirrored] take a point of its part's own space into body space. It
 * places a point as the hollow does: u along the hollow from its mouth, v round it.
 */
class HollowLining(
    val hollow: PlacingSolid,
    val hosts: List<Solid>,
    val wall: Float,
    val rim: Float,
    val origin: Vec3,
    val mirrored: Boolean,
) : PlacingSolid {
    private val reach = wall + 2f * rim

    override val low: Vec3 = hollow.low - Vec3(reach, reach, reach)
    override val high: Vec3 = hollow.high + Vec3(reach, reach, reach)

    private fun body(q: Vec3): Vec3 = (if (mirrored) q.mirrored else q) + origin

    private fun lined(q: Vec3, inHollow: Float): Float {
        val p = body(q)
        var host = Float.MAX_VALUE
        for (solid in hosts) host = minOf(host, solid.distance(p))
        val shell = maxOf(-inHollow, inHollow - wall, host)
        if (rim <= 0f) return shell
        val off = inHollow - rim
        val roll = sqrt(off * off + host * host) - rim
        return smoothMin(shell, roll, rim * RIM_BLEND)
    }

    override fun distance(p: Vec3): Float = lined(p, hollow.distance(p))

    override fun place(p: Vec3, out: FloatArray) {
        hollow.place(p, out)
        out[PLACE_FIELD] = lined(p, out[PLACE_FIELD])
    }

    private companion object {
        /** The smooth union of the rim and the shell, as a share of the rim's radius. */
        const val RIM_BLEND = 0.6f
    }
}
