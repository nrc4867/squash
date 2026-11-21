package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** The directions round (2 degrees apart) and up (2 degrees apart, from straight down to straight up) a head's outside is read along. */
private const val READ_AROUND = 180
private const val READ_UP = 91
private const val STEP = 2f

/** How nearly a ray may run along a triangle's plane and still be read as meeting it, the sine of the angle between them. */
private const val GRAZING = 1e-3f

/** How far past a triangle's farthest corner a meeting may lie and be kept, so a meeting found by rounding alone is dropped. */
private const val HIT_SLACK = 1.001f


/** The rounds an unread direction takes its neighbours' reach in. */
private const val FILL_ROUNDS = 12

/**
 * A head's outside read along rays from [middle]: in each direction of a 2-degree grid, how far the farthest of the head's triangles the
 * ray meets lies, each triangle read only along the directions its corners span; a direction no triangle meets takes its neighbours'.
 * Read between directions, so a shell laid at it follows the surface itself, smoothly over the pole as anywhere.
 */
internal class HeadReach private constructor(private val reach: FloatArray) {
    fun at(azimuth: Float, elevation: Float): Float {
        val a = ((azimuth / 360f) - floor(azimuth / 360f)) * READ_AROUND
        val e = ((elevation + 90f) / STEP).coerceIn(0f, READ_UP - 1f)
        val a0 = a.toInt() % READ_AROUND
        val a1 = (a0 + 1) % READ_AROUND
        val e0 = e.toInt().coerceAtMost(READ_UP - 2)
        val fa = a - floor(a)
        val fe = e - e0
        fun bin(i: Int, j: Int) = reach[j * READ_AROUND + i]
        val low = bin(a0, e0) + (bin(a1, e0) - bin(a0, e0)) * fa
        val high = bin(a0, e0 + 1) + (bin(a1, e0 + 1) - bin(a0, e0 + 1)) * fa
        return low + (high - low) * fe
    }

    companion object {
        /** [head]'s outside as it shows at rest, read round [middle]. */
        fun of(head: PlacedPart, middle: Vec3): HeadReach {
            val reach = FloatArray(READ_AROUND * READ_UP)
            val mesh = head.surface.mesh
            val corners = List(mesh.vertexCount) { head.origin + head.surface.shown(mesh.position(it)) - middle }
            val indices = mesh.indices
            for (t in 0 until indices.size / 3) {
                val a = corners[indices[t * 3]]
                val b = corners[indices[t * 3 + 1]]
                val c = corners[indices[t * 3 + 2]]
                read(a, b, c, reach)
            }
            fill(reach)
            val top = (READ_UP - 1) * READ_AROUND
            val mean = (0 until READ_AROUND).map { reach[top + it] }.average().toFloat()
            for (i in 0 until READ_AROUND) reach[top + i] = mean
            return HeadReach(reach)
        }

        /** Every grid direction whose ray from the middle meets the triangle [a], [b], [c] takes the farthest meeting. */
        private fun read(a: Vec3, b: Vec3, c: Vec3, reach: FloatArray) {
            val angles = listOf(a, b, c).map { Scalp.anglesOf(it) }
            val low = angles.minOf { it.second }
            val high = angles.maxOf { it.second }
            val ups = listOf(Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f))
            val pole = ups.firstOrNull { hit(it, a, b, c) != null }
            var azimuths = angles.map { it.first }
            if (azimuths.max() - azimuths.min() > 180f) azimuths = azimuths.map { if (it < 180f) it + 360f else it }
            val fromA = if (pole != null) 0f else azimuths.min()
            val toA = if (pole != null) 360f else azimuths.max()
            val fromE = if (pole != null && pole.y < 0f) -90f else low
            val toE = if (pole != null && pole.y > 0f) 90f else high
            val j0 = ceil((fromE + 90f) / STEP).toInt().coerceIn(0, READ_UP - 1)
            val j1 = floor((toE + 90f) / STEP).toInt().coerceIn(0, READ_UP - 1)
            val i0 = ceil(fromA / STEP).toInt()
            val i1 = floor(toA / STEP).toInt()
            val farthest = maxOf(a.length, b.length, c.length) * HIT_SLACK
            for (j in j0..j1) {
                val elevation = -90f + j * STEP
                for (i in i0..i1) {
                    val azimuth = i * STEP
                    val d = Scalp.direction(azimuth, elevation)
                    val t = hit(d, a, b, c)?.takeIf { it <= farthest } ?: continue
                    val k = j * READ_AROUND + ((i % READ_AROUND) + READ_AROUND) % READ_AROUND
                    if (t > reach[k]) reach[k] = t
                }
            }
        }

        /** How far along [d] from the middle its ray meets the triangle [a], [b], [c], or null where it misses (Möller and Trumbore). */
        private fun hit(d: Vec3, a: Vec3, b: Vec3, c: Vec3): Float? {
            val e1 = b - a
            val e2 = c - a
            val p = d cross e2
            val det = e1 dot p
            if (abs(det) < GRAZING * e1.length * e2.length) return null
            val inv = 1f / det
            val s = Vec3.ZERO - a
            val u = (s dot p) * inv
            if (u < -1e-5f || u > 1f + 1e-5f) return null
            val q = s cross e1
            val v = (d dot q) * inv
            if (v < -1e-5f || u + v > 1f + 1e-5f) return null
            val t = (e2 dot q) * inv
            return if (t > 0f) t else null
        }

        private fun fill(reach: FloatArray) {
            repeat(FILL_ROUNDS) {
                val before = reach.copyOf()
                var empty = 0
                for (j in 0 until READ_UP) for (i in 0 until READ_AROUND) {
                    val k = j * READ_AROUND + i
                    if (before[k] > 0f) continue
                    var sum = 0f
                    var n = 0
                    for ((di, dj) in NEIGHBOURS) {
                        val jj = j + dj
                        if (jj !in 0 until READ_UP) continue
                        val v = before[jj * READ_AROUND + (i + di + READ_AROUND) % READ_AROUND]
                        if (v > 0f) {
                            sum += v
                            n++
                        }
                    }
                    if (n > 0) reach[k] = sum / n else empty++
                }
                if (empty == 0) return
            }
            val known = reach.filter { it > 0f }
            val mean = if (known.isEmpty()) 0f else known.average().toFloat()
            for (k in reach.indices) if (reach[k] == 0f) reach[k] = mean
        }

        private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
