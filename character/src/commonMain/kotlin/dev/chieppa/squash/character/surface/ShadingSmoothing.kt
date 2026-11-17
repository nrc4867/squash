package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.surface.SurfaceMesh
import kotlin.math.sqrt

/** How many times each of the mesh's own vertices' normal and the runs' own shading are averaged with its neighbours' everywhere. */
internal const val SMOOTHING_STEPS = 3

/** How many more times a vertex's normal and the runs' own shading are averaged as far as no part's own shading holds it, as across a join. */
internal const val JOIN_SMOOTHING_STEPS = 16

/** How much of a vertex its runs' own shading holds for it to count as held whole, and be left out of the joins' smoothing. */
private const val HELD_WHOLE = 0.99f

/** How far each step moves a value toward its neighbours' mean. */
private const val SMOOTHING_FACTOR = 0.5f

private const val TINY = 1e-12f

/**
 * The normals and own shading [SurfaceFinish] smooths at [mesh]'s own vertices, along the sides of [fan]'s quads, with [fallback] the
 * normal a vertex takes where its own comes out of no length. Each smoothing step writes into spare arrays, taken in by [swap] before
 * the next batch reads them.
 */
internal class ShadingSmoothing(private val mesh: SurfaceMesh, private val fan: QuadFan, private val fallback: FloatArray) {
    private val corners = fan.corners
    private val quads = fan.quads
    private var start = IntArray(0)
    private var links = IntArray(0)
    private var spare = FloatArray(corners * 3)
    private var spareOwned = FloatArray(corners * 3)
    private var swapping = false

    /** Each vertex's surface normal. */
    var normals = FloatArray(corners * 3)
        private set

    /** Each vertex's owning runs' own shading, and how much of it they hold (see [ownShading]). */
    var owned = FloatArray(corners * 3)
        private set
    val held = FloatArray(corners)

    /** The groups [group] found. */
    var groups: List<IntArray> = emptyList()
        private set

    /** Has the next [swap] take in what the batch of [step]s about to run writes. */
    fun swapNext() {
        swapping = true
    }

    /** Takes in what the last batch of [step]s wrote. */
    fun swap() {
        if (!swapping) return
        normals = spare.also { spare = normals }
        owned = spareOwned.also { spareOwned = owned }
        swapping = false
    }

    /**
     * Each of the mesh's vertices' neighbours along its faces' sides, never across a quad, so smoothing spreads alike every way round:
     * for each quad, then each triangle standing alone, round it, in order, the corner after it and the corner before it.
     */
    fun link() {
        start = IntArray(corners + 1)
        for (q in 0 until quads) for (k in 0 until 4) start[fan.corner(q, k) + 1] += 2
        for (s in 0 until fan.singles) for (k in 0 until 3) start[fan.singleCorner(s, k) + 1] += 2
        for (v in 0 until corners) start[v + 1] += start[v]
        links = IntArray(start[corners])
        val filled = IntArray(corners)
        for (q in 0 until quads) for (k in 0 until 4) {
            val v = fan.corner(q, k)
            links[start[v] + filled[v]++] = fan.corner(q, (k + 1) % 4)
            links[start[v] + filled[v]++] = fan.corner(q, (k + 3) % 4)
        }
        for (s in 0 until fan.singles) for (k in 0 until 3) {
            val v = fan.singleCorner(s, k)
            links[start[v] + filled[v]++] = fan.singleCorner(s, (k + 1) % 3)
            links[start[v] + filled[v]++] = fan.singleCorner(s, (k + 2) % 3)
        }
    }

    /** The normal of the mesh's vertices [from] until [to], from the corners of the quads round each by area, made one long. */
    fun face(from: Int, to: Int) {
        val p = mesh.positions
        for (v in from until to) {
            var x = 0f
            var y = 0f
            var z = 0f
            val a = v * 3
            var k = start[v]
            while (k < start[v + 1]) {
                val b = links[k] * 3
                val c = links[k + 1] * 3
                val ux = p[b] - p[a]
                val uy = p[b + 1] - p[a + 1]
                val uz = p[b + 2] - p[a + 2]
                val vx = p[c] - p[a]
                val vy = p[c + 1] - p[a + 1]
                val vz = p[c + 2] - p[a + 2]
                x += uy * vz - uz * vy
                y += uz * vx - ux * vz
                z += ux * vy - uy * vx
                k += 2
            }
            normals[a] = x
            normals[a + 1] = y
            normals[a + 2] = z
            unit(normals, v, fallback)
        }
    }

    /** One step of averaging vertices [from] until [to] with their neighbours, into the spare arrays. */
    /** One step of averaging vertices [from] until [to] with their neighbours, into the spare arrays. */
    fun step(from: Int, to: Int) {
        for (v in from until to) {
            average(normals, spare, v, SMOOTHING_FACTOR)
            unit(spare, v, normals)
            average(owned, spareOwned, v, SMOOTHING_FACTOR)
        }
    }

    /**
     * Finds [groups]: the vertices no run's own shading holds whole ([HELD_WHOLE]), in groups joined along the quads' sides. Smoothing
     * one group reads only its own vertices and ones that stay as they are, so the groups are smoothed apart.
     */
    fun group() {
        val seen = BooleanArray(corners)
        val groups = ArrayList<IntArray>()
        val queue = IntArray(corners)
        for (first in 0 until corners) {
            if (seen[first] || held[first] >= HELD_WHOLE) continue
            var head = 0
            var tail = 0
            queue[tail++] = first
            seen[first] = true
            while (head < tail) {
                val v = queue[head++]
                for (k in start[v] until start[v + 1]) {
                    val n = links[k]
                    if (!seen[n] && held[n] < HELD_WHOLE) {
                        seen[n] = true
                        queue[tail++] = n
                    }
                }
            }
            groups += queue.copyOf(tail)
        }
        this.groups = groups
    }

    /** Every vertex of [groups], one after another, for [joinStep]: smoothing one reads only its group's and ones that stay as they are. */
    var joining = IntArray(0)
        private set

    /** Starts the [JOIN_SMOOTHING_STEPS] steps over [groups]: the spare arrays take the values the vertices outside them keep. */
    fun startJoins() {
        joining = IntArray(groups.sumOf { it.size }).also { all ->
            var at = 0
            for (group in groups) {
                group.copyInto(all, at)
                at += group.size
            }
        }
        normals.copyInto(spare)
        owned.copyInto(spareOwned)
    }

    /** One step of averaging [joining]'s vertices [from] until [to], each moved by the square of how loosely it is held, into the spare arrays. */
    fun joinStep(from: Int, to: Int) {
        for (i in from until to) {
            val v = joining[i]
            val free = (1f - held[v]).coerceIn(0f, 1f)
            average(normals, spare, v, SMOOTHING_FACTOR * free * free)
            unit(spare, v, normals)
            average(owned, spareOwned, v, SMOOTHING_FACTOR * free * free)
        }
    }

    /** Moves vertex [v]'s value [factor] of the way toward the mean of its neighbours', into [into]. */
    private fun average(values: FloatArray, into: FloatArray, v: Int, factor: Float) {
        var x = 0f
        var y = 0f
        var z = 0f
        for (k in start[v] until start[v + 1]) {
            val n = links[k] * 3
            x += values[n]
            y += values[n + 1]
            z += values[n + 2]
        }
        val count = start[v + 1] - start[v]
        if (count == 0) {
            values.copyInto(into, v * 3, v * 3, v * 3 + 3)
            return
        }
        into[v * 3] = values[v * 3] + factor * (x / count - values[v * 3])
        into[v * 3 + 1] = values[v * 3 + 1] + factor * (y / count - values[v * 3 + 1])
        into[v * 3 + 2] = values[v * 3 + 2] + factor * (z / count - values[v * 3 + 2])
    }

    private fun unit(values: FloatArray, v: Int, fallback: FloatArray) {
        val x = values[v * 3]
        val y = values[v * 3 + 1]
        val z = values[v * 3 + 2]
        val length = sqrt(x * x + y * y + z * z)
        for (c in 0 until 3) values[v * 3 + c] = if (length > TINY) values[v * 3 + c] / length else fallback[v * 3 + c]
    }
}
