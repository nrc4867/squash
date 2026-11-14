package dev.chieppa.squash.character.surface

/** How many times a level is eased toward its neighbours' near its zero, and how far each time. */
private const val LEVEL_STEPS = 6
private const val LEVEL_FACTOR = 0.5f

/**
 * [values], one a vertex of a mesh of [vertexCount] vertices and [triangles], eased toward the mean of each vertex's neighbours along
 * the triangles' sides where it lies within [band] of zero, so the curve where it is zero runs smoothly across the cells, without the
 * steps a value read from shares that change within a cell leaves. Values further from zero than [band] stay as they are.
 */
fun smoothedNearZero(values: FloatArray, triangles: IntArray, vertexCount: Int, band: Float): FloatArray {
    val start = IntArray(vertexCount + 1)
    for (k in triangles.indices) start[triangles[k] + 1] += 2
    for (v in 0 until vertexCount) start[v + 1] += start[v]
    val links = IntArray(start[vertexCount])
    val filled = start.copyOf(vertexCount)
    for (tri in 0 until triangles.size / 3) for (c in 0 until 3) {
        val v = triangles[tri * 3 + c]
        links[filled[v]++] = triangles[tri * 3 + (c + 1) % 3]
        links[filled[v]++] = triangles[tri * 3 + (c + 2) % 3]
    }
    val moving = (0 until vertexCount).filter { kotlin.math.abs(values[it]) < band }.toIntArray()
    val current = values.copyOf()
    val next = FloatArray(moving.size)
    repeat(LEVEL_STEPS) {
        for (i in moving.indices) {
            val v = moving[i]
            val n = start[v + 1] - start[v]
            if (n == 0) {
                next[i] = current[v]
                continue
            }
            var sum = 0f
            for (j in start[v] until start[v + 1]) sum += current[links[j]]
            next[i] = current[v] + LEVEL_FACTOR * (sum / n - current[v])
        }
        for (i in moving.indices) current[moving[i]] = next[i]
    }
    return current
}
