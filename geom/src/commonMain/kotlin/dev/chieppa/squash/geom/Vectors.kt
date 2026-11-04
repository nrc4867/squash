package dev.chieppa.squash.geom

import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
}

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Float get() = sqrt(this dot this)
    fun normalized(): Vec3 = this * (1f / length)

    /** This vector reflected across the body's plane, z = 0. */
    val mirrored: Vec3 get() = Vec3(x, y, -z)

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val X = Vec3(1f, 0f, 0f)
        val Y = Vec3(0f, 1f, 0f)
        val Z = Vec3(0f, 0f, 1f)
    }
}

/** A rotation as its three columns: the images of x, y and z. */
data class Mat3(val x: Vec3, val y: Vec3, val z: Vec3) {
    operator fun times(v: Vec3): Vec3 = x * v.x + y * v.y + z * v.z
}

/** A point on an axis with its orientation: [tangent] along the axis, [up] at v = 0, [side] at v = 0.25. */
data class Frame(val center: Vec3, val tangent: Vec3, val up: Vec3) {
    val side: Vec3 get() = up cross tangent
    val rotation: Mat3 get() = Mat3(side, up, tangent)
}
