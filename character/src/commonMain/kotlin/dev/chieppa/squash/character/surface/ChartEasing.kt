package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.collections.IntList
import dev.chieppa.squash.geom.surface.HELD

/** How far off a run's own surface, in cells, a vertex starts to have its laid value eased toward its neighbours', and is eased fully. */
private const val EASED_FROM_CELLS = 0.1f
private const val EASED_FULLY_CELLS = 0.6f

/** How many times a laid value is eased toward its neighbours' off its run's own surface, and how far each time. */
internal const val PAINT_EASING_STEPS = 10
private const val EASING_FACTOR = 0.5f

/**
 * What a painting takes from the painting of the mesh before: where each corner was there ([previousOf], -1 for a corner meshed again),
 * and each corner's [steps] along the mesh's sides from the nearest corner meshed again, counted to twice [PAINT_EASING_STEPS].
 */
class PaintCarry(val previousOf: IntArray, val steps: IntArray) {
    companion object {
        /** The carry onto [chart]'s mesh from the one whose corners [previousOf] names. */
        fun of(chart: SurfaceChart, previousOf: IntArray): PaintCarry =
            PaintCarry(previousOf, chart.stepsFrom({ previousOf[it] < 0 }, 2 * PAINT_EASING_STEPS))
    }
}

/**
 * Values laid on [chart] eased toward their neighbours' where a vertex lies off its run's own surface, as on the blend into a neighbouring
 * part, where the run's (u, v) is read past where its rings lie and wavers: a vertex on the run's surface keeps its value, and one a cell
 * or so off it takes its neighbours' among those its members hold.
 */
class ChartEasing(private val chart: SurfaceChart) {
    private val mesh = chart.mesh
    private val corners = chart.corners
    private val links = chart.links
    private val linkStart = chart.linkStart

    /** Which vertices [members] hold, those of them off their run's own surface, and how far off. */
    private class Easing(val set: BooleanArray, val moving: IntArray, val weight: FloatArray)

    private val easings = arrayOfNulls<Easing>(chart.surface.field.runs.size)

    /**
     * [values], laid on by [SurfaceChart.layOn] for [members], eased; each middle of the quads among [triangles] takes the mean of its
     * corners again. With [carry] and the values [before] laid on the mesh before, eased and kept to the same sections, a corner more than
     * [PAINT_EASING_STEPS] steps from any corner meshed again takes its value there, since everything easing reads to reach it is the same,
     * and easing runs only where it can reach the rest.
     */
    fun eased(values: FloatArray, members: List<Int>, triangles: IntArray, carry: PaintCarry? = null, before: FloatArray? = null): FloatArray {
        val easing = easingOf(members)
        val near = if (carry != null && before != null) carry.steps else null
        val moving = if (near == null) easing.moving else easing.moving.filter { near[it] <= 2 * PAINT_EASING_STEPS }.toIntArray()
        val current = values.copyOf()
        if (moving.isEmpty() && near == null) return values
        val weight = easing.weight
        val set = easing.set
        val active = reached(values, moving, set, weight, near)
        val next = FloatArray(active.size)
        repeat(PAINT_EASING_STEPS) {
            for (i in active.indices) {
                val v = active[i]
                var sum = 0f
                var n = 0
                for (j in linkStart[v] until linkStart[v + 1]) {
                    val u = links[j]
                    if (!set[u]) continue
                    sum += current[u]
                    n++
                }
                next[i] = if (n > 0) current[v] + weight[v] * EASING_FACTOR * (sum / n - current[v]) else current[v]
            }
            for (i in active.indices) current[active[i]] = next[i]
        }
        if (near != null) for (v in easing.moving) if (near[v] > PAINT_EASING_STEPS) current[v] = before!![carry!!.previousOf[v]]
        val indices = chart.indices
        for (tri in triangles) {
            val middle = indices[tri * 3 + 2]
            if (middle < corners) continue
            val quad = tri / 4
            var sum = 0f
            for (k in 0 until 4) sum += current[indices[(quad * 4 + k) * 3]]
            current[middle] = sum / 4f
        }
        return current
    }

    /**
     * Those of [moving] whose value easing can change: where a neighbour [set] flags holds another value, and those [moving] reaches from
     * there in fewer steps than easing takes, since a change spreads one neighbour a step; with [near], only those within twice
     * [PAINT_EASING_STEPS] of a corner meshed again.
     */
    private fun reached(values: FloatArray, moving: IntArray, set: BooleanArray, weight: FloatArray, near: IntArray?): IntArray {
        val seen = BooleanArray(corners)
        var frontier = IntList()
        for (v in moving) {
            for (j in linkStart[v] until linkStart[v + 1]) {
                val u = links[j]
                if (set[u] && values[u] != values[v]) {
                    seen[v] = true
                    frontier.add(v)
                    break
                }
            }
        }
        val out = IntList()
        repeat(PAINT_EASING_STEPS) { step ->
            val next = IntList()
            for (i in 0 until frontier.size) {
                val v = frontier[i]
                out.add(v)
                if (step == PAINT_EASING_STEPS - 1) continue
                for (j in linkStart[v] until linkStart[v + 1]) {
                    val u = links[j]
                    if (weight[u] > 0f && !seen[u] && (near == null || near[u] <= 2 * PAINT_EASING_STEPS)) {
                        seen[u] = true
                        next.add(u)
                    }
                }
            }
            frontier = next
        }
        return out.toArray()
    }

    private fun easingOf(members: List<Int>): Easing {
        if (members.size == 1) easings[members[0]]?.let { return it }
        val cell = mesh.cell
        val weight = FloatArray(corners)
        val set = BooleanArray(corners)
        val loose = IntList()
        for (member in members) for (v in chart.vertices[member]) for (k in 0 until HELD) {
            if (mesh.held[v * HELD + k] != member) continue
            set[v] = true
            val off = (kotlin.math.abs(mesh.heldOwn[v * HELD + k]) / cell - EASED_FROM_CELLS) / (EASED_FULLY_CELLS - EASED_FROM_CELLS)
            val w = off.coerceIn(0f, 1f)
            if (w > 0f && weight[v] == 0f) loose.add(v)
            if (w > weight[v]) weight[v] = w
        }
        return Easing(set, loose.toArray(), weight).also { if (members.size == 1) easings[members[0]] = it }
    }
}
