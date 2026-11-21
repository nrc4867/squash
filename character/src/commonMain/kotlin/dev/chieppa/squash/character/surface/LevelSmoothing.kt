package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.collections.IntList
import kotlin.math.abs

/** How many times a level is eased toward its neighbours' near its zero, and how far each time. */
internal const val NEAR_ZERO_STEPS = 6
private const val LEVEL_FACTOR = 0.5f

/** How much a fitted plane's slope is held toward none, as a share of the squared spread of the neighbours it is fitted to. */
private const val SLOPE_HOLD = 0.001f

/** Moving vertices one piece of smoothing work takes. */
private const val SMOOTHING_CHUNK = 1024

/**
 * [values], one a vertex of a mesh of [vertexCount] vertices and [triangles], eased where it lies within [band] of zero, so the curve
 * where it is zero runs smoothly across the cells, without the steps a value read from shares that change within a cell leaves. Values
 * further from zero than [band] stay as they are. Without [positions], each vertex is eased toward the mean of its neighbours along the
 * triangles' sides; with them, three a vertex, toward what a plane fitted through its own and its neighbours' values gives at the vertex,
 * which leaves a value changing evenly across the surface as it is however uneven the mesh, where a mean would bend it. No value moves
 * further than [shift] from where it was, so a band of positive values narrower than [band], a strap's, keeps its sign.
 */
fun smoothedNearZero(
    values: FloatArray,
    triangles: IntArray,
    vertexCount: Int,
    band: Float,
    positions: FloatArray? = null,
    shift: Float = Float.POSITIVE_INFINITY,
): FloatArray = NearZeroSmoothing(values, triangles, vertexCount, band, positions, shift).run()

/**
 * The work of [smoothedNearZero], in batches whose pieces are independent of one another, handed out by [next] until it gives null, then
 * read from [result]. Each of its [steps] eases every moving vertex from the values the step before left, so the pieces of a step may run
 * at once; more steps ease over a wider stretch of the mesh.
 */
class NearZeroSmoothing(
    private val values: FloatArray,
    private val triangles: IntArray,
    private val vertexCount: Int,
    private val band: Float,
    private val positions: FloatArray? = null,
    private val shift: Float = Float.POSITIVE_INFINITY,
    private val steps: Int = NEAR_ZERO_STEPS,
) {
    private val start = IntArray(vertexCount + 1)
    private var links = IntArray(0)
    private var moving = IntArray(0)
    private var current = FloatArray(0)
    private var following = FloatArray(0)
    private var step = -1

    /** The eased values, once [next] has given null. */
    val result: FloatArray get() = current

    fun next(): List<() -> Unit>? {
        step++
        return when {
            step == 0 -> listOf({ linked() })
            step <= steps -> {
                if (step > 1) current = following.also { following = current }
                chunked(moving.size, SMOOTHING_CHUNK) { from, to -> ease(from, to) }.ifEmpty { listOf({}) }
            }
            step == steps + 1 -> {
                current = following.also { following = current }
                for (v in moving) current[v] = current[v].coerceIn(values[v] - shift, values[v] + shift)
                null
            }
            else -> null
        }
    }

    /** Every batch run here in turn, then [result]. */
    fun run(): FloatArray {
        while (true) (next() ?: break).forEach { it() }
        return result
    }

    private fun linked() {
        for (k in triangles.indices) start[triangles[k] + 1] += 2
        for (v in 0 until vertexCount) start[v + 1] += start[v]
        links = IntArray(start[vertexCount])
        val filled = start.copyOf(vertexCount)
        for (tri in 0 until triangles.size / 3) for (c in 0 until 3) {
            val v = triangles[tri * 3 + c]
            links[filled[v]++] = triangles[tri * 3 + (c + 1) % 3]
            links[filled[v]++] = triangles[tri * 3 + (c + 2) % 3]
        }
        val near = IntList()
        for (v in 0 until vertexCount) if (abs(values[v]) < band) near.add(v)
        moving = near.toArray()
        current = values.copyOf()
        following = values.copyOf()
    }

    /** Moving vertices [from] until [to] eased one step from [current] into [following]. */
    private fun ease(from: Int, to: Int) {
        val fit = if (positions != null) PlaneFit(positions) else null
        for (i in from until to) {
            val v = moving[i]
            if (start[v + 1] == start[v]) {
                following[v] = current[v]
                continue
            }
            val target = fit?.at(v, links, start[v], start[v + 1], current) ?: run {
                var sum = 0f
                for (j in start[v] until start[v + 1]) sum += current[links[j]]
                sum / (start[v + 1] - start[v])
            }
            following[v] = current[v] + LEVEL_FACTOR * (target - current[v])
        }
    }
}

/** Least-squares planes through a vertex's and its neighbours' values over [positions], their slope held a little toward none. */
private class PlaneFit(private val positions: FloatArray) {
    private val m = DoubleArray(16)
    private val r = DoubleArray(4)
    private val basis = DoubleArray(4)
    private val x = DoubleArray(4)

    /** The value at vertex [v] of the plane fitted through it and [links] from [from] until [to], from [values]. */
    fun at(v: Int, links: IntArray, from: Int, to: Int, values: FloatArray): Float {
        m.fill(0.0)
        r.fill(0.0)
        add(0.0, 0.0, 0.0, values[v].toDouble())
        var spread = 0.0
        for (j in from until to) {
            val n = links[j]
            val dx = (positions[n * 3] - positions[v * 3]).toDouble()
            val dy = (positions[n * 3 + 1] - positions[v * 3 + 1]).toDouble()
            val dz = (positions[n * 3 + 2] - positions[v * 3 + 2]).toDouble()
            spread += dx * dx + dy * dy + dz * dz
            add(dx, dy, dz, values[n].toDouble())
        }
        val hold = SLOPE_HOLD * spread
        for (k in 1 until 4) m[k * 4 + k] += hold
        return solvedFirst()?.toFloat() ?: values[v]
    }

    private fun add(dx: Double, dy: Double, dz: Double, value: Double) {
        basis[0] = 1.0
        basis[1] = dx
        basis[2] = dy
        basis[3] = dz
        for (a in 0 until 4) {
            r[a] += basis[a] * value
            for (b in 0 until 4) m[a * 4 + b] += basis[a] * basis[b]
        }
    }

    /** The first unknown of m x = r by elimination with partial pivoting; null where m is singular. */
    private fun solvedFirst(): Double? {
        for (col in 0 until 4) {
            var pivot = col
            for (row in col + 1 until 4) if (abs(m[row * 4 + col]) > abs(m[pivot * 4 + col])) pivot = row
            if (abs(m[pivot * 4 + col]) < TINY) return null
            if (pivot != col) {
                for (k in 0 until 4) m[col * 4 + k] = m[pivot * 4 + k].also { m[pivot * 4 + k] = m[col * 4 + k] }
                r[col] = r[pivot].also { r[pivot] = r[col] }
            }
            for (row in col + 1 until 4) {
                val f = m[row * 4 + col] / m[col * 4 + col]
                for (k in col until 4) m[row * 4 + k] -= f * m[col * 4 + k]
                r[row] -= f * r[col]
            }
        }
        for (row in 3 downTo 0) {
            var sum = r[row]
            for (k in row + 1 until 4) sum -= m[row * 4 + k] * x[k]
            x[row] = sum / m[row * 4 + row]
        }
        return x[0]
    }

    private companion object {
        const val TINY = 1e-18
    }
}
