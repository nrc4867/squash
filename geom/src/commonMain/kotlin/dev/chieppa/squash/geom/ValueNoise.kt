package dev.chieppa.squash.geom

import kotlin.math.floor

/**
 * Smooth noise in space, from -1 to 1: a value hashed at each corner of the unit lattice, eased between corners with a smoothstep, so it
 * is continuous everywhere and repeats nowhere a body reaches. The same point and [seed] always give the same value.
 */
fun valueNoise(x: Float, y: Float, z: Float, seed: Int = 0): Float {
    val ix = floor(x).toInt()
    val iy = floor(y).toInt()
    val iz = floor(z).toInt()
    val fx = eased(x - ix)
    val fy = eased(y - iy)
    val fz = eased(z - iz)
    fun corner(dx: Int, dy: Int, dz: Int) = latticeValue(ix + dx, iy + dy, iz + dz, seed)
    val x00 = mix(corner(0, 0, 0), corner(1, 0, 0), fx)
    val x10 = mix(corner(0, 1, 0), corner(1, 1, 0), fx)
    val x01 = mix(corner(0, 0, 1), corner(1, 0, 1), fx)
    val x11 = mix(corner(0, 1, 1), corner(1, 1, 1), fx)
    return mix(mix(x00, x10, fy), mix(x01, x11, fy), fz)
}

/** [valueNoise] at [point] measured in steps of [scale]. */
fun valueNoise(point: Vec3, scale: Float, seed: Int = 0): Float = valueNoise(point.x / scale, point.y / scale, point.z / scale, seed)

private fun eased(t: Float) = t * t * (3f - 2f * t)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** A value from -1 to 1 for one lattice corner, from a 32-bit integer hash of its place. */
private fun latticeValue(x: Int, y: Int, z: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 32767.5f - 1f
}
