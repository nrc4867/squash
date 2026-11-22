package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A linear map [linear] followed by a move by [offset]. */
data class Affine(val linear: Mat3, val offset: Vec3) {
    operator fun invoke(point: Vec3): Vec3 = linear * point + offset

    /** What this map does to a normal, up to its length: the cofactor matrix of [linear], its inverse transposed times its determinant. */
    val normals: Mat3 by lazy { Mat3(linear.y cross linear.z, linear.z cross linear.x, linear.x cross linear.y) }

    /** The map that undoes this one; [linear] is never singular, since a stretch is held above [MIN_STRETCH]. */
    val inverse: Affine by lazy {
        val undo = normals.transposed() * (1f / (linear.x dot (linear.y cross linear.z)))
        Affine(undo, (undo * offset) * -1f)
    }

    /** Whether this map leaves every point where it is. */
    val isIdentity: Boolean get() = linear.x.isAxis(1f, 0f, 0f) && linear.y.isAxis(0f, 1f, 0f) && linear.z.isAxis(0f, 0f, 1f) && offset.isAxis(0f, 0f, 0f)

    companion object {
        val IDENTITY = Affine(Mat3(Vec3.X, Vec3.Y, Vec3.Z), Vec3.ZERO)
    }
}

private fun Vec3.isAxis(x: Float, y: Float, z: Float) = this.x == x && this.y == y && this.z == z

/** This map with its rows and columns swapped. */
internal fun Mat3.transposed(): Mat3 = Mat3(Vec3(x.x, y.x, z.x), Vec3(x.y, y.y, z.y), Vec3(x.z, y.z, z.z))

/** This map with every entry scaled by [s]. */
internal operator fun Mat3.times(s: Float): Mat3 = Mat3(x * s, y * s, z * s)

/** Column by column: this map after [other]. */
internal operator fun Mat3.times(other: Mat3): Mat3 = Mat3(this * other.x, this * other.y, this * other.z)

/** The turn by [radians] about the unit direction [axis]. */
internal fun rotation(axis: Vec3, radians: Float): Mat3 {
    val c = cos(radians)
    val s = sin(radians)
    fun turned(v: Vec3) = v * c + (axis cross v) * s + axis * ((axis dot v) * (1f - c))
    return Mat3(turned(Vec3.X), turned(Vec3.Y), turned(Vec3.Z))
}

/** Lengthening by [factor] along the unit direction [along], and narrowing by its square root across it, so volume is kept. */
internal fun stretchAlong(along: Vec3, factor: Float): Mat3 {
    val across = 1f / sqrt(factor)
    fun scaled(v: Vec3): Vec3 {
        val part = along * (along dot v)
        return part * factor + (v - part) * across
    }
    return Mat3(scaled(Vec3.X), scaled(Vec3.Y), scaled(Vec3.Z))
}

/** Lengthening by [factor] along the unit direction [along], leaving every direction across it as it is. */
internal fun lengthenAlong(along: Vec3, factor: Float): Mat3 {
    fun scaled(v: Vec3): Vec3 = v + along * ((along dot v) * (factor - 1f))
    return Mat3(scaled(Vec3.X), scaled(Vec3.Y), scaled(Vec3.Z))
}
