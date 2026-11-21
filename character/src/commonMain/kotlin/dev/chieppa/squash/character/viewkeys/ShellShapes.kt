package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor

/** How many of the head's nearest surface vertices a shell vertex takes its change from. */
private const val NEAREST = 4

/** A grid cell's side, as a share of the head's frame length. */
private const val CELL_SHARE = 0.04f

/** How far a shell vertex looks for the head's surface, in cells each way. */
private const val SEARCH = 2

private const val TINY = 1e-12f

/**
 * A short-hair shell's changes at each key direction, taken from the head's own on the one [surface]: each shell vertex moves as the
 * head's surface right under it does, its change the change [shapes] give the [NEAREST] head vertices round it, weighed by nearness, so
 * the shell rides the turned face exactly as the skin does. Null where the head is not on the surface; per direction null where the key
 * leaves the head.
 */
internal fun shellDeltas(shell: PlacedPart, head: PartId, surface: CharacterSurface, shapes: SurfaceShapes, frame: HeadFrame): List<FloatArray?>? {
    if (head !in surface.parts) return null
    val runs = surface.field.runs
    val winners = surface.mesh.winners
    val corners = surface.mesh.vertexCount
    val slot = IntArray(surface.render.vertexCount) { -1 }
    shapes.moved.forEachIndexed { i, v -> slot[v] = i }
    val cell = frame.length * CELL_SHARE
    val grid = HashMap<Long, MutableList<Int>>()
    for (v in 0 until corners) {
        val w = winners[v]
        if (w < 0 || runs[w].partId != head) continue
        grid.getOrPut(key(surface.render.vertices.vec(v * RenderMesh.FLOATS_PER_VERTEX), cell)) { ArrayList() } += v
    }
    if (grid.isEmpty()) return null
    val mesh = shell.surface.mesh
    val under = Array(mesh.vertexCount) { v ->
        val point = shell.origin + shell.surface.shown(mesh.vertices.vec(v * RenderMesh.FLOATS_PER_VERTEX))
        nearest(point, grid, surface, cell)
    }
    return shapes.deltas.map { surfaceDelta ->
        val delta = FloatArray(mesh.vertexCount * DELTA_FLOATS)
        var any = false
        for (v in 0 until mesh.vertexCount) {
            val near = under[v]
            for (part in 0 until DELTA_FLOATS / 3) {
                var sum = Vec3.ZERO
                for ((vertex, weight) in near) {
                    val s = slot[vertex]
                    if (s >= 0) sum += surfaceDelta.vec(s * DELTA_FLOATS + part * 3) * weight
                }
                if (sum != Vec3.ZERO) any = true
                delta.put(v * DELTA_FLOATS + part * 3, shell.surface.shown(sum))
            }
        }
        if (any) delta else null
    }
}

/** The head's surface vertices nearest [point], at most [NEAREST], each with its share of the change, the shares summing to 1. */
private fun nearest(point: Vec3, grid: Map<Long, List<Int>>, surface: CharacterSurface, cell: Float): List<Pair<Int, Float>> {
    val cx = floor(point.x / cell).toInt()
    val cy = floor(point.y / cell).toInt()
    val cz = floor(point.z / cell).toInt()
    var reach = SEARCH
    while (true) {
        val found = ArrayList<Pair<Int, Float>>()
        for (x in cx - reach..cx + reach) for (y in cy - reach..cy + reach) for (z in cz - reach..cz + reach) {
            for (v in grid[pack(x, y, z)] ?: continue) {
                found += v to (surface.render.vertices.vec(v * RenderMesh.FLOATS_PER_VERTEX) - point).length
            }
        }
        if (found.isNotEmpty() || reach > SEARCH * 8) {
            val near = found.sortedBy { it.second }.take(NEAREST)
            val weights = near.map { 1f / (it.second + cell * TINY_SHARE).coerceAtLeast(TINY) }
            val total = weights.sum()
            return near.mapIndexed { i, (v, _) -> v to weights[i] / total }
        }
        reach *= 2
    }
}

/** What a distance is padded by before its inverse is taken, as a share of a cell, so a vertex lying on the surface weighs finitely. */
private const val TINY_SHARE = 0.05f

private fun key(point: Vec3, cell: Float): Long = pack(floor(point.x / cell).toInt(), floor(point.y / cell).toInt(), floor(point.z / cell).toInt())

private fun pack(x: Int, y: Int, z: Int): Long = ((x.toLong() and 0x1FFFFF) shl 42) or ((y.toLong() and 0x1FFFFF) shl 21) or (z.toLong() and 0x1FFFFF)
