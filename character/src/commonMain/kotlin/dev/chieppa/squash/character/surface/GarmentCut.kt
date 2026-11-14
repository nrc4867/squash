package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.TriangleEdges
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.collections.IntList
import dev.chieppa.squash.geom.field.JoinedMember

/** How far either side of its hem, in cells, coverage is read before it is held. */
internal const val COVERAGE_CELLS = 4f

/** How far, in cells, smoothing coverage near its zero may move it, so a garment narrower than the smoothing still shows. */
internal const val COVERAGE_SHIFT_CELLS = 0.25f

/** Vertices or quads one piece of cutting work takes. */
private const val CUT_CHUNK = 2048

/**
 * The work that cuts [cover]'s garment from its grown [surface], opened where a part in [holes] comes through and round a part in [ends]
 * coming out through an end it reaches, in batches whose pieces are independent of one another, handed out by
 * [next] until it gives null, then read from [garment]: its coverage at every vertex, spread over the drawn mesh, the cut, the
 * triangles with a corner it covers with their edges, over which its contours run kept to its coverage, and its hems. Its coverage is smoothed near its zero first, see [smoothedNearZero].
 */
class GarmentCut(
    private val cover: WornCover,
    private val surface: CharacterSurface,
    private val holes: List<JoinedMember> = emptyList(),
    private val ends: List<JoinedMember> = emptyList(),
) {
    private val mesh = surface.mesh
    private val coverage = FloatArray(mesh.vertexCount)
    private val fan = FloatArray(surface.render.vertexCount)
    private var kept = IntArray(0)
    private var edges: TriangleEdges? = null
    private var cut: LevelCut? = null
    private var hems: List<List<CurvePoint>> = emptyList()
    private var stage = 0
    private var done: SurfaceGarment? = null

    val garment: SurfaceGarment get() = checkNotNull(done) { "The garment has work left" }

    fun next(): List<() -> Unit>? {
        stage++
        return when (stage) {
            1 -> {
                val reader = Coverage(surface.field, cover, mesh.cell * COVERAGE_CELLS, holes, ends = ends)
                chunked(mesh.vertexCount, CUT_CHUNK) { from, to -> reader.fill(mesh, coverage, from, to) }
            }
            2 -> {
                smoothedNearZero(coverage, mesh.triangles, mesh.vertexCount, mesh.cell * COVERAGE_CELLS, mesh.positions, mesh.cell * COVERAGE_SHIFT_CELLS).copyInto(coverage)
                kept = touchedTriangles(mesh.triangles, coverage)
                val triangleEdges = TriangleEdges(surface.positions, mesh.vertexCount, kept).also { edges = it }
                coverage.copyInto(fan)
                chunked(fan.size - mesh.vertexCount, CUT_CHUNK) { from, to -> fanned(surface, coverage, fan, from, to) } +
                    triangleEdges.next().orEmpty() + listOf({ hems = hemsOf(surface.lines, coverage) })
            }
            3 -> listOf({ cut = LevelCut.of(fan, surface.render.indices) }) + checkNotNull(edges).next().orEmpty()
            4 -> checkNotNull(edges).next().orEmpty().ifEmpty { listOf({}) }
            5 -> {
                val lines = TriangleLines(surface.positions, surface.lineNormals, kept, checkNotNull(edges), coverage)
                val made = checkNotNull(cut)
                done = SurfaceGarment(cover, surface, coverage, made, made.mesh(surface.render), hems, lines, fan, holes, ends = ends)
                null
            }
            else -> null
        }
    }

    companion object {
        /** [cover]'s garment worn on its grown [surface], its work run here in turn. */
        fun run(cover: WornCover, surface: CharacterSurface, holes: List<JoinedMember> = emptyList(), ends: List<JoinedMember> = emptyList()): SurfaceGarment {
            val cut = GarmentCut(cover, surface, holes, ends)
            while (true) (cut.next() ?: break).forEach { it() }
            return cut.garment
        }
    }
}

/** Quads [from] until [to]'s middles of a surface's drawn mesh in [out]: the mean of their corners' [values]. */
internal fun fanned(surface: CharacterSurface, values: FloatArray, out: FloatArray, from: Int, to: Int) {
    val corners = surface.mesh.vertexCount
    val indices = surface.render.indices
    for (q in from until to) {
        var sum = 0f
        for (k in 0 until 4) sum += values[indices[(q * 4 + k) * 3]]
        out[corners + q] = sum / 4f
    }
}

/** The triangles of [triangles] with a corner at coverage zero or more, as their corners. */
internal fun touchedTriangles(triangles: IntArray, coverage: FloatArray): IntArray {
    val out = IntList(triangles.size)
    for (tri in 0 until triangles.size / 3) {
        val a = triangles[tri * 3]
        val b = triangles[tri * 3 + 1]
        val c = triangles[tri * 3 + 2]
        if (coverage[a] >= 0f || coverage[b] >= 0f || coverage[c] >= 0f) {
            out.add(a)
            out.add(b)
            out.add(c)
        }
    }
    return out.toArray()
}

/** Where [coverage] is zero on [lines]' mesh, eased. */
internal fun hemsOf(lines: TriangleLines, coverage: FloatArray): List<List<CurvePoint>> = lines.zeroCurves(coverage) { true }.map(::eased)
