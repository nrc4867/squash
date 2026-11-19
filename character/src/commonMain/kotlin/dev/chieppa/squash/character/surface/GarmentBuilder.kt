package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
import dev.chieppa.squash.geom.field.ReadRounds
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.geom.surface.SurfaceMesher

/** The work a garment build's batch does: meshing a garment, finishing its surface, cutting it, or finding the skin it hides. */
enum class DressStage { MESH, FINISH, CUT, HIDE }

/**
 * Builds the garments a one surface wears, keeping a mesher per garment and cell between builds, so an edit meshes a garment again only
 * where the edited member reaches, keeping each garment whole while nothing it covers changed, and cutting it again on its kept surface
 * where only a part coming out through it moved, and taking it as it is, to be painted again, where only what its cloth prints changed. A garment needs only the body's field
 * and mesh, not its finished surface, and reads its channels from the body's blocks where they hold the same solids. One build at a
 * time; hand out its batches with [Build.next] until it has none; where reads are gathered, the garments' rounds of reads are read
 * together. The meshers read the garments' fields through [reads].
 */
class GarmentBuilder(private val reads: FieldReads = CpuFieldReads) {
    private val meshers = HashMap<Triple<GarmentId, PartId?, Float>, SurfaceMesher>()

    /** Each garment's last build, with what its surface was grown from and what it was cut by. */
    private val kept = HashMap<Pair<GarmentId, PartId?>, Kept>()
    private val shells = ShellCache()
    private val solids = RunSolids()
    private val clips = RunClips()
    private val coverage = CoverageMemo()

    /**
     * One build of [covers]' garments, worn over [body], on the body meshed as [mesh] from [field]: each garment's mesher's batches, then
     * its finishing's, then its cut's.
     */
    inner class Build internal constructor(
        private val field: CharacterField,
        private val mesh: SurfaceMesh,
        private val body: AssembledCharacter,
        covers: List<GarmentCover>,
    ) {
        private val pieces = covers.mapIndexed { i, cover -> WornCover.of(cover, body, covers.take(i), shells) }.also { shells.settle() }.flatMap { it.pieces(body) }
        private val made = arrayOfNulls<SurfaceGarment>(pieces.size)
        private var works: List<Piece>? = null

        init {
            coverage.begin()
        }

        /** What the last batch [next] gave works on, the first of the garments' at once. */
        var stage: DressStage = DressStage.MESH
            private set

        /** The next batch: every garment still being made gives its next batch, so the garments are made beside one another. */
        fun next(): List<() -> Unit>? {
            val running = works ?: begin().also { works = it }
            val tasks = ArrayList<() -> Unit>()
            var first: DressStage? = null
            val asking = running.mapNotNull { it.asking }
            if (asking.isNotEmpty()) {
                first = DressStage.MESH
                tasks += { ReadRounds.answerTogether(reads, asking) }
            }
            for (work in running) {
                if (work.asking != null) continue
                val batch = work.next() ?: continue
                if (first == null) first = work.stage
                tasks += batch
            }
            if (first == null) {
                clips.settle()
                coverage.settle()
                return null
            }
            stage = first
            return tasks
        }

        /** A piece of work for each garment that is not kept whole, those kept whole taken as they are. */
        private fun begin(): List<Piece> = pieces.indices.mapNotNull { i ->
            val nextCover = pieces[i]
            val nextField = garmentField(field, nextCover, body, solids, clips) ?: return@mapNotNull null
            val last = kept[nextCover.garment.id to nextCover.piece]
            if (last != null && last.shape == shapeOf(nextCover, nextField)) {
                if (last.cut == cutOf(nextCover)) {
                    made[i] = if (last.garment.cover.cover == nextCover.cover) last.garment else last.garment.reprinted(nextCover).also { kept[nextCover.garment.id to nextCover.piece] = Kept(last.shape, last.cut, it) }
                    return@mapNotNull null
                }
                return@mapNotNull Piece(i, nextCover, nextField, null, last.garment.surface)
            }
            val mesher = meshers.getOrPut(Triple(nextCover.garment.id, nextCover.piece, nextField.cell)) { SurfaceMesher(nextField.cell, reads) }
            Piece(i, nextCover, nextField, mesher.begin(nextField.field, mesh.channels), null)
        }

        /** One garment being made: meshed by [run] and finished, or cut again on its kept surface [recut]. */
        private inner class Piece(val index: Int, val cover: WornCover, val grown: CharacterField, val run: SurfaceMesher.MeshRun?, val recut: CharacterSurface?) {
            private var finish: SurfaceFinish? = null
            private var cutting: GarmentCut? = null
            private var done = false
            var stage: DressStage = DressStage.MESH
                private set

            /** The round of reads the garment's mesher waits on, answered beside the other garments'. */
            val asking: ReadRounds? get() = if (done || finish != null) null else run?.asking

            fun next(): List<() -> Unit>? {
                if (done) return null
                stage = DressStage.MESH
                run?.next()?.let { return it }
                val making = finish ?: run?.let { SurfaceFinish(grown, it.mesh).also { f -> finish = f } }
                if (making != null) {
                    stage = DressStage.FINISH
                    making.next()?.let { return it }
                }
                val surface = making?.surface ?: checkNotNull(recut)
                stage = DressStage.CUT
                val cut = cutting ?: GarmentCut(cover, surface, membersOf(cover.holes, field, body, solids), membersOf(cover.outThroughEnds, field, body, solids), coverage, cover.garment.id to cover.piece).also { cutting = it }
                cut.next()?.let { return it }
                val garment = cut.garment
                kept[garment.cover.garment.id to garment.cover.piece] = Kept(shapeOf(garment.cover, grown), cutOf(garment.cover), garment)
                made[index] = garment
                done = true
                return null
            }
        }

        /** What a garment is cut by: its cover, and the members of the parts that come out through its holes and its ends as they read. */
        private fun cutOf(worn: WornCover): Any = listOf(worn.key, membersOf(worn.holes + worn.outThroughEnds, field, body, solids).map { it.key })

        /** The garments made, innermost first, once [next] has given null. */
        val garments: List<SurfaceGarment> get() = made.filterNotNull()
    }

    /** A build of [covers]' garments, worn over [body], on the body [mesh] meshes from [field]. */
    fun start(field: CharacterField, mesh: SurfaceMesh, body: AssembledCharacter, covers: List<GarmentCover>): Build = Build(field, mesh, body, covers)

    /** What a garment's surface is grown from: equal for the same shape of cover over members that read the same at the same cell. */
    private fun shapeOf(cover: WornCover, field: CharacterField): Any =
        listOf(cover.shapeKey, field.field.members.map { it.key to (it.blend to it.parent) }, field.cell)

    /** A garment as last built, with what its surface was grown from, [shape], and what it was cut by, [cut]. */
    private class Kept(val shape: Any, val cut: Any, val garment: SurfaceGarment)
}
