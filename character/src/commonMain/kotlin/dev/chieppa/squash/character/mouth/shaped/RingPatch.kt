package dev.chieppa.squash.character.mouth.shaped

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small part's ring of [segments] points round the place [centre], its section's [across] and [depth] half-axes, each point at angle a
 * from the depth half-axis toward the across one, moved by [shape] (its across and depth scale at a, and how far along [along] it moves),
 * wound as a sweep's rings are, so faces point outward from a ring whose way is [along].
 */
internal fun ring(segments: Int, centre: Vec3, across: Vec3, depth: Vec3, along: Vec3, shape: (Float) -> Triple<Float, Float, Float>): List<Vec3> =
    List(segments) { k ->
        val a = 2f * PI.toFloat() * k / segments
        val (x, y, shift) = shape(a)
        centre + across * (sin(a) * x) + depth * (cos(a) * y) + along * shift
    }

/** A patch named [name] closed round its rings, [rings] from its root, at [us] along it; with [boneU] every vertex weighs as if there. */
internal fun ringPatch(name: String, rings: List<List<Vec3>>, us: FloatArray, boneU: Float? = null): Patch {
    val nv = rings.first().size
    val points = FloatArray(rings.size * nv * Patch.COMPONENTS)
    var o = 0
    for (ring in rings) for (p in ring) {
        points[o++] = p.x
        points[o++] = p.y
        points[o++] = p.z
    }
    return Patch(name, rings.size, nv, closedV = true, slices = null, points = points, boneU = boneU, us = us)
}

/** The way [across] would have to point for a ring along [along] with depth half-axis [depth] to wind outward: depth crossed with along. */
internal fun acrossOf(depth: Vec3, along: Vec3): Vec3 = (depth cross along).normalized()

/** [v] with its part along the unit [w] taken out. */
internal fun Vec3.without(w: Vec3): Vec3 = this - w * (this dot w)

/** Ease out: 0 at 0, 1 at 1, flat at 1. */
internal fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

/** A unit [v], or [fallback] where [v] has no length. */
internal fun unitOr(v: Vec3, fallback: Vec3): Vec3 = if (v.length > 1e-6f) v.normalized() else fallback
