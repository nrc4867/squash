package dev.chieppa.squash.geom.blend

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** About how many surface samples each patch's field is fitted to. */
const val FIELD_SAMPLES = 48

/** The most Newton steps a vertex takes toward the blended surface. */
private const val MOST_STEPS = 8

/** How close to its level a vertex stops, as a share of the radius. */
private const val CLOSE_ENOUGH = 1e-3

/** The furthest a vertex moves, as a multiple of how far the blend can grow the surface; where two gradients nearly cancel, Newton's step would run off. */
private const val FURTHEST = 2.0

/** Below this, as a share of the radius, the blend moves a vertex nowhere. */
private const val NO_MOVE = 1e-4

/**
 * Fillets where a part's patches cross, as a displacement that keeps every patch's grid: each patch gets a smooth field
 * ([HermiteField]), the fields of patches that [joins] are joined by [blend], and every vertex near a crossing steps along the
 * joined field's gradient until the joined field there equals what the plain union of the fields was at its rest place. A vertex
 * on the surface lands on the fillet; one buried inside another patch stays buried at the same depth; one away from every crossing
 * stays exactly where it was. Shading normals turn toward the fillet's as far as the blend moved the field there.
 */
class ImplicitBlend(
    val blend: GradientBlend,
    /** Whether the patches named so are filleted where they cross. */
    val joins: (String, String) -> Boolean = { _, _ -> true },
    /**
     * Whether the patch so named moves. One that does not still lends its field; the patches that move then cover the whole fillet,
     * a vertex buried in a still patch rising toward the fillet as far as the blend lowers the field there.
     */
    val moves: (String) -> Boolean = { true },
    private val samples: Int = FIELD_SAMPLES,
) {
    /** [patches] with the vertices near their crossings moved onto the fillet. [checkCancelled] runs between patches. */
    fun apply(patches: List<Patch>, checkCancelled: () -> Unit = {}): List<Patch> =
        apply(patches, { PatchSamples.of(patches[it], samples) }, checkCancelled)

    /**
     * [patches] with the vertices near their crossings moved onto the fillet, each patch's field fitted to [sampled] of its index:
     * samples taken where the patch's own grid gives outward normals, for a patch moved into another space.
     */
    fun apply(patches: List<Patch>, sampled: (Int) -> PatchSamples, checkCancelled: () -> Unit = {}): List<Patch> {
        val taking = patches.indices.filter { i -> patches.indices.any { j -> j != i && joins(patches[i].name, patches[j].name) } }
        if (taking.size < 2 || blend.radius <= 0f) return patches
        val fields = HashMap<Int, HermiteField>()
        for (i in taking) {
            checkCancelled()
            val samples = sampled(i)
            HermiteField.fit(samples.points, samples.normals)?.let { fields[i] = it }
        }
        val joined = Joined(fields.keys.sorted().map { patches[it].name to fields.getValue(it) })
        val rising = taking.any { !moves(patches[it].name) }
        return patches.mapIndexed { i, patch ->
            if (i !in fields || !moves(patch.name)) return@mapIndexed patch
            checkCancelled()
            moved(patch, joined, rising)
        }
    }

    private fun moved(patch: Patch, joined: Joined, rising: Boolean): Patch {
        val points = patch.points.copyOf()
        val normals = patch.proxyNormals?.copyOf()
        var any = false
        val gradient = DoubleArray(3)
        val count = patch.nu * patch.nv
        for (vertex in 0 until count) {
            val rest = patch.point(vertex)
            val near = joined.near(rest, blend.radius)
            if (!near.joinable) continue
            val rested = near.union(rest)
            val start = near.value(rest, gradient)
            val lowered = rested - start
            if (lowered < NO_MOVE * blend.radius) continue
            val share = (lowered / blend.reach).coerceIn(0.0, 1.0)
            val union = if (rising && rested < 0.0) rested * (1.0 - share) else rested
            var p = rest
            var value = start
            for (step in 0 until MOST_STEPS) {
                if (abs(value - union) < CLOSE_ENOUGH * blend.radius) break
                val squared = gradient[0] * gradient[0] + gradient[1] * gradient[1] + gradient[2] * gradient[2]
                if (squared == 0.0) break
                val scale = (value - union) / squared
                val next = Vec3((p.x - scale * gradient[0]).toFloat(), (p.y - scale * gradient[1]).toFloat(), (p.z - scale * gradient[2]).toFloat())
                val offset = next - rest
                val furthest = (FURTHEST * blend.reach).toFloat()
                p = if (offset.length > furthest) rest + offset * (furthest / offset.length) else next
                value = near.value(p, gradient)
            }
            any = true
            points[vertex * 3] = p.x
            points[vertex * 3 + 1] = p.y
            points[vertex * 3 + 2] = p.z
            if (normals != null) turnNormal(normals, vertex, gradient, share)
        }
        return if (any) patch.withPoints(points, normals) else patch
    }

    /** The shading normal at [vertex] turned toward the field's [gradient] by [share]. */
    private fun turnNormal(normals: FloatArray, vertex: Int, gradient: DoubleArray, share: Double) {
        val length = sqrt(gradient[0] * gradient[0] + gradient[1] * gradient[1] + gradient[2] * gradient[2])
        if (length == 0.0) return
        val at = vertex * 3
        val x = normals[at] * (1.0 - share) + gradient[0] / length * share
        val y = normals[at + 1] * (1.0 - share) + gradient[1] / length * share
        val z = normals[at + 2] * (1.0 - share) + gradient[2] / length * share
        val turned = sqrt(x * x + y * y + z * z)
        if (turned == 0.0) return
        normals[at] = (x / turned).toFloat()
        normals[at + 1] = (y / turned).toFloat()
        normals[at + 2] = (z / turned).toFloat()
    }

    /** The patches' fields by name. */
    private inner class Joined(private val fields: List<Pair<String, HermiteField>>) {
        fun near(p: Vec3, margin: Float): Near = Near(fields.filter { it.second.near(p, margin) })
    }

    /** The fields whose boxes reach a vertex, which alone can move it. */
    private inner class Near(private val fields: List<Pair<String, HermiteField>>) {
        private val pairs: List<Pair<Int, Int>> = fields.indices.flatMap { a ->
            (a + 1 until fields.size).filter { b -> joins(fields[a].first, fields[b].first) }.map { b -> a to b }
        }
        val joinable: Boolean get() = pairs.isNotEmpty()

        private val values = DoubleArray(fields.size)
        private val gradients = Array(fields.size) { DoubleArray(3) }
        private val shares = DoubleArray(2)

        private fun evaluate(p: Vec3) {
            for (i in fields.indices) values[i] = fields[i].second.at(p, gradients[i])
        }

        /** The plain union of the fields at [p]: the least of them. */
        fun union(p: Vec3): Double {
            evaluate(p)
            return values.min()
        }

        /** The joined field at [p], with its gradient written into [gradient]. */
        fun value(p: Vec3, gradient: DoubleArray): Double {
            evaluate(p)
            var best = Double.POSITIVE_INFINITY
            for (i in fields.indices) {
                if (values[i] < best) {
                    best = values[i]
                    gradients[i].copyInto(gradient)
                }
            }
            for ((a, b) in pairs) {
                val joined = blend.join(values[a], values[b], GradientBlend.cosine(gradients[a], gradients[b]), shares)
                if (joined < best) {
                    best = joined
                    for (c in 0 until 3) gradient[c] = shares[0] * gradients[a][c] + shares[1] * gradients[b][c]
                }
            }
            return best
        }
    }
}
