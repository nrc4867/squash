package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3

/** Below this a triangle's doubled area counts as none, as a pole's triangles have. */
private const val DEGENERATE_AREA = 1e-10f

fun FloatArray.point(vertex: Int) = Vec3(this[vertex * 3], this[vertex * 3 + 1], this[vertex * 3 + 2])

/** The doubled-area normal of triangle [t] of [indices] over [positions]. */
fun faceNormal(positions: FloatArray, indices: IntArray, t: Int): Vec3 {
    val a = positions.point(indices[t * 3])
    return (positions.point(indices[t * 3 + 1]) - a) cross (positions.point(indices[t * 3 + 2]) - a)
}

/**
 * The triangles of [part] that [posed] turns inside out: whose normal points against the rest normal carried by the bones
 * of its first corner. Triangles with no area are skipped.
 */
fun invertedTriangles(part: RigPart, posed: FloatArray, transforms: List<Affine>): Int {
    var inverted = 0
    for (t in 0 until part.indices.size / 3) {
        val rest = faceNormal(part.rest, part.indices, t)
        if (rest.length < DEGENERATE_AREA) continue
        val corner = part.indices[t * 3]
        val expected = part.weights.of(corner).fold(Vec3.ZERO) { sum, (bone, w) -> sum + transforms[bone].linear * rest * w }
        if ((faceNormal(posed, part.indices, t) dot expected) < 0f) inverted++
    }
    return inverted
}

/** Every edge of [indices] once, as vertex pairs. */
fun edges(indices: IntArray): Set<Pair<Int, Int>> {
    val out = HashSet<Pair<Int, Int>>()
    for (t in 0 until indices.size / 3) for (k in 0 until 3) {
        val a = indices[t * 3 + k]
        val b = indices[t * 3 + (k + 1) % 3]
        if (a != b) out += minOf(a, b) to maxOf(a, b)
    }
    return out
}

/** How far an edge's posed length strays from its rest length, as a share of the rest length. */
fun edgeChange(part: RigPart, posed: FloatArray, edge: Pair<Int, Int>): Float {
    val rest = (part.rest.point(edge.first) - part.rest.point(edge.second)).length
    val now = (posed.point(edge.first) - posed.point(edge.second)).length
    return if (rest <= 0f) 0f else kotlin.math.abs(now / rest - 1f)
}

/**
 * The volume [indices] enclose over [positions], each open end closed by a fan to the middle of its rim, so an open tube
 * such as a leg counts as closed at its socket.
 */
fun enclosedVolume(positions: FloatArray, indices: IntArray): Float {
    var six = 0.0
    fun add(a: Vec3, b: Vec3, c: Vec3) {
        six += (a dot (b cross c)).toDouble()
    }
    val directed = HashMap<Pair<Int, Int>, Int>()
    for (t in 0 until indices.size / 3) {
        val corners = IntArray(3) { indices[t * 3 + it] }
        add(positions.point(corners[0]), positions.point(corners[1]), positions.point(corners[2]))
        for (k in 0 until 3) {
            val a = corners[k]
            val b = corners[(k + 1) % 3]
            if (a != b) directed[a to b] = (directed[a to b] ?: 0) + 1
        }
    }
    val rim = directed.keys.filter { (a, b) -> (b to a) !in directed }
    val next = rim.associate { it.first to it.second }
    val left = next.keys.toMutableSet()
    while (left.isNotEmpty()) {
        val loop = mutableListOf(left.first())
        while (true) {
            val after = next[loop.last()] ?: break
            if (after == loop.first()) break
            loop += after
        }
        left -= loop.toSet()
        val middle = loop.fold(Vec3.ZERO) { sum, v -> sum + positions.point(v) } * (1f / loop.size)
        for (i in loop.indices) add(middle, positions.point(loop[(i + 1) % loop.size]), positions.point(loop[i]))
    }
    return (six / 6.0).toFloat()
}
