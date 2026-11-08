package dev.chieppa.squash.geom.blend

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A capsule-like tube of [radius] from [from] to [to], [rings] rings closed by a pole at each end, [segments] points round each,
 * with its faces outward as the mesh builder winds them.
 */
internal fun tube(name: String, from: Vec3, to: Vec3, radius: Float, rings: Int = 18, segments: Int = 16): Patch {
    val axis = (to - from).normalized()
    val helper = if (kotlin.math.abs(axis.y) < 0.9f) Vec3.Y else Vec3.X
    val side = (axis cross helper).normalized()
    val up = side cross axis
    val length = (to - from).length
    val points = FloatArray(rings * segments * 3)
    for (ring in 0 until rings) {
        val t = ring.toFloat() / (rings - 1)
        // Hemispherical ends: the angle runs from one pole to the other over the ends, straight between.
        val cap = radius / (length + 2f * radius)
        val (along, r) = when {
            t < cap -> {
                val a = (PI / 2 * (1 - t / cap)).toFloat()
                -radius * sin(a) to radius * cos(a)
            }
            t > 1f - cap -> {
                val a = (PI / 2 * ((t - (1f - cap)) / cap)).toFloat()
                length + radius * sin(a) to radius * cos(a)
            }
            else -> (t - cap) / (1f - 2f * cap) * length to radius
        }
        for (segment in 0 until segments) {
            val angle = (2 * PI * segment / segments).toFloat()
            val p = from + axis * along + side * (r * cos(angle)) + up * (r * sin(angle))
            val at = (ring * segments + segment) * 3
            points[at] = p.x
            points[at + 1] = p.y
            points[at + 2] = p.z
        }
    }
    return Patch(name, rings, segments, closedV = true, slices = null, points = points)
}

internal fun Patch.vertices(): List<Vec3> = (0 until nu * nv).map { point(it) }
