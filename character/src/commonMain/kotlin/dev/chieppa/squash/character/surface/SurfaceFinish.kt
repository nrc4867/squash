package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.TriangleEdges
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh
import kotlin.math.sqrt

/** Vertices one piece of finishing work takes. */
private const val FINISH_CHUNK = 2048

private const val TINY = 1e-12f

/**
 * The work that makes a [CharacterSurface] of a meshed [field], in batches whose pieces are independent of one another, handed out by
 * [next] until it gives null, then read from [surface]. It shades the mesh's [QuadFan], so no quad's diagonal shows where light meets
 * shade, and draws its lines on the mesh itself. At each of the mesh's vertices it reads the owning runs' own shading and how much of
 * the vertex that holds (see [ownShading]), and takes the surface's own normal from the quads round it by area. Both are averaged with
 * their neighbours' along the quads' sides [SMOOTHING_STEPS] times, so no cell's facet shows and a share that changes from one vertex
 * to the next never shows as a step; then [JOIN_SMOOTHING_STEPS] times more by as much as the runs leave the vertex unheld, so across a
 * join, where the swept solids' small waves meet, light meets shade along a smooth curve. The shading normal is the runs' own shading
 * plus the surface's normal by as much as they leave unheld: inside a part it shades as the part's own build does. Each quad's middle
 * takes the mean of its corners'.
 */
class SurfaceFinish(private val field: CharacterField, private val mesh: SurfaceMesh) {
    private val fan = QuadFan(mesh)
    private val count = fan.count
    private val corners = fan.corners
    private val quads = count - corners
    private val fieldNormals = fan.spread(mesh.normals, 3)
    private val smoothing = ShadingSmoothing(mesh, fan, fieldNormals)

    private var lines: TriangleLines? = null
    private var joins: List<List<CurvePoint>> = emptyList()
    private var stage = 0
    private var render: RenderMesh? = null
    private val edges = TriangleEdges(mesh.positions, mesh.vertexCount, mesh.triangles)
    private val positive = FloatArray(count)
    private val negative = FloatArray(count)
    private var done: CharacterSurface? = null

    val surface: CharacterSurface get() = checkNotNull(done) { "The surface has work left" }

    fun next(): List<() -> Unit>? {
        stage++
        smoothing.swap()
        return when {
            stage == 1 -> edges.next().orEmpty() + listOf({ smoothing.link() }) +
                chunks(quads) { from, to ->
                    fan.build(from, to)
                    fan.fill(fan.positions, 3, from, to)
                    fan.fill(fieldNormals, 3, from, to)
                } +
                chunks(corners) { from, to ->
                    ownShading(field, mesh, from, to, smoothing.owned, smoothing.held)
                    sides(from, to)
                }
            stage == 2 -> edges.next().orEmpty() + listOf({ smoothing.group() }) +
                chunks(corners) { from, to -> smoothing.face(from, to) } +
                chunks(quads) { from, to ->
                    fan.fill(positive, 1, from, to)
                    fan.fill(negative, 1, from, to)
                }
            stage <= 2 + SMOOTHING_STEPS -> {
                val edgeWork = if (stage == 3) edges.next().orEmpty() else emptyList()
                edgeWork + chunks(corners) { from, to -> smoothing.step(from, to) }.also { smoothing.swapNext() }
            }
            stage == 3 + SMOOTHING_STEPS -> {
                smoothing.startJoins()
                joinSteps()
            }
            stage < 3 + SMOOTHING_STEPS + JOIN_SMOOTHING_STEPS -> joinSteps()
            stage == 3 + SMOOTHING_STEPS + JOIN_SMOOTHING_STEPS -> {
                val out = FloatArray(count * RenderMesh.FLOATS_PER_VERTEX)
                render = RenderMesh(out, fan.triangles)
                val drawn = TriangleLines(mesh.positions, smoothing.normals, mesh.triangles, edges)
                lines = drawn
                listOf({ joins = joinCurves(field, mesh, drawn) }) + chunks(count) { from, to -> compose(from, to, out) }
            }
            stage == 4 + SMOOTHING_STEPS + JOIN_SMOOTHING_STEPS -> {
                done = CharacterSurface(field, mesh, checkNotNull(render), checkNotNull(lines), joins, positive, negative, mesh.positions, smoothing.normals)
                null
            }
            else -> null
        }
    }

    /** One step of the joins' smoothing, its values taken in by the next batch. */
    private fun joinSteps(): List<() -> Unit> =
        chunks(smoothing.joining.size) { from, to -> smoothing.joinStep(from, to) }.ifEmpty { listOf({}) }.also { smoothing.swapNext() }

    private fun chunks(total: Int, work: (Int, Int) -> Unit): List<() -> Unit> =
        (0 until total step FINISH_CHUNK).map { from -> { work(from, minOf(total, from + FINISH_CHUNK)) } }

    /** Each of vertices [from] until [to]'s share of paired parts on either side of the body's plane. */
    private fun sides(from: Int, to: Int) {
        for (v in from until to) for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member < 0) continue
            val part = field.runs[member].part
            if (part.side == null) continue
            if (part.origin.z >= 0f) positive[v] += mesh.heldShares[v * HELD + k] else negative[v] += mesh.heldShares[v * HELD + k]
        }
    }

    /**
     * The GPU's values for vertices [from] until [to]: position, the field's normal and the shading normal, a quad's middle taking the
     * mean of its corners' normal and own shading.
     */
    private fun compose(from: Int, to: Int, out: FloatArray) {
        val normal = FloatArray(3)
        val own = FloatArray(3)
        for (v in from until to) {
            val at = v * RenderMesh.FLOATS_PER_VERTEX
            for (c in 0 until 3) {
                out[at + c] = fan.positions[v * 3 + c]
                out[at + RenderMesh.NORMAL_OFFSET_FLOATS + c] = fieldNormals[v * 3 + c]
            }
            val loose: Float
            if (v < corners) {
                smoothing.normals.copyInto(normal, 0, v * 3, v * 3 + 3)
                smoothing.owned.copyInto(own, 0, v * 3, v * 3 + 3)
                loose = 1f - smoothing.held[v]
            } else {
                fan.mean(smoothing.normals, v - corners, normal)
                fan.mean(smoothing.owned, v - corners, own)
                loose = 1f - fan.mean(smoothing.held, v - corners)
                val length = sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2])
                if (length > TINY) for (c in 0 until 3) normal[c] /= length
            }
            val x = own[0] + normal[0] * loose
            val y = own[1] + normal[1] * loose
            val z = own[2] + normal[2] * loose
            val length = sqrt(x * x + y * y + z * z)
            val base = at + RenderMesh.PROXY_OFFSET_FLOATS
            out[base] = if (length > TINY) x / length else fieldNormals[v * 3]
            out[base + 1] = if (length > TINY) y / length else fieldNormals[v * 3 + 1]
            out[base + 2] = if (length > TINY) z / length else fieldNormals[v * 3 + 2]
        }
    }

    companion object {
        /** [field]'s surface of [mesh], all its work run here in turn. */
        fun run(field: CharacterField, mesh: SurfaceMesh): CharacterSurface {
            val finish = SurfaceFinish(field, mesh)
            while (true) (finish.next() ?: break).forEach { it() }
            return finish.surface
        }
    }
}
