package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3

/**
 * [solid] kept to the box from [low] to [high] in its own space: the larger of its field and how far a point lies outside the box, so
 * the solid ends square at the box's faces and nothing past them is meshed. Read only where the box's faces lie well away from what is
 * kept of a surface meshed from it.
 */
class ClippedSolid(val solid: Solid, override val low: Vec3, override val high: Vec3) : Solid {
    override fun distance(p: Vec3): Float = maxOf(solid.distance(p), outside(p))

    /** How far [p] lies outside the box along its farthest axis, negative inside. */
    fun outside(p: Vec3): Float = maxOf(low.x - p.x, p.x - high.x, maxOf(low.y - p.y, p.y - high.y, maxOf(low.z - p.z, p.z - high.z)))
}
