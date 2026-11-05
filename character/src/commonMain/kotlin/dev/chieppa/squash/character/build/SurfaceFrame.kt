package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.MarkFlight
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt

/** How far either side of a place its directions are measured, as a share of the patch. */
private const val MEASURED_ACROSS = 0.02f

/** Directions shorter than this at a place, as at a pole, say nothing about which way. */
private const val LEAST_REACH = 1e-5f

/**
 * Directions at a place on a surface, each of length 1 and square to the others: [along] it as u grows, [across] it as v grows, and [out] of it. A mark
 * held in them turns and travels with the surface under its root, and on a mirrored surface comes out mirrored.
 */
data class SurfaceFrame(val along: Vec3, val across: Vec3, val out: Vec3) {
    /** Where [flight] from [root] lies. */
    fun placed(root: Vec3, flight: MarkFlight): Vec3 = root + along * flight.along + across * flight.across + out * flight.out

    /** How [point] lies from [root] in these directions. */
    fun flightTo(root: Vec3, point: Vec3): MarkFlight {
        val from = point - root
        return MarkFlight(from dot along, from dot across, from dot out)
    }
}

/** The directions at [anchor] on this surface, or null when it holds no such patch. */
fun PartSurface.frameAt(anchor: Anchor): SurfaceFrame? {
    val patch = patches.firstOrNull { it.name == anchor.patch } ?: return null
    val rings = patch.ringUs
    val reachU = (rings.last() - rings.first()) * MEASURED_ACROSS
    val low = (anchor.u - reachU).coerceAtLeast(rings.first())
    val high = (anchor.u + reachU).coerceAtMost(rings.last())
    val before = if (patch.closedV) anchor.v - MEASURED_ACROSS else (anchor.v - MEASURED_ACROSS).coerceAtLeast(0f)
    val after = if (patch.closedV) anchor.v + MEASURED_ACROSS else (anchor.v + MEASURED_ACROSS).coerceAtMost(1f)
    val out = shown(patch.surfaceAt(anchor.u, anchor.v).normal).normalized()
    val rawAlong = shown(patch.positionAt(high, anchor.v) - patch.positionAt(low, anchor.v))
    val rawAcross = shown(patch.positionAt(anchor.u, after) - patch.positionAt(anchor.u, before))
    val flatAlong = rawAlong - out * (rawAlong dot out)
    val along = if (flatAlong.length > LEAST_REACH) flatAlong.normalized() else squareTo(out)
    val flatAcross = rawAcross - out * (rawAcross dot out) - along * (rawAcross dot along)
    val across = if (flatAcross.length > LEAST_REACH) flatAcross.normalized() else (out cross along).normalized()
    return SurfaceFrame(along, across, out)
}

private fun squareTo(direction: Vec3): Vec3 {
    val other = if (kotlin.math.abs(direction.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
    return (direction cross other).normalized()
}
