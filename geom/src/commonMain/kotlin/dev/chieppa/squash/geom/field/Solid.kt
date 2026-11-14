package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3

/** A solid as a distance-like field in some space: negative inside, positive outside, near the true distance close to its surface. */
interface Solid {
    /** The field at [p]. */
    fun distance(p: Vec3): Float

    /** The least corner of a box holding the solid. */
    val low: Vec3

    /** The greatest corner of a box holding the solid. */
    val high: Vec3
}
