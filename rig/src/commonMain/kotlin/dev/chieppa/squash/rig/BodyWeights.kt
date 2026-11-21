package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3

/**
 * The body's weights are read over this many mean edge lengths of the part sat on past the nearest body vertex, so they ease between
 * its rings without blurring its joints' bands.
 */
private const val SMOOTH_SPACINGS = 1.5f

/** How many times a bound part's weights are eased toward its neighbouring vertices' along its own mesh. */
private const val WEIGHT_PASSES = 4

/** How far each pass moves a vertex's weights toward the mean of its neighbours'. */
private const val WEIGHT_EASE = 0.5f

private const val MIN_SPACING = 1e-4f

/** Weights read from the body for a part: each vertex's, and how it holds on to the body. */
internal class Followed(val weights: PartWeights, val hold: BodyHold)

/**
 * The rest vertices of [body] parts as one [BodySurface], with their weights, for reading what the body gives any point near it: each
 * vertex of a part bound to it takes the weights the body vertices round it carry, eased by the surface's kernel, then eased along the
 * part's own mesh over [WEIGHT_PASSES] passes, so two neighbouring vertices never take weights from different body parts that overlap
 * there, and a far vertex the nearest body vertex's alone.
 */
internal class BodyWeights(private val body: List<RigPart>) {
    private val starts = IntArray(body.size + 1).also { s -> body.forEachIndexed { i, part -> s[i + 1] = s[i] + part.vertexCount } }
    private val surface = BodySurface.of(body.map { it.rest }, body.map { it.mesh }, SMOOTH_SPACINGS * meanEdge(body.first()).coerceAtLeast(MIN_SPACING))

    fun follow(part: RigPart): Followed {
        val count = part.vertexCount
        val shares = Array(count) { HashMap<Int, Float>() }
        val standoff = FloatArray(count)
        for (v in 0 until count) {
            val p = part.rest.vec(v * 3)
            if (!surface.near(p) { k, kernel -> addWeights(k, kernel, shares[v]) }) addWeights(nearestByScan(p), 1f, shares[v])
            standoff[v] = surface.planeAt(p)?.standoff(p) ?: 0f
        }
        val eased = easedOver(shares, part.indices)
        val bones = IntArray(count * MAX_INFLUENCES) { -1 }
        val weights = FloatArray(count * MAX_INFLUENCES)
        for (v in 0 until count) Blend(eased[v]).write(v, bones, weights)
        return Followed(PartWeights(part.id, bones, weights), BodyHold(body.map { it.id }, surface.reach, standoff))
    }

    private fun addWeights(k: Int, amount: Float, into: MutableMap<Int, Float>) {
        val owner = ownerOf(k)
        val weights = body[owner].weights
        val v = k - starts[owner]
        for (slot in 0 until MAX_INFLUENCES) {
            val bone = weights.bones[v * MAX_INFLUENCES + slot]
            if (bone >= 0) into[bone] = (into[bone] ?: 0f) + weights.weights[v * MAX_INFLUENCES + slot] * amount
        }
    }

    private fun ownerOf(k: Int): Int {
        var i = 0
        while (k >= starts[i + 1]) i++
        return i
    }

    private fun nearestByScan(p: Vec3): Int {
        var best = 0
        var least = Float.MAX_VALUE
        body.forEachIndexed { i, part ->
            for (v in 0 until part.vertexCount) {
                val d = (part.rest.vec(v * 3) - p).length
                if (d < least) {
                    least = d
                    best = starts[i] + v
                }
            }
        }
        return best
    }
}

/** Each vertex's [shares], made to sum to one, eased toward its neighbours' along [triangles]. */
private fun easedOver(shares: Array<HashMap<Int, Float>>, triangles: IntArray): Array<Map<Int, Float>> {
    var current: Array<Map<Int, Float>> = Array(shares.size) { v ->
        val total = shares[v].values.sum()
        if (total > 0f) shares[v].mapValues { it.value / total } else shares[v]
    }
    val neighbours = Array(shares.size) { HashSet<Int>() }
    for (tri in 0 until triangles.size / 3) for (c in 0 until 3) {
        val a = triangles[tri * 3 + c]
        val b = triangles[tri * 3 + (c + 1) % 3]
        if (a < shares.size && b < shares.size && a != b) {
            neighbours[a] += b
            neighbours[b] += a
        }
    }
    repeat(WEIGHT_PASSES) {
        val before = current
        current = Array(before.size) { v ->
            val around = neighbours[v]
            if (around.isEmpty()) return@Array before[v]
            val next = HashMap<Int, Float>()
            before[v].forEach { (bone, w) -> next[bone] = w * (1f - WEIGHT_EASE) }
            val each = WEIGHT_EASE / around.size
            for (n in around) before[n].forEach { (bone, w) -> next[bone] = (next[bone] ?: 0f) + w * each }
            next
        }
    }
    return current
}

/** The mean length of the edges of [part]'s mesh. */
private fun meanEdge(part: RigPart): Float {
    val mesh = part.mesh
    val indices = mesh.indices
    if (indices.isEmpty()) return 0f
    var total = 0.0
    var count = 0
    for (tri in 0 until indices.size / 3) for (c in 0 until 3) {
        val a = indices[tri * 3 + c]
        val b = indices[tri * 3 + (c + 1) % 3]
        total += (mesh.position(a) - mesh.position(b)).length
        count++
    }
    return (total / count).toFloat()
}
