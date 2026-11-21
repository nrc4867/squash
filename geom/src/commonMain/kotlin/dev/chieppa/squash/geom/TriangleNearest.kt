package dev.chieppa.squash.geom

/** The point of triangle [a], [b], [c] nearest [p], with the weights of its three corners that make it. */
fun nearestOnTriangle(p: Vec3, a: Vec3, b: Vec3, c: Vec3): Pair<Vec3, Vec3> {
    val ab = b - a
    val ac = c - a
    val ap = p - a
    val d1 = ab dot ap
    val d2 = ac dot ap
    if (d1 <= 0f && d2 <= 0f) return a to Vec3(1f, 0f, 0f)
    val bp = p - b
    val d3 = ab dot bp
    val d4 = ac dot bp
    if (d3 >= 0f && d4 <= d3) return b to Vec3(0f, 1f, 0f)
    val vc = d1 * d4 - d3 * d2
    if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
        val t = d1 / (d1 - d3)
        return (a + ab * t) to Vec3(1f - t, t, 0f)
    }
    val cp = p - c
    val d5 = ab dot cp
    val d6 = ac dot cp
    if (d6 >= 0f && d5 <= d6) return c to Vec3(0f, 0f, 1f)
    val vb = d5 * d2 - d1 * d6
    if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
        val t = d2 / (d2 - d6)
        return (a + ac * t) to Vec3(1f - t, 0f, t)
    }
    val va = d3 * d6 - d5 * d4
    if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
        val t = (d4 - d3) / ((d4 - d3) + (d5 - d6))
        return (b + (c - b) * t) to Vec3(0f, 1f - t, t)
    }
    val total = 1f / (va + vb + vc)
    val v = vb * total
    val w = vc * total
    return (a + ab * v + ac * w) to Vec3(1f - v - w, v, w)
}
