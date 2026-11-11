package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.StripJoin
import dev.chieppa.squash.character.build.patchWithFirst
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import kotlin.math.floor

/**
 * [rigged] with every strip's vertices weighted across the strip: each column blends, ring by ring, the weights of its host's surface
 * under its foot, read between the four vertices round it, into the weights of its tube's vertex on the ring it meets, so the strip skins as one with both parts. [placed] gives
 * each rigged part as placed.
 */
internal fun withStripWeights(rigged: List<RigPart>, placed: (PartId) -> PlacedPart?): List<RigPart> {
    val byId = rigged.associateBy { it.id }
    return rigged.map { part ->
        val tube = placed(part.id) ?: return@map part
        if (tube.strips.isEmpty()) return@map part
        val bones = part.weights.bones.copyOf()
        val weights = part.weights.weights.copyOf()
        for (strip in tube.strips) {
            val host = placed(strip.host) ?: continue
            val hostWeights = byId[strip.host]?.weights ?: continue
            blendStrip(tube, strip, host, part.weights, hostWeights, bones, weights)
        }
        RigPart(part.id, part.rest, part.indices, PartWeights(part.id, bones, weights), part.mesh, part.origin, part.vertices, part.anchors, part.role, part.axis)
    }
}

/** The four vertices of [patch], starting at [first] in its part's mesh, round (u, v), each with its bilinear share. */
private fun around(patch: Patch, first: Int, u: Float, v: Float): List<Pair<Int, Float>> {
    val rings = patch.ringUs
    val ring = (0 until rings.size - 1).firstOrNull { u <= rings[it + 1] } ?: (rings.size - 2)
    val span = rings[ring + 1] - rings[ring]
    val t = if (span > 0f) ((u - rings[ring]) / span).coerceIn(0f, 1f) else 0f
    val across = if (patch.closedV) (v - floor(v)) * patch.segments else v.coerceIn(0f, 1f) * patch.segments
    val column = across.toInt().coerceAtMost(patch.segments - 1)
    val s = across - column
    val next = (column + 1) % patch.nv
    return listOf(
        first + patch.index(ring, column) to (1f - t) * (1f - s),
        first + patch.index(ring, next) to (1f - t) * s,
        first + patch.index(ring + 1, column) to t * (1f - s),
        first + patch.index(ring + 1, next) to t * s,
    )
}

private fun blendStrip(tube: PlacedPart, strip: StripJoin, host: PlacedPart, own: PartWeights, hostWeights: PartWeights, bones: IntArray, weights: FloatArray) {
    val (ring, ringFirst) = tube.patchWithFirst(strip.tube) ?: return
    val (patch, first) = tube.patchWithFirst(strip.patch) ?: return
    val (onHost, hostFirst) = host.patchWithFirst(strip.hostPatch) ?: return
    for (row in 0 until patch.nu) {
        val towardTube = if (strip.atStart) row.toFloat() / (patch.nu - 1) else 1f - row.toFloat() / (patch.nu - 1)
        val share = towardTube * towardTube * (3f - 2f * towardTube)
        for (column in 0 until patch.nv) {
            val foot = strip.foot.getOrNull(column) ?: continue
            val blend = HashMap<Int, Float>()
            for ((under, w) in around(onHost, hostFirst, foot.u, foot.v)) {
                hostWeights.of(under).forEach { (bone, weight) -> blend[bone] = (blend[bone] ?: 0f) + weight * w * (1f - share) }
            }
            own.of(ringFirst + ring.index(strip.ring, column)).forEach { (bone, w) -> blend[bone] = (blend[bone] ?: 0f) + w * share }
            val kept = blend.entries.sortedByDescending { it.value }.take(MAX_INFLUENCES)
            val total = kept.sumOf { it.value.toDouble() }.toFloat()
            val vertex = first + patch.index(row, column)
            for (slot in 0 until MAX_INFLUENCES) {
                val entry = kept.getOrNull(slot)
                bones[vertex * MAX_INFLUENCES + slot] = entry?.key ?: -1
                weights[vertex * MAX_INFLUENCES + slot] = if (entry != null && total > 0f) entry.value / total else 0f
            }
        }
    }
}
