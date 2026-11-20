package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs

/**
 * An ellipsoid about [centre] whose semi-axes [radii] lie along [x], [y] and [z], three unit directions square to each other, in place
 * of the space's own axes: a dish turned to face along a sole's normal.
 */
class TurnedEllipsoid(val centre: Vec3, val x: Vec3, val y: Vec3, val z: Vec3, val radii: Vec3) : Solid {
    private val own = Ellipsoid(Vec3.ZERO, radii)

    override fun distance(p: Vec3): Float {
        val q = p - centre
        return own.distance(Vec3(q dot x, q dot y, q dot z))
    }

    private val reach = Vec3(
        abs(x.x) * radii.x + abs(y.x) * radii.y + abs(z.x) * radii.z,
        abs(x.y) * radii.x + abs(y.y) * radii.y + abs(z.y) * radii.z,
        abs(x.z) * radii.x + abs(y.z) * radii.y + abs(z.z) * radii.z,
    )

    override val low: Vec3 = centre - reach
    override val high: Vec3 = centre + reach
}
