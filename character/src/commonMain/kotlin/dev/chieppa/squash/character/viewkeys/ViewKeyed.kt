package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.PosedParts
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.posedBy
import dev.chieppa.squash.character.build.posedLines
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor

/** Weights this small or smaller count as none. */
private const val NO_WEIGHT = 1e-4f

/** How far from a moved vertex of the surface, in its cells, an ink point still rides with it. */
private const val CARRY_CELLS = 2f

/** What a view shows of a character with its head cheats: its [body], its [lines] found on that body, and its one [surface]. */
class KeyedView(val body: AssembledCharacter, val lines: BodyLines, val surface: PosedSurface?)

/**
 * How much each key direction holds for each keyed head of [body], as it stands, for a viewer whose way from a point is [toViewer]: read
 * in the head's own directions as [body] turns it, so a head turned over the shoulder picks its keys by where it points.
 */
fun ViewKeyShapes.weightsFor(body: AssembledCharacter, toViewer: (Vec3) -> Vec3): Map<PartId, FloatArray> =
    heads.mapNotNull { (id, frame) ->
        val head = body.part(id) ?: return@mapNotNull null
        id to headWeights(frame, head.turn, toViewer(head.box?.center ?: head.origin))
    }.toMap()

/** The key weights of a head whose frame at rest is [frame], turned by [turn], for a viewer the way [toViewer] from it. */
fun headWeights(frame: HeadFrame, turn: Mat3, toViewer: Vec3): FloatArray =
    keyWeights(toViewer dot (turn * frame.forward), toViewer dot (turn * frame.up), toViewer dot (turn * frame.left))

/**
 * [body], with its [lines], [work] and one [surface] as they stand, at rest or posed from the character the shapes were built on,
 * with each head's keys blended by [weights]: every moved vertex takes the weighted sum of its changes, turned as its head turns, so the
 * cheat rides the pose. The surface's paint is laid again on it and its ink marks and eyes carried with the vertex nearest each; a
 * separate part keyed is shown moved and its lines found again. Itself where nothing moves.
 */
fun ViewKeyShapes.keyed(body: AssembledCharacter, lines: BodyLines, work: BodySurfaceWork, surface: PosedSurface?, weights: Map<PartId, FloatArray>): KeyedView {
    val active = weights.filterValues { w -> w.any { it > NO_WEIGHT } }
    if (active.isEmpty() || isEmpty) return KeyedView(body, lines, surface)
    val turns = headOrder.map { body.part(it)?.turn ?: Mat3.IDENTITY }
    val keyedSurface = surface?.let { keyedSurface(it, headOrder.map { id -> active[id] }, turns) } ?: surface
    val parts = keyedParts(body, active)
    if (parts == null) return KeyedView(body, lines, keyedSurface)
    val shown = body.posedBy(parts)
    return KeyedView(shown, posedLines(lines, shown, parts.meshes.keys, work, { false }), keyedSurface)
}

/** [base] with each moved vertex's changes blended by its head's weights, null for a head at rest, and turned by its head's turn. */
private fun ViewKeyShapes.keyedSurface(base: PosedSurface, weights: List<FloatArray?>, turns: List<Mat3>): PosedSurface {
    val rest = surface ?: return base
    val shown = base.surface
    if (shown.mesh !== rest.mesh || shown.render.vertexCount != rest.render.vertexCount || moved.isEmpty()) return base
    val width = RenderMesh.FLOATS_PER_VERTEX
    val vertices = shown.render.vertices.copyOf()
    val positions = shown.positions.copyOf()
    val lineNormals = shown.lineNormals.copyOf()
    val corners = shown.mesh.vertexCount
    val shifts = FloatArray(moved.size * 3)
    for ((i, v) in moved.withIndex()) {
        val head = movedHead[i]
        val w = weights.getOrNull(head) ?: continue
        val turn = turns[head]
        var shift = Vec3.ZERO
        var normal = Vec3.ZERO
        var proxy = Vec3.ZERO
        var line = Vec3.ZERO
        for (d in w.indices) {
            val weight = w[d]
            if (weight <= NO_WEIGHT) continue
            val delta = surfaceDeltas[d]
            shift += delta.vec(i * DELTA_FLOATS) * weight
            normal += delta.vec(i * DELTA_FLOATS + RenderMesh.NORMAL_OFFSET_FLOATS) * weight
            proxy += delta.vec(i * DELTA_FLOATS + RenderMesh.PROXY_OFFSET_FLOATS) * weight
            line += lineDeltas[d].vec(i * 3) * weight
        }
        val at = v * width
        val moving = turn * shift
        shifts.put(i * 3, moving)
        val point = vertices.vec(at) + moving
        vertices.put(at, point)
        vertices.put(at + RenderMesh.NORMAL_OFFSET_FLOATS, (vertices.vec(at + RenderMesh.NORMAL_OFFSET_FLOATS) + turn * normal).unitOr(Vec3.Y))
        vertices.put(at + RenderMesh.PROXY_OFFSET_FLOATS, (vertices.vec(at + RenderMesh.PROXY_OFFSET_FLOATS) + turn * proxy).unitOr(Vec3.Y))
        if (v < corners) {
            positions.put(v * 3, point)
            lineNormals.put(v * 3, (lineNormals.vec(v * 3) + turn * line).unitOr(Vec3.Y))
        }
    }
    val render = RenderMesh(vertices, shown.render.indices)
    val moves = shown.movedTo(render, positions, lineNormals, shown.lines.triangles)
    val carry = Carry(shown.positions, moved, corners, shifts, shown.mesh.cell * CARRY_CELLS)::carried
    return PosedSurface(moves, base.paint?.posed(render, carry), base.dress, base.carry, base.creases)
}

/** The separate parts' meshes in [body] with their changes blended by their heads' [weights], or null where none moves. */
private fun ViewKeyShapes.keyedParts(body: AssembledCharacter, weights: Map<PartId, FloatArray>): PosedParts? {
    val meshes = HashMap<PartId, RenderMesh>()
    val rests = HashMap<PartId, RenderMesh>()
    for ((id, held) in parts) {
        val w = weights[held.head] ?: continue
        val part = body.part(id) ?: continue
        val headTurn = body.part(held.head)?.turn ?: Mat3.IDENTITY
        val mesh = part.surface.mesh
        if (mesh.vertexCount * DELTA_FLOATS != held.deltas.firstNotNullOfOrNull { it }?.size) continue
        val vertices = mesh.vertices.copyOf()
        var any = false
        for (v in 0 until mesh.vertexCount) {
            val at = v * RenderMesh.FLOATS_PER_VERTEX
            for (offset in listOf(0, RenderMesh.NORMAL_OFFSET_FLOATS, RenderMesh.PROXY_OFFSET_FLOATS)) {
                var sum = Vec3.ZERO
                for (d in w.indices) {
                    val delta = held.deltas[d] ?: continue
                    if (w[d] <= NO_WEIGHT) continue
                    sum += delta.vec(v * DELTA_FLOATS + offset) * w[d]
                }
                if (sum == Vec3.ZERO) continue
                any = true
                val turned = part.surface.shown(headTurn * part.surface.shown(sum))
                val next = vertices.vec(at + offset) + turned
                vertices.put(at + offset, if (offset == 0) next else next.unitOr(Vec3.Y))
            }
        }
        if (!any) continue
        meshes[id] = RenderMesh(vertices, mesh.indices)
        rests[id] = mesh
    }
    if (meshes.isEmpty()) return null
    return object : PosedParts {
        override val meshes: Map<PartId, RenderMesh> = meshes
        override val turns: Map<PartId, Mat3> = meshes.keys.associateWith { body.part(it)?.turn ?: Mat3.IDENTITY }
        override val rests: Map<PartId, RenderMesh> = rests
    }
}

/** Carries an ink point by the change of the moved vertex nearest it, within [reach]; a point near none stays. */
private class Carry(private val positions: FloatArray, private val moved: IntArray, private val corners: Int, private val shifts: FloatArray, private val reach: Float) {
    private val cells = HashMap<Long, MutableList<Int>>()

    init {
        for ((i, v) in moved.withIndex()) if (v < corners) cells.getOrPut(key(positions.vec(v * 3))) { ArrayList() } += i
    }

    private fun cell(x: Float) = floor(x / reach).toLong()

    private fun key(p: Vec3) = key(cell(p.x), cell(p.y), cell(p.z))

    private fun key(x: Long, y: Long, z: Long) = (x and 0x1FFFFF) or ((y and 0x1FFFFF) shl 21) or ((z and 0x1FFFFF) shl 42)

    fun carried(point: ResolvedPoint): ResolvedPoint {
        val p = point.position
        var best = -1
        var nearest = reach
        val cx = cell(p.x)
        val cy = cell(p.y)
        val cz = cell(p.z)
        for (x in cx - 1..cx + 1) for (y in cy - 1..cy + 1) for (z in cz - 1..cz + 1) {
            for (i in cells[key(x, y, z)] ?: continue) {
                val d = (positions.vec(moved[i] * 3) - p).length
                if (d < nearest) {
                    nearest = d
                    best = i
                }
            }
        }
        if (best < 0) return point
        return point.copy(position = p + shifts.vec(best * 3))
    }
}
