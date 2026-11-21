package dev.chieppa.squash.rig

import kotlin.math.abs

/** How many times a push is eased toward each vertex's neighbours' along a mesh's triangles, so the mesh moves as one sheet. */
private const val PUSH_STEPS = 8
private const val PUSH_FACTOR = 0.5f

/** [push], three values a vertex, eased toward each vertex's neighbours along [triangles], never below what it held. */
internal fun eased(push: FloatArray, triangles: IntArray, corners: Int) {
    val sum = FloatArray(corners * 3)
    val count = IntArray(corners)
    repeat(PUSH_STEPS) {
        sum.fill(0f)
        count.fill(0)
        for (tri in 0 until triangles.size / 3) for (c in 0 until 3) {
            val a = triangles[tri * 3 + c]
            val b = triangles[tri * 3 + (c + 1) % 3]
            for (k in 0 until 3) {
                sum[a * 3 + k] += push[b * 3 + k]
                sum[b * 3 + k] += push[a * 3 + k]
            }
            count[a]++
            count[b]++
        }
        for (v in 0 until corners) {
            if (count[v] == 0) continue
            for (k in 0 until 3) {
                val mean = sum[v * 3 + k] / count[v]
                val next = push[v * 3 + k] + PUSH_FACTOR * (mean - push[v * 3 + k])
                push[v * 3 + k] = if (abs(next) > abs(push[v * 3 + k])) next else push[v * 3 + k]
            }
        }
    }
}
