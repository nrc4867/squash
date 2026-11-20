package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.geom.Vec3

/** The three floats from [at] as a vector. */
internal fun FloatArray.vec(at: Int) = Vec3(this[at], this[at + 1], this[at + 2])

/** Writes [v] into the three floats from [at]. */
internal fun FloatArray.put(at: Int, v: Vec3) {
    this[at] = v.x
    this[at + 1] = v.y
    this[at + 2] = v.z
}

/** This vector made unit length, or [fallback] where it has none. */
internal fun Vec3.unitOr(fallback: Vec3): Vec3 = if (length > 1e-8f) normalized() else fallback
