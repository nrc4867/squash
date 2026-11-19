package dev.chieppa.squash.character.copies

import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** This matrix applied after [other]: the product taking a vector through [other] first. */
internal operator fun Mat3.times(other: Mat3): Mat3 = Mat3(this * other.x, this * other.y, this * other.z)

/** This matrix with its rows and columns swapped, the inverse of a rotation. */
internal val Mat3.transposed: Mat3
    get() = Mat3(Vec3(x.x, y.x, z.x), Vec3(x.y, y.y, z.y), Vec3(x.z, y.z, z.z))

/** This matrix scaled by [s]. */
internal fun Mat3.scaled(s: Float): Mat3 = Mat3(x * s, y * s, z * s)

/** [v] turned [radians] about the unit axis [axis], by Rodrigues' formula. */
internal fun Vec3.turnedAbout(axis: Vec3, radians: Float): Vec3 {
    if (radians == 0f) return this
    val c = cos(radians)
    val s = sin(radians)
    return this * c + (axis cross this) * s + axis * ((axis dot this) * (1f - c))
}

/** A unit vector square to [n], along [hint] where it is not parallel to [n]. */
internal fun squareTo(n: Vec3, hint: Vec3): Vec3 {
    val flat = hint - n * (hint dot n)
    if (flat.length > 1e-4f) return flat.normalized()
    val other = if (abs(n.x) < 0.9f) Vec3.X else Vec3.Y
    return (other - n * (other dot n)).normalized()
}

/**
 * The frame whose columns are [side], [up] and [along], [along] and [up] made square to each other and [side] their cross, or its
 * mirror image when [mirrored], as a right side's part shows.
 */
internal fun frameOf(along: Vec3, up: Vec3, mirrored: Boolean): Mat3 {
    val a = along.normalized()
    val u = squareTo(a, up)
    val side = u cross a
    return Mat3(if (mirrored) side * -1f else side, u, a)
}

/** A smooth step from 0 at 0 to 1 at 1. */
internal fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
