package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.smoothstep
import dev.chieppa.squash.geom.surface.HELD

/** What a key moves of the one surface: the render vertices, the head each turns with, and per direction their changes. */
internal class SurfaceShapes(val moved: IntArray, val heads: IntArray, val deltas: List<FloatArray>, val lines: List<FloatArray>) {
    companion object {
        fun of(surface: CharacterSurface, chains: List<KeyChains>, heads: List<PartId>, lookup: CharacterKeyLookup): SurfaceShapes? {
            val mesh = surface.mesh
            val runs = surface.field.runs
            val maps = chains.map { chain -> runs.map { chain.of(it.partId, it.patch.name) } }
            val runHead = runs.map { run -> lookup.headOf(run.partId)?.let(heads::indexOf) ?: -1 }
            val softRuns = chains.map { chain -> runs.map { run -> chain.soft.filter { it.part == run.partId } } }
            val turning = chains.map { chain -> runHead.map { h -> h >= 0 && (chain.faceDegrees[heads[h]] ?: 0f) != 0f } }
            val corners = mesh.vertexCount
            val render = surface.render
            val touched = BooleanArray(render.vertexCount)
            for (v in 0 until corners) {
                for (k in 0 until HELD) {
                    val member = mesh.held[v * HELD + k]
                    if (member < 0 || mesh.heldShares[v * HELD + k] <= 0f) continue
                    if (chains.indices.any { d -> maps[d][member] != KeyAffine.IDENTITY || softRuns[d][member].isNotEmpty() || turning[d][member] }) touched[v] = true
                }
            }
            val middles = render.vertexCount - corners
            for (quad in 0 until middles) if ((0 until 4).any { touched[render.indices[(quad * 4 + it) * 3]] }) touched[corners + quad] = true
            val moved = touched.indices.filter { touched[it] }.toIntArray()
            if (moved.isEmpty()) return null
            val slot = IntArray(render.vertexCount) { -1 }
            moved.forEachIndexed { i, v -> slot[v] = i }
            val head = IntArray(moved.size) { i ->
                val v = moved[i]
                val corner = if (v < corners) v else render.indices[((v - corners) * 4) * 3]
                val winner = mesh.winners[corner]
                if (winner >= 0) runHead[winner].coerceAtLeast(0) else 0
            }
            val deltas = chains.map { FloatArray(moved.size * DELTA_FLOATS) }
            val lines = chains.map { FloatArray(moved.size * 3) }
            for ((d, chain) in chains.withIndex()) {
                if (chain.still) continue
                val map = maps[d]
                val delta = deltas[d]
                for ((i, v) in moved.withIndex()) {
                    if (v >= corners) continue
                    val at = v * RenderMesh.FLOATS_PER_VERTEX
                    val point = render.vertices.vec(at)
                    val n = render.vertices.vec(at + RenderMesh.NORMAL_OFFSET_FLOATS)
                    val p = render.vertices.vec(at + RenderMesh.PROXY_OFFSET_FLOATS)
                    val l = surface.lineNormals.vec(v * 3)
                    var shift = Vec3.ZERO
                    var normal = Vec3.ZERO
                    var proxy = Vec3.ZERO
                    var line = Vec3.ZERO
                    var faceShare = 0f
                    val ownHead = heads[head[i]]
                    for (k in 0 until HELD) {
                        val member = mesh.held[v * HELD + k]
                        val share = if (member >= 0) mesh.heldShares[v * HELD + k] else 0f
                        if (share <= 0f) continue
                        val m = map[member]
                        shift += (m(point) - point) * share
                        normal += m.turn * n * share
                        proxy += m.turn * p * share
                        line += m.turn * l * share
                        for (soft in softRuns[d][member]) {
                            val reach = soft.area?.at(runs[member].patch.name, mesh.heldU[v * HELD + k], mesh.heldV[v * HELD + k]) ?: 1f
                            if (reach <= 0f) continue
                            val near = if (soft.nearPush == 0f) 0f else
                                smoothstep(NEAR_START, NEAR_WHOLE, ((point - soft.frame.center) dot soft.near) / soft.frame.length)
                            val swell = n * ((soft.push + soft.nearPush * near) * soft.frame.length)
                            shift += ((soft.map(point) - point) + swell) * (share * reach)
                        }
                        if (runHead[member] >= 0 && heads[runHead[member]] == ownHead) faceShare += share
                    }
                    val frame = chain.frames.getValue(ownHead)
                    val degrees = (chain.faceDegrees[ownHead] ?: 0f) * chain.follow(ownHead).mask(point, frame) * faceShare
                    val follow = chain.follow(ownHead)
                    val placed = follow.turned(point + shift, frame, degrees)
                    delta.put(i * DELTA_FLOATS, placed - point)
                    delta.put(i * DELTA_FLOATS + RenderMesh.NORMAL_OFFSET_FLOATS, turnedUp(normal.unitOr(n), frame, degrees) - n)
                    delta.put(i * DELTA_FLOATS + RenderMesh.PROXY_OFFSET_FLOATS, turnedUp(proxy.unitOr(p), frame, degrees) - p)
                    lines[d].put(i * 3, turnedUp(line.unitOr(l), frame, degrees) - l)
                }
                for ((i, v) in moved.withIndex()) {
                    if (v < corners) continue
                    val quad = v - corners
                    for (f in 0 until DELTA_FLOATS) {
                        var sum = 0f
                        for (c in 0 until 4) {
                            val s = slot[render.indices[(quad * 4 + c) * 3]]
                            if (s >= 0) sum += delta[s * DELTA_FLOATS + f]
                        }
                        delta[i * DELTA_FLOATS + f] = sum / 4f
                    }
                }
            }
            return SurfaceShapes(moved, head, deltas, lines)
        }
    }
}
