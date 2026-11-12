package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.max

/**
 * One digit as a run joined to a field sees it, in the part's own axes: a straight axis from [root] along [direction], unit length,
 * [length] long, [radius] thick at its root, reaching [beyond] further past its tip where a claw stands. Shares along it run from 0 at
 * the root to 1 at the tip.
 */
data class DigitAxis(val root: Vec3, val direction: Vec3, val length: Float, val radius: Float, val beyond: Float = 0f) {
    /** How far along the digit [p] lies, as a share of its length. */
    fun share(p: Vec3): Float = ((p - root) dot direction) / length

    /** The point on the axis at [share]. */
    fun at(share: Float): Vec3 = root + direction * (length * share)

    /** How far [p] lies from the axis, between a radius behind the root and the tip, or the claw's point past it. */
    fun distance(p: Vec3): Float {
        val along = ((p - root) dot direction).coerceIn(-radius, length + beyond)
        return (p - (root + direction * along)).length
    }
}

/**
 * Which digit of [digits] owns each point of a surface they share with a field: a digit owns what lies past [seam] along it, as a share
 * of its length, inside its own cell (nearer its axis than any other digit's) and within [reach] of its radii from its axis. The field's
 * own run shows the rest.
 */
class DigitClaims(val digits: List<DigitAxis>, val seam: FloatArray, val reach: Float) {
    init {
        require(seam.size == digits.size) { "${seam.size} seams for ${digits.size} digits" }
    }

    /** The digit whose cell [p] lies in: the one whose axis lies nearest. */
    fun nearest(p: Vec3): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        digits.forEachIndexed { i, digit ->
            val d = digit.distance(p)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    /** The digit that owns [p] with [margin] radii more past the seam than the claim's own, or null. */
    fun owner(p: Vec3, margin: Float = 0f): Int? {
        val i = nearest(p)
        val digit = digits[i]
        val past = digit.share(p) - seam[i]
        val within = digit.distance(p) <= digit.radius * reach
        return if (past * digit.length >= digit.radius * margin && within) i else null
    }

    /**
     * How much [p]'s bones follow each digit rather than the field's own run, with the shares round it summing to that: each digit's
     * share eases in over [ease] radii either side of its seam and out from the claim's reach to [out] radii from its axis, so the field
     * away from a digit stays put, and digits whose axes lie within [across] of their radii of the nearest's share it, as a web between
     * two touching digits does.
     */
    fun follow(p: Vec3, ease: Float, across: Float, out: Float): FloatArray {
        val distances = FloatArray(digits.size) { digits[it].distance(p) }
        val nearest = distances.min()
        val lateral = FloatArray(digits.size) { i ->
            val width = digits[i].radius * across
            smoothstep(width, 0f, distances[i] - nearest)
        }
        val total = max(lateral.sum(), TINY)
        return FloatArray(digits.size) { i ->
            val digit = digits[i]
            val band = digit.radius * ease / digit.length
            val near = smoothstep(digit.radius * out, digit.radius * reach, distances[i])
            lateral[i] / total * near * smoothstep(seam[i] - band, seam[i] + band, digit.share(p))
        }
    }

    private companion object {
        const val TINY = 1e-9f
    }
}
