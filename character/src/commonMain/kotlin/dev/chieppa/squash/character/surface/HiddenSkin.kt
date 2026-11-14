package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.LevelCut

/** How far inside a garment's coverage, in the surface's cells, skin or an inner layer lies before the garment hides it. */
private const val HIDING_CELLS = 1.5f

/** How far, in its garment's ease, skin round a part coming out through a hole stays shown, as it is seen through the hole. */
private const val HOLE_CLEAR_EASES = 2f

/** Vertices one piece of hiding work takes. */
private const val HIDING_CHUNK = 2048

/**
 * The work that finds what of [body] and of each of [garments], innermost first, the garments over it hide, in batches handed out by
 * [next] until it gives null, then read from [skin] and [worn]: the body is cut along the curve where it lies [HIDING_CELLS] cells inside
 * the coverage of a garment, and each garment where it lies as far inside an outer one's, so skin and inner layers seen through an
 * opening still show and end on a smooth curve; skin round a part coming out through a garment's hole stays shown.
 */
class HiddenSkin(private val body: CharacterSurface, private val garments: List<SurfaceGarment>) {
    /** Each surface the garments may hide, the body first, and for each the garments over it. */
    private val surfaces: List<CharacterSurface> = listOf(body) + garments.map { it.surface }
    private val over: List<List<Int>> = surfaces.indices.map { s -> (s until garments.size).toList() }
    private val fans = surfaces.indices.map { s -> over[s].map { FloatArray(surfaces[s].render.vertexCount) } }
    private val shown = surfaces.map { FloatArray(it.render.vertexCount) }
    private var stage = 0
    private var cuts: List<LevelCut?> = emptyList()

    /** What of the body shows: null where nothing is worn. */
    val skin: LevelCut? get() = cuts.firstOrNull()

    /** The garments with what of each shows under the ones over it. */
    val worn: List<SurfaceGarment> get() = garments.mapIndexed { i, garment -> cuts.getOrNull(i + 1)?.let { garment.within(it, shown[i + 1]) } ?: garment }

    fun next(): List<() -> Unit>? {
        if (garments.isEmpty()) return null
        stage++
        return when (stage) {
            1 -> surfaces.indices.flatMap { s ->
                val mesh = surfaces[s].mesh
                over[s].flatMapIndexed { k, g ->
                    val cover = garments[g].cover
                    val clear = if (s == 0) cover.branches.values.maxOfOrNull { it.ease }?.times(HOLE_CLEAR_EASES) ?: 0f else 0f
                    val reader = Coverage(surfaces[s].field, cover, mesh.cell * COVERAGE_CELLS, garments[g].holes, clear)
                    chunked(mesh.vertexCount, HIDING_CHUNK) { from, to -> reader.fill(mesh, fans[s][k], from, to) }
                }
            }
            2 -> surfaces.indices.flatMap { s ->
                over[s].indices.flatMap { k -> chunked(surfaces[s].render.vertexCount - surfaces[s].mesh.vertexCount, HIDING_CHUNK) { from, to -> fanned(surfaces[s], fans[s][k], fans[s][k], from, to) } }
            }
            3 -> surfaces.indices.map { s -> { least(s) } }
            4 -> {
                val made = arrayOfNulls<LevelCut>(surfaces.size)
                cuts = made.asList()
                surfaces.indices.filter { over[it].isNotEmpty() }.map { s -> { made[s] = LevelCut.of(shown[s], surfaces[s].render.indices) } }
            }
            else -> null
        }
    }

    /** At each vertex of surface [s], how far it lies short of being hidden by any garment over it, starting from its own coverage for a garment. */
    private fun least(s: Int) {
        val out = shown[s]
        if (s > 0) garments[s - 1].drawnCoverage.copyInto(out) else out.fill(Float.MAX_VALUE)
        val margin = surfaces[s].mesh.cell * HIDING_CELLS
        for (fan in fans[s]) for (i in out.indices) out[i] = minOf(out[i], margin - fan[i])
    }

    /** Every batch run here in turn. */
    fun run(): HiddenSkin {
        while (true) (next() ?: break).forEach { it() }
        return this
    }
}
