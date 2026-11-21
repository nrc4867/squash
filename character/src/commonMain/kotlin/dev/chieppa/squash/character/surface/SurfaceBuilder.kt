package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.mouth.MouthRecess
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.geom.surface.AdaptiveMesher
import kotlin.math.abs

/** How far a character's own cell may drift from the one its surface is meshed at before the surface is meshed again whole at the new one. */
private const val CELL_DRIFT = 0.25f

/** How many times the body's cell the surface takes while a part is being dragged. */
const val COARSE_FACTOR = 2f

/**
 * Builds characters' surfaces, keeping between builds each run's solid and the blocks of the last surface at the full cell and at the
 * coarse one, so a build after an edit meshes again only where the edited member reaches. Digits mesh at finer cells (see
 * [digitLevels]) in both, the coarse surface's every level at twice the full one's. The cell, [cellShare] of the root's least
 * half-extent (see [fieldOf]), stays the one it started at while the character's own drifts less than a quarter from it, so an edit does
 * not move every cell. The meshers read the field through [reads]. One build at a time.
 */
class SurfaceBuilder(private val cellShare: Float = CELL_SHARE, private val reads: FieldReads = CpuFieldReads) {
    private val solids = RunSolids()
    private var cell = 0f
    private var fine: AdaptiveMesher? = null
    private var coarse: AdaptiveMesher? = null

    /**
     * One build: hand out its batches with [next] until it has none, then read [surface]; or hand out only the mesher's with
     * [nextMeshing], then read [mesh] and finish it apart.
     */
    inner class Build internal constructor(val field: CharacterField, mesher: AdaptiveMesher, levels: IntArray) {
        private val run: AdaptiveMesher.Run = mesher.begin(field.field, levels)
        private var meshed = false
        private var finish: SurfaceFinish? = null

        /** The next batch of the mesher's work, its pieces independent of one another; null once the mesh is made. */
        fun nextMeshing(): List<() -> Unit>? {
            if (meshed) return null
            return run.next() ?: null.also { meshed = true }
        }

        /** The next batch of work, its pieces independent of one another: the mesher's, then the surface's; null when done. */
        fun next(): List<() -> Unit>? {
            nextMeshing()?.let { return it }
            val making = finish ?: SurfaceFinish(field, run.mesh).also { finish = it }
            return making.next()
        }

        /** The mesh, once [nextMeshing] has given null. */
        val mesh: SurfaceMesh get() = run.mesh

        val surface: CharacterSurface get() = checkNotNull(finish).surface
    }

    /** A build of [character]'s surface with [recesses] cut into it, at twice the cell when [dragging]; null when it has no field part. */
    fun start(character: AssembledCharacter, dragging: Boolean, recesses: List<MouthRecess> = emptyList()): Build? {
        val field = fieldOf(character, solids, cellShare, recesses) ?: return null
        if (fine == null || abs(field.cell / cell - 1f) > CELL_DRIFT) {
            cell = field.cell
            fine = AdaptiveMesher(cell, reads)
            coarse = AdaptiveMesher(cell * COARSE_FACTOR, reads)
        }
        val mesher = checkNotNull(if (dragging) coarse else fine)
        return Build(CharacterField(field.field, field.runs, mesher.cell, field.recesses), mesher, digitLevels(field))
    }

    /** [character]'s surface, built with [runAll] running each batch of work; null when it has no field part. */
    fun build(character: AssembledCharacter, dragging: Boolean = false, runAll: (List<() -> Unit>) -> Unit = { tasks -> tasks.forEach { it() } }): CharacterSurface? {
        val build = start(character, dragging) ?: return null
        while (true) runAll(build.next() ?: break)
        return build.surface
    }
}
