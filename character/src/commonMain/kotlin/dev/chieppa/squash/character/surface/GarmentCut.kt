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

/** The most steps drawn cloth's coverage is smoothed in near its zero. */
private const val MOST_SMOOTHING_STEPS = 64

/** Vertices or quads one piece of cutting work takes. */
private const val CUT_CHUNK = 2048

/**
 * The work that cuts [cover]'s garment from its grown [surface], opened where a part in [holes] comes through and round a part in [ends]
 * coming out through an end it reaches, in batches whose pieces are independent of one another, handed out by
 * [next] until it gives null, then read from [garment]: its coverage at every vertex, spread over the drawn mesh, the cut, the
 * triangles with a corner it covers with their edges, over which its contours run kept to its coverage, and its hems. Its coverage is smoothed near its zero first, see [smoothedNearZero],
 * and the surface is lifted along its hems by the least stand-off and roll [hemLifts] gives, measured against the body's [bodyCell].
 */
class GarmentCut(
    private val cover: WornCover,
    private val grown: CharacterSurface,
    private val holes: List<JoinedMember> = emptyList(),
    private val ends: List<JoinedMember> = emptyList(),
    private val memo: CoverageMemo = CoverageMemo(),
    private val slot: Any = Unit,
    private val bodyCell: Float = grown.mesh.cell,
) {
    /** The surface it is cut from: the grown one, lifted along its hems once its coverage is read ([hemLifts]). */
    private var surface = grown
    private val mesh = surface.mesh
    private val coverage = FloatArray(mesh.vertexCount)
    private val fan = FloatArray(surface.render.vertexCount)
    private var kept = IntArray(0)
    private var edges: TriangleEdges? = null
    private var cut: LevelCut? = null
    private var hems: List<List<CurvePoint>> = emptyList()
    private var stage = 0
    private var smoothing: NearZeroSmoothing? = null
    private var read: CoverageMemo.Pending? = null
    private var done: SurfaceGarment? = null

    val garment: SurfaceGarment get() = checkNotNull(done) { "The garment has work left" }

    fun next(): List<() -> Unit>? {
        stage++
        return when (stage) {
            1 -> {
                val reach = mesh.cell * COVERAGE_CELLS
                val reader = Coverage(surface.field, cover, reach, holes, ends = ends)
                memo.read(slot, CoverageMemo.keyOf(cover, reach, 0f), CoverageMemo.partsOf(holes, ends), reader, surface.field, mesh, CUT_CHUNK).also { read = it }.tasks
            }
            2 -> {
                read?.values?.copyInto(coverage)
                read = null
                val running = smoothing ?: smoothingOf(coverage).also { smoothing = it }
                running.next()?.let {
                    stage--
                    return it
                }
                running.result.copyInto(coverage)
                hemLifts(grown, cover, coverage, bodyCell)?.let { surface = lifted(grown, it) }
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
                val reader = ClothSeams(surface.field, cover)
                val seams = if (isDrawnFine()) reader.smoothedValues(mesh, bodyCell) { smoothingOf(it).run() } else reader.values(mesh)
                done = SurfaceGarment(cover, surface, coverage, made, made.mesh(surface.render), hems, lines, fan, holes, ends = ends, seamValues = seams, grown = grown)
                null
            }
            else -> null
        }
    }

    /**
     * The smoothing of [values] near zero: over [COVERAGE_CELLS] of the mesh's cell, moving none by more than [COVERAGE_SHIFT_CELLS] of
     * it; where the cloth is drawn and meshes finer than [DETAIL_LEVEL]'s cell, over that cell instead, in as many more steps as reach
     * across it, so the body's coordinates, which vary unevenly within the body's cell, leave no steps along a drawn hem.
     */
    /** Whether its cloth is drawn and meshed finer than [DETAIL_LEVEL]'s cell. */
    private fun isDrawnFine(): Boolean = mesh.cell < bodyCell / (1 shl DETAIL_LEVEL) && cover.cloth.values.any { it.isDrawn }

    private fun smoothingOf(values: FloatArray): NearZeroSmoothing {
        val detail = bodyCell / (1 shl DETAIL_LEVEL)
        val drawn = isDrawnFine()
        val cell = if (drawn) detail else mesh.cell
        val reach = cell / mesh.cell
        val steps = if (drawn) minOf((NEAR_ZERO_STEPS * reach * reach).toInt(), MOST_SMOOTHING_STEPS) else NEAR_ZERO_STEPS
        return NearZeroSmoothing(values, mesh.triangles, mesh.vertexCount, cell * COVERAGE_CELLS, mesh.positions, cell * COVERAGE_SHIFT_CELLS, steps)
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

/**
 * Where [coverage] is zero on [lines]' mesh, eased, over the triangles with a corner [shown] keeps, at least zero there, so a hem a garment
 * over it hides is no hem; every triangle with no [shown].
 */
internal fun hemsOf(lines: TriangleLines, coverage: FloatArray, shown: FloatArray? = null): List<List<CurvePoint>> {
    val t = lines.triangles
    return lines.zeroCurves(coverage) { tri -> shown == null || (0 until 3).any { shown[t[tri * 3 + it]] >= 0f } }.map(::eased)
}
