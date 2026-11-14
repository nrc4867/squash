package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.field.smoothMin

/** How many members a vertex holds a share of at most. */
const val HELD = 4

/**
 * How much of the surface at a point each of a block's members holds, from their own fields there, [owns], their [parents] among them
 * (-1 for none) and the [blends] they join them by, written into [out]; they sum to one. Across a smooth union the child holds the
 * union's own mixing weight, clamp(0.5 + 0.5 (d_parent − d_child) / blend, 0, 1), and the parent what each of its children leaves it;
 * a member counts only as far as its own term of the union lies within [band] of it plus its own [reaches], so a run far from the point
 * holds none, and the shares change smoothly wherever the surface does.
 */
internal fun surfaceShares(owns: FloatArray, parents: IntArray, blends: FloatArray, reaches: FloatArray, band: Float, out: FloatArray) {
    val slots = owns.size
    val terms = FloatArray(slots)
    var joined = Float.MAX_VALUE
    for (s in 0 until slots) {
        val parent = parents[s]
        terms[s] = if (parent >= 0 && blends[s] > 0f) smoothMin(owns[parent], owns[s], blends[s]) else owns[s]
        if (terms[s] < joined) joined = terms[s]
    }
    val near = FloatArray(slots) { s -> nearness(terms[s] - joined, reaches[s] + band) }
    val mixing = FloatArray(slots) { s ->
        val parent = parents[s]
        if (parent >= 0 && blends[s] > 0f) (0.5f + 0.5f * (owns[parent] - owns[s]) / blends[s]).coerceIn(0f, 1f) else 1f
    }
    var total = 0f
    for (s in 0 until slots) {
        var share = near[s] * mixing[s]
        for (c in 0 until slots) if (parents[c] == s && blends[c] > 0f) share *= 1f - mixing[c] * near[c]
        out[s] = share
        total += share
    }
    if (total <= 0f) {
        for (s in 0 until slots) out[s] = if (terms[s] == joined) 1f else 0f
        return
    }
    for (s in 0 until slots) out[s] /= total
}

/** 1 at 0, falling smoothly to 0 at [reach]. */
private fun nearness(x: Float, reach: Float): Float {
    if (reach <= 0f) return if (x <= 0f) 1f else 0f
    val t = (x / reach).coerceIn(0f, 1f)
    return 1f - t * t * (3f - 2f * t)
}
