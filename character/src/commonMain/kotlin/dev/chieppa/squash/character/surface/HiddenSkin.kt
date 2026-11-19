package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.field.JoinedMember

/** How far inside a garment's coverage, in the surface's cells, skin or an inner layer lies before the garment hides it. */
internal const val HIDING_CELLS = 1.5f

/** How far past the hiding cells, in its garment's ease, skin under a garment stays shown, as far as it is seen past the garment's edge. */
internal const val SEEN_UNDER_EASES = 2f

/** How far, in its garment's ease, skin round a part coming out through a hole stays shown, as it is seen through the hole. */
private const val HOLE_CLEAR_EASES = 2f

/** Vertices one piece of hiding work takes. */
private const val HIDING_CHUNK = 2048

/** What names a hiding read's slot in its memo. */
private const val HIDING_SLOT = "hiding"

/** What names a hiding cut's slot in its memo. */
private const val CUT_SLOT = "cut"

/** The memo slot of surface [s]'s read under its hider [k]. */
private fun readSlot(s: Int, k: Int): Any = listOf(HIDING_SLOT, s, k)

/** The memo slot of surface [s]'s cut. */
private fun cutSlot(s: Int): Any = listOf(HIDING_SLOT, CUT_SLOT, s)

/**
 * The work that finds what of [body] and of each of [garments], innermost first, the garments over it hide, in batches handed out by
 * [next] until it gives null, then read from [skin] and [worn]. The body is cut along the curve where it lies [HIDING_CELLS] cells and
 * [SEEN_UNDER_EASES] of the garment's ease inside the coverage of a garment over it, or of those garments together, as deep as it is seen
 * past the garment's edge; each garment is cut where it lies as far inside an outer one's. So skin and inner layers seen through an
 * opening still show and end on a smooth curve, each coverage eased near its zero as a garment's cut eases it; skin round a part coming
 * out through a garment's hole stays shown.
 */
class HiddenSkin(private val body: CharacterSurface, private val garments: List<SurfaceGarment>, private val memo: CoverageMemo = CoverageMemo()) {
    init {
        memo.begin()
    }

    /** Each surface the garments may hide, the body first, and for each the garments over it. */
    private val surfaces: List<CharacterSurface> = listOf(body) + garments.map { it.surface }
    private val over: List<List<Int>> = surfaces.indices.map { s -> (s until garments.size).toList() }

    /** For each surface, what hides it: each garment over it, and those garments together as one cover where there are several. */
    private val hiders: List<List<Hider>> = surfaces.indices.map { s ->
        over[s].map { g ->
            val cover = garments[g].cover
            val clear = if (s == 0) cover.branches.values.maxOfOrNull { it.ease }?.times(HOLE_CLEAR_EASES) ?: 0f else 0f
            Hider(cover, garments[g].holes, clear, garments[g].ends)
        } + listOfNotNull(coverUnion(over[s].map { garments[it].cover })?.let { Hider(it, emptyList(), 0f, over[s].flatMap { g -> garments[g].ends }.distinctBy { it.key }) })
    }
    private val fans = surfaces.indices.map { s -> hiders[s].map { FloatArray(surfaces[s].render.vertexCount) } }
    private val shown = surfaces.map { FloatArray(it.render.vertexCount) }

    /** For each surface, how far each of its vertices lies inside the coverage of the garments over it, the most of any. */
    val under: List<FloatArray> = surfaces.map { FloatArray(it.mesh.vertexCount) }
    private var stage = 0
    private var cuts: List<LevelCut?> = emptyList()

    /** Each surface's coverage under each of its hiders as read, before it is smoothed. */
    private var raw: List<List<CoverageMemo.Pending>> = emptyList()

    /** Each coverage being smoothed near its zero, with its smoothing and the memo slot its read was begun in. */
    private var smoothing: List<Smoothing>? = null

    /** Whether each surface's cut is made afresh, so kept in the memo once made, rather than taken from it. */
    private var cutFresh = BooleanArray(0)

    /** What of the body shows: null where nothing is worn. */
    val skin: LevelCut? get() = cuts.firstOrNull()

    /** How far each vertex of the body's drawn mesh lies short of being hidden: what of the body shows is where it is at least zero. */
    val shownSkin: FloatArray? get() = if (cuts.isEmpty()) null else shown[0]

    /** The garments with what of each shows under the ones over it. */
    val worn: List<SurfaceGarment> get() = garments.mapIndexed { i, garment -> cuts.getOrNull(i + 1)?.let { garment.within(it, shown[i + 1]) } ?: garment }

    fun next(): List<() -> Unit>? {
        if (garments.isEmpty()) return null
        smoothing?.let { running ->
            val tasks = running.mapNotNull { it.smoothed.next() }
            if (tasks.isNotEmpty()) return tasks.flatten()
            for (it in running) {
                it.smoothed.result.copyInto(it.fan)
                memo.keepSmoothed(it.slot, it.smoothed.result.copyOf(it.count))
            }
            memo.settle()
            smoothing = null
            return surfaces.indices.flatMap { s ->
                hiders[s].indices.flatMap { k -> chunked(surfaces[s].render.vertexCount - surfaces[s].mesh.vertexCount, HIDING_CHUNK) { from, to -> fanned(surfaces[s], fans[s][k], fans[s][k], from, to) } }
            }
        }
        stage++
        return when (stage) {
            1 -> {
                val reads = surfaces.indices.map { s ->
                    val mesh = surfaces[s].mesh
                    hiders[s].mapIndexed { k, hider ->
                        val reach = mesh.cell * COVERAGE_CELLS
                        val reader = Coverage(surfaces[s].field, hider.cover, reach, hider.holes, hider.clear, hider.ends)
                        val key = CoverageMemo.keyOf(hider.cover, reach, hider.clear)
                        memo.read(readSlot(s, k), key, CoverageMemo.partsOf(hider.holes, hider.ends), reader, surfaces[s].field, mesh, HIDING_CHUNK)
                    }
                }
                raw = reads
                reads.flatten().flatMap { it.tasks }
            }
            2 -> {
                memo.settle()
                raw.forEachIndexed { s, reads -> reads.forEachIndexed { k, read -> read.values.copyInto(fans[s][k]) } }
                smoothing = surfaces.indices.flatMap { s ->
                    val mesh = surfaces[s].mesh
                    hiders[s].indices.mapNotNull { k ->
                        val slot = readSlot(s, k)
                        val kept = memo.smoothedOf(slot, raw[s][k], mesh.vertexCount)
                        if (kept != null) {
                            kept.copyInto(fans[s][k])
                            return@mapNotNull null
                        }
                        val smoothed = NearZeroSmoothing(fans[s][k], mesh.triangles, mesh.vertexCount, mesh.cell * COVERAGE_CELLS, mesh.positions, mesh.cell * COVERAGE_SHIFT_CELLS)
                        Smoothing(fans[s][k], smoothed, slot, mesh.vertexCount)
                    }
                }
                next()
            }
            3 -> surfaces.indices.map { s -> { least(s) } }
            4 -> {
                val made = arrayOfNulls<LevelCut>(surfaces.size)
                cuts = made.asList()
                cutFresh = BooleanArray(surfaces.size)
                surfaces.indices.filter { over[it].isNotEmpty() }.mapNotNull { s ->
                    val kept = memo.cutOf(cutSlot(s), surfaces[s].render.indices, shown[s])
                    if (kept != null) {
                        made[s] = kept
                        return@mapNotNull null
                    }
                    cutFresh[s] = true
                    val cut: () -> Unit = { made[s] = LevelCut.of(shown[s], surfaces[s].render.indices) }
                    cut
                }
            }
            5 -> {
                for (s in surfaces.indices) if (cutFresh[s]) memo.keepCut(cutSlot(s), surfaces[s].render.indices, shown[s].copyOf(), checkNotNull(cuts[s]))
                memo.settle()
                null
            }
            else -> null
        }
    }

    /** At each vertex of surface [s], how far it lies short of being hidden by any garment over it, starting from its own coverage for a garment. */
    private fun least(s: Int) {
        val out = shown[s]
        if (s > 0) garments[s - 1].drawnCoverage.copyInto(out) else out.fill(Float.MAX_VALUE)
        val most = under[s]
        most.fill(-Float.MAX_VALUE)
        for ((k, fan) in fans[s].withIndex()) {
            for (i in most.indices) most[i] = maxOf(most[i], fan[i])
            val standing = hiders[s][k].cover.branches.values.maxOfOrNull { it.ease } ?: 0f
            val margin = surfaces[s].mesh.cell * HIDING_CELLS + standing * SEEN_UNDER_EASES
            for (i in out.indices) out[i] = minOf(out[i], margin - fan[i])
        }
    }

    /** Coverage [fan] being [smoothed], read in memo slot [slot] at a mesh of [count] vertices. */
    private class Smoothing(val fan: FloatArray, val smoothed: NearZeroSmoothing, val slot: Any, val count: Int)

    /**
     * A cover that hides what lies inside it, with the parts that come out through its [holes] and the skin [clear] of them that stays
     * shown, and those that come out through the ends it reaches, [ends].
     */
    private class Hider(val cover: WornCover, val holes: List<JoinedMember>, val clear: Float, val ends: List<JoinedMember>)

    /** Every batch run here in turn. */
    fun run(): HiddenSkin {
        while (true) (next() ?: break).forEach { it() }
        return this
    }
}
