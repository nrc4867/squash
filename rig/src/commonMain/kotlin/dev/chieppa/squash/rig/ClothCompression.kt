package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.field.smoothstep

/** How much a garment's mesh shortens against rest, as a share, before it wrinkles at all, and where its wrinkles are full. */
private const val WRINKLE_FROM = 0.06f
private const val WRINKLE_FULL = 0.25f

/** How many times the bunching is smoothed over each corner's neighbours, so wrinkles gather in patches and not in specks. */
private const val SMOOTHING_PASSES = 6

/** How many indices a quad takes as two triangles, and where its four corners lie in order among them. */
internal const val QUAD_INDICES = 6
internal val QUAD_CORNERS = intArrayOf(0, 1, 4, 5)

/** The mean of [values] over quad [q]'s four corners in [triangles], the mesh's quads first, two triangles each. */
internal fun quadMean(values: FloatArray, triangles: IntArray, q: Int): Float {
    var sum = 0f
    for (k in QUAD_CORNERS) sum += values[triangles[q * QUAD_INDICES + k]]
    return sum / QUAD_CORNERS.size
}

/**
 * How much the cloth bunches at each of [count] vertices of a garment's drawn mesh, 0 to 1: at each of its [corners], the mean of how much
 * the edges of its [triangles], its quads first, two triangles each, shorten from their rest lengths at [rest] to their posed lengths at
 * [posed], three values a corner each, smoothed [SMOOTHING_PASSES] times over its neighbours and eased between [WRINKLE_FROM] and [WRINKLE_FULL]; at a
 * quad's middle the mean of its corners.
 */
fun clothBunching(triangles: IntArray, corners: Int, rest: FloatArray, posed: FloatArray, count: Int): FloatArray {
    val shortened = FloatArray(corners)
    val edges = IntArray(corners)
    for (t in 0 until triangles.size / 3) {
        for (k in 0 until 3) {
            val a = triangles[t * 3 + k]
            val b = triangles[t * 3 + (k + 1) % 3]
            val before = (rest.vec(b * 3) - rest.vec(a * 3)).length
            if (before <= 0f) continue
            val after = (posed.vec(b * 3) - posed.vec(a * 3)).length
            val shorter = maxOf(0f, 1f - after / before)
            shortened[a] += shorter
            shortened[b] += shorter
            edges[a]++
            edges[b]++
        }
    }
    for (v in 0 until corners) if (edges[v] > 0) shortened[v] /= edges[v]
    var smoothed = shortened
    repeat(SMOOTHING_PASSES) { smoothed = smoothedOnce(triangles, smoothed) }
    return FloatArray(count) { v ->
        val value = if (v < corners) {
            smoothed[v]
        } else {
            quadMean(smoothed, triangles, v - corners)
        }
        smoothstep(WRINKLE_FROM, WRINKLE_FULL, value)
    }
}

/** [values] each moved half way to the mean of its neighbours along [triangles]' edges. */
private fun smoothedOnce(triangles: IntArray, values: FloatArray): FloatArray {
    val near = FloatArray(values.size)
    val count = IntArray(values.size)
    for (t in 0 until triangles.size / 3) {
        for (k in 0 until 3) {
            val a = triangles[t * 3 + k]
            val b = triangles[t * 3 + (k + 1) % 3]
            near[a] += values[b]
            near[b] += values[a]
            count[a]++
            count[b]++
        }
    }
    return FloatArray(values.size) { v -> if (count[v] > 0) 0.5f * values[v] + 0.5f * near[v] / count[v] else values[v] }
}
