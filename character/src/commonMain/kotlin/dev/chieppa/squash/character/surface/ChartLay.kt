package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.surface.HELD

/** Vertices one piece of laying work reads. */
private const val LAY_CHUNK = 1024

/** Reads a value at [vertex] of a [SurfaceChart]'s mesh for the [member] it holds in its held [slot]. */
fun interface VertexRead {
    fun at(vertex: Int, slot: Int, member: Int): Float
}

/**
 * A value laid on [chart] for [members] by [read], as [SurfaceChart.layOn] lays one, in [pieces] independent of one another, so a run
 * holding many vertices is read on many threads; once they have run, [finish] gives the value with each quad middle filled.
 */
class ChartLay(private val chart: SurfaceChart, private val members: List<Int>, private val read: VertexRead) {
    private val reach = chart.reach
    private val values = FloatArray(chart.count) { -reach }

    /** The pieces of reading, each a share of the vertices [members] hold. */
    val pieces: List<() -> Unit> = if (members.size == 1) {
        val member = members[0]
        val vertices = chart.vertices[member]
        (vertices.indices step LAY_CHUNK).map { from -> { one(member, vertices, from, minOf(vertices.size, from + LAY_CHUNK)) } }
    } else {
        listOf({ several() })
    }

    private fun one(member: Int, vertices: IntArray, from: Int, to: Int) {
        val held = chart.mesh.held
        for (i in from until to) {
            val v = vertices[i]
            for (k in 0 until HELD) if (held[v * HELD + k] == member) values[v] = read.at(v, k, member).coerceIn(-reach, reach)
        }
    }

    private fun several() {
        val held = chart.mesh.held
        val set = BooleanArray(chart.corners)
        for (member in members) for (v in chart.vertices[member]) for (k in 0 until HELD) {
            if (held[v * HELD + k] != member || chart.behindSibling(v, k)) continue
            val value = read.at(v, k, member).coerceIn(-reach, reach)
            values[v] = if (set[v]) maxOf(values[v], value) else value
            set[v] = true
        }
    }

    /** The value laid, each middle of the quads among [triangles] the mean of its corners. */
    fun finish(triangles: IntArray): FloatArray {
        val indices = chart.indices
        for (tri in triangles) {
            val middle = indices[tri * 3 + 2]
            if (middle < chart.corners) continue
            val quad = tri / 4
            var sum = 0f
            for (k in 0 until 4) sum += values[indices[(quad * 4 + k) * 3]]
            values[middle] = sum / 4f
        }
        return values
    }
}
