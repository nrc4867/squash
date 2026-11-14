package dev.chieppa.squash.geom

import kotlin.math.sqrt

/** Area-weighted vertex normals of [count] vertices at [positions], three values each, from [triangles] wound to face out. */
fun vertexNormals(positions: FloatArray, triangles: IntArray, count: Int): FloatArray {
    val out = FloatArray(count * 3)
    for (t in 0 until triangles.size / 3) {
        val a = triangles[t * 3]
        val b = triangles[t * 3 + 1]
        val c = triangles[t * 3 + 2]
        val ux = positions[b * 3] - positions[a * 3]
        val uy = positions[b * 3 + 1] - positions[a * 3 + 1]
        val uz = positions[b * 3 + 2] - positions[a * 3 + 2]
        val wx = positions[c * 3] - positions[a * 3]
        val wy = positions[c * 3 + 1] - positions[a * 3 + 1]
        val wz = positions[c * 3 + 2] - positions[a * 3 + 2]
        val nx = uy * wz - uz * wy
        val ny = uz * wx - ux * wz
        val nz = ux * wy - uy * wx
        for (v in intArrayOf(a, b, c)) {
            out[v * 3] += nx
            out[v * 3 + 1] += ny
            out[v * 3 + 2] += nz
        }
    }
    for (v in 0 until count) unit(out, v)
    return out
}

/**
 * [normals], three values a vertex, turned as the surface [triangles] make turns between [before] and [after]: each normal takes the
 * change in its vertex's mesh normal, so a smooth normal stays smooth where the surface did not move and follows it where it did.
 */
fun turnedNormals(normals: FloatArray, before: FloatArray, after: FloatArray, triangles: IntArray): FloatArray {
    val count = normals.size / 3
    val was = vertexNormals(before, triangles, count)
    val now = vertexNormals(after, triangles, count)
    val out = normals.copyOf()
    for (v in 0 until count) {
        var moved = false
        for (k in 0 until 3) if (before[v * 3 + k] != after[v * 3 + k]) moved = true
        if (!moved && was[v * 3] == now[v * 3] && was[v * 3 + 1] == now[v * 3 + 1] && was[v * 3 + 2] == now[v * 3 + 2]) continue
        for (k in 0 until 3) out[v * 3 + k] += now[v * 3 + k] - was[v * 3 + k]
        unit(out, v)
    }
    return out
}

private fun unit(values: FloatArray, v: Int) {
    val x = values[v * 3]
    val y = values[v * 3 + 1]
    val z = values[v * 3 + 2]
    val length = sqrt(x * x + y * y + z * z)
    if (length <= 0f) return
    values[v * 3] = x / length
    values[v * 3 + 1] = y / length
    values[v * 3 + 2] = z / length
}
