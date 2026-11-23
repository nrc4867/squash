package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.collections.IntList
import dev.chieppa.squash.geom.surface.HELD

/** How far, in the surface's cells, a value laid on it reaches either side of zero before it is held there. */
private const val REACH_CELLS = 3f

/**
 * A [CharacterSurface] read by its parts' coordinates: for each run, the mesh's vertices where it holds a share and the drawn triangles
 * round them, and how long its u and v are on its surface. A value laid on the surface by [layOn] is read at each vertex a run holds and
 * held within [reach] of zero, every other vertex reading as far outside; a quad's middle takes the mean of its corners'.
 */
class SurfaceChart(val surface: CharacterSurface) {
    @PublishedApi internal val mesh = surface.mesh
    @PublishedApi internal val corners = mesh.vertexCount
    @PublishedApi internal val indices = surface.render.indices
    val count: Int = surface.render.vertexCount

    /** How far either side of zero a laid value is held. */
    val reach: Float = mesh.cell * REACH_CELLS

    private val runs = surface.field.runs
    @PublishedApi internal val vertices: Array<IntArray>
    private val triangles = arrayOfNulls<IntArray>(runs.size)
    private val scales = arrayOfNulls<RunScales>(runs.size)

    /** The drawn triangles with each mesh vertex as one of their first two corners, from [trianglesStart] until the next vertex's. */
    private val trianglesRound: IntArray
    private val trianglesStart: IntArray

    init {
        val counts = IntArray(runs.size)
        for (v in 0 until corners) for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member >= 0) counts[member]++
        }
        vertices = Array(runs.size) { IntArray(counts[it]) }
        counts.fill(0)
        for (v in 0 until corners) for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member >= 0) vertices[member][counts[member]++] = v
        }
        val triangleCount = indices.size / 3
        val start = IntArray(corners + 1)
        val round = if (count == corners) 3 else 2
        for (tri in 0 until triangleCount) for (c in 0 until round) {
            val v = indices[tri * 3 + c]
            if (v < corners) start[v + 1]++
        }
        for (v in 0 until corners) start[v + 1] += start[v]
        val around = IntArray(start[corners])
        val filled = start.copyOf(corners)
        for (tri in 0 until triangleCount) for (c in 0 until round) {
            val v = indices[tri * 3 + c]
            if (v < corners) around[filled[v]++] = tri
        }
        trianglesRound = around
        trianglesStart = start
    }

    /** The drawn triangles round the vertices run [member] holds, by the vertices in order; found when first asked for. */
    private fun trianglesOfRun(member: Int): IntArray {
        triangles[member]?.let { return it }
        val taken = BooleanArray(indices.size / 3)
        val out = IntList()
        for (v in vertices[member]) for (j in trianglesStart[v] until trianglesStart[v + 1]) {
            val tri = trianglesRound[j]
            if (!taken[tri]) {
                taken[tri] = true
                out.add(tri)
            }
        }
        return out.toArray().also { triangles[member] = it }
    }

    /** The runs of [part]'s patch [patch]: its members in the surface's field. */
    fun members(part: PartId, patch: String): List<Int> = runs.indices.filter { runs[it].partId == part && runs[it].patch.name == patch }

    /** How long u and v are on run [member]'s surface. */
    fun scales(member: Int): RunScales = scales[member] ?: RunScales(runs[member].patch).also { scales[member] = it }

    /** The drawn triangles round the vertices [members] hold, each once. */
    fun trianglesOf(members: List<Int>): IntArray {
        if (members.size == 1) return trianglesOfRun(members[0])
        val taken = BooleanArray(indices.size / 3)
        val out = IntList()
        for (member in members) for (tri in trianglesOfRun(member)) if (!taken[tri]) {
            taken[tri] = true
            out.add(tri)
        }
        return out.toArray()
    }

    /**
     * A value over the drawn mesh's vertices: at each vertex one of [members] holds, [read] given the vertex, the held slot of the member
     * and the member, the most of them, held within [reach]; everywhere else, and at a vertex [only] leaves out, as far outside as
     * [reach]; each middle of the quads round those vertices, among [triangles], the mean of its corners.
     */
    inline fun layOn(
        members: List<Int>,
        only: BooleanArray? = null,
        triangles: IntArray = trianglesOf(members),
        read: (vertex: Int, slot: Int, member: Int) -> Float,
    ): FloatArray {
        val out = FloatArray(count) { -reach }
        val set = BooleanArray(corners)
        for (member in members) for (v in vertices[member]) {
            if (only != null && !only[v]) continue
            for (k in 0 until HELD) {
                if (mesh.held[v * HELD + k] != member) continue
                val value = read(v, k, member).coerceIn(-reach, reach)
                out[v] = if (set[v]) maxOf(out[v], value) else value
                set[v] = true
            }
        }
        for (tri in triangles) {
            val middle = indices[tri * 3 + 2]
            if (middle < corners) continue
            val quad = tri / 4
            var sum = 0f
            for (k in 0 until 4) sum += out[indices[(quad * 4 + k) * 3]]
            out[middle] = sum / 4f
        }
        return out
    }

    /** Each mesh vertex's neighbours along the mesh's triangles' sides, from [linkStart] until the next vertex's. */
    internal val links: IntArray
    internal val linkStart: IntArray

    init {
        val t = mesh.triangles
        val start = IntArray(corners + 1)
        for (k in t.indices) start[t[k] + 1] += 2
        for (v in 0 until corners) start[v + 1] += start[v]
        val out = IntArray(start[corners])
        val filled = IntArray(corners)
        for (tri in 0 until t.size / 3) for (c in 0 until 3) {
            val v = t[tri * 3 + c]
            out[start[v] + filled[v]++] = t[tri * 3 + (c + 1) % 3]
            out[start[v] + filled[v]++] = t[tri * 3 + (c + 2) % 3]
        }
        links = out
        linkStart = start
    }

    /** Laid values eased off their runs' own surfaces; see [ChartEasing]. */
    val easing: ChartEasing by lazy { ChartEasing(this) }

    /** Each corner's steps along the mesh's sides from the nearest of [fresh]'s corners, counted up to [most] and [most] + 1 past it. */
    fun stepsFrom(fresh: (Int) -> Boolean, most: Int): IntArray {
        val steps = IntArray(corners) { most + 1 }
        var frontier = IntList()
        for (v in 0 until corners) if (fresh(v)) {
            steps[v] = 0
            frontier.add(v)
        }
        for (step in 1..most) {
            val next = IntList()
            for (i in 0 until frontier.size) {
                val v = frontier[i]
                for (j in linkStart[v] until linkStart[v + 1]) {
                    val u = links[j]
                    if (steps[u] > step) {
                        steps[u] = step
                        next.add(u)
                    }
                }
            }
            frontier = next
        }
        return steps
    }

    /** The vertices [members] hold that [keep] keeps, as a flag a mesh vertex. */
    fun held(members: List<Int>, keep: (Int) -> Boolean): BooleanArray {
        val out = BooleanArray(corners)
        for (member in members) for (v in vertices[member]) if (!out[v] && keep(v)) out[v] = true
        return out
    }

    /** Those of [triangles] whose quad, or itself where it stands alone, has a corner [near] flags, in order. */
    fun trianglesNear(triangles: IntArray, near: BooleanArray): IntArray {
        val out = IntList()
        for (tri in triangles) {
            var any = false
            if (indices[tri * 3 + 2] < corners) {
                for (k in 0 until 3) if (near[indices[tri * 3 + k]]) any = true
            } else {
                val quad = tri / 4
                for (k in 0 until 4) if (near[indices[(quad * 4 + k) * 3]]) any = true
            }
            if (any) out.add(tri)
        }
        return out.toArray()
    }

    /**
     * How far inside run [member]'s own section of the surface vertex [v] lies: half the least, over the other runs it holds, of how much
     * lower the member's own field is than theirs, so zero where it shares the surface equally with a neighbour. A part's first run reads
     * its part's other runs as its own: they grow out of it, as digits joined to a palm do, and what is painted on it carries on over
     * them wherever it still holds the surface.
     */
    fun section(v: Int, member: Int): Float {
        var own = 0f
        for (k in 0 until HELD) if (mesh.held[v * HELD + k] == member) own = mesh.heldOwn[v * HELD + k]
        val run = runs[member]
        var least = reach
        for (k in 0 until HELD) {
            val other = mesh.held[v * HELD + k]
            if (other < 0 || other == member) continue
            if (run.first && runs[other].partId == run.partId) continue
            least = minOf(least, (mesh.heldOwn[v * HELD + k] - own) * 0.5f)
        }
        return least
    }
}
