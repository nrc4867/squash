package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3

/**
 * The solid a patch's rings sweep out, read from its points themselves: ring i's [points] in order round at [vs], in the plane
 * [tangents] gives it, its up from [ups], at u [us], each read round from its own entry of [middles] where that is not null and from
 * the middle of its box otherwise; null for fewer than two rings.
 */
internal fun sweptOfRings(
    points: List<List<Vec3>>,
    vs: FloatArray,
    tangents: List<Vec3>,
    ups: List<Vec3>,
    us: FloatArray,
    middles: List<Vec3?>? = null,
): SweptSolid? {
    if (points.size < 2) return null
    val rings = points.indices.map { i -> ringOf(points[i], vs, tangents[i], ups[i], middles?.get(i)) }
    val tables = Array(rings.size) { RingTables.of(rings[it]) }
    var low = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    var high = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    rings.forEachIndexed { i, ring ->
        val reach = tables[i].radii.max()
        val c = ring.middle
        low = Vec3(minOf(low.x, c.x - reach), minOf(low.y, c.y - reach), minOf(low.z, c.z - reach))
        high = Vec3(maxOf(high.x, c.x + reach), maxOf(high.y, c.y + reach), maxOf(high.z, c.z + reach))
    }
    return SweptSolid(
        Array(rings.size) { rings[it].middle }, Array(rings.size) { rings[it].tangent }, Array(rings.size) { rings[it].up },
        tables, us.copyOf(), low, high,
    )
}

/** The ring through [ring]'s points, in the plane square to [tangent], its middle [given] moved into that plane, else the middle of their box there. */
private fun ringOf(ring: List<Vec3>, vs: FloatArray, tangent: Vec3, up: Vec3, given: Vec3? = null): SweptRing {
    val along = tangent.normalized()
    val upFlat = up - along * (up dot along)
    val upward = if (upFlat.length > 0f) upFlat.normalized() else up
    val side = upward cross along
    var minA = Float.MAX_VALUE
    var maxA = -Float.MAX_VALUE
    var minB = Float.MAX_VALUE
    var maxB = -Float.MAX_VALUE
    var height = 0f
    val origin = ring[0]
    for (p in ring) {
        val q = p - origin
        minA = minOf(minA, q dot upward)
        maxA = maxOf(maxA, q dot upward)
        minB = minOf(minB, q dot side)
        maxB = maxOf(maxB, q dot side)
        height += q dot along
    }
    val middle = if (given != null) {
        given + along * (height / ring.size - ((given - origin) dot along))
    } else {
        origin + upward * ((minA + maxA) / 2f) + side * ((minB + maxB) / 2f) + along * (height / ring.size)
    }
    return SweptRing(middle, along, upward, ring + ring.first(), vs + (vs.first() + 1f))
}
