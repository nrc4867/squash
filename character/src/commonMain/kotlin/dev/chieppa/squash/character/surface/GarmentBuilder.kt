package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.geom.surface.SurfaceMesher

/** The work a garment build's batch does: meshing a garment, finishing its surface, cutting it, or finding the skin it hides. */
enum class DressStage { MESH, FINISH, CUT, HIDE }

/**
 * Builds the garments a one surface wears, keeping a mesher per garment and cell between builds, so an edit meshes a garment again only
 * where the edited member reaches, keeping each garment whole while nothing it covers changed, and cutting it again on its kept surface
 * where only a part coming out through it moved. A garment needs only the body's field
 * and mesh, not its finished surface, and reads its channels from the body's blocks where they hold the same solids. One build at a
 * time; hand out its batches with [Build.next] until it has none.
 */
class GarmentBuilder {
    private val meshers = HashMap<Triple<GarmentId, PartId?, Float>, SurfaceMesher>()

    /** Each garment's last build, with what its surface was grown from and what it was cut by. */
    private val kept = HashMap<Pair<GarmentId, PartId?>, Kept>()
    private val shells = ShellCache()
    private val solids = RunSolids()
    private val clips = RunClips()

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
        private val pending = ArrayDeque(covers.mapIndexed { i, cover -> WornCover.of(cover, body, covers.take(i), shells) }.also { shells.settle() }.flatMap { it.pieces(body) })
        private val made = ArrayList<SurfaceGarment>()
        private var cover: WornCover? = null
        private var run: SurfaceMesher.MeshRun? = null
        private var finish: SurfaceFinish? = null
        private var cutting: GarmentCut? = null
        private var grown: CharacterField? = null
        private var recut: CharacterSurface? = null

        /** What the last batch [next] gave works on. */
        var stage: DressStage = DressStage.MESH
            private set

        fun next(): List<() -> Unit>? {
            while (true) {
                stage = DressStage.MESH
                run?.let { meshing -> meshing.next()?.let { return it } }
                val making = finish ?: run?.let { SurfaceFinish(checkNotNull(grown), it.mesh).also { f -> finish = f } }
                if (making != null) {
                    stage = DressStage.FINISH
                    making.next()?.let { return it }
                }
                val surface = making?.surface ?: recut
                if (surface != null) {
                    stage = DressStage.CUT
                    val worn = checkNotNull(cover)
                    val cut = cutting ?: GarmentCut(worn, surface, membersOf(worn.holes, field, body, solids), membersOf(worn.outThroughEnds, field, body, solids)).also { cutting = it }
                    cut.next()?.let { return it }
                    val garment = cut.garment
                    kept[garment.cover.garment.id to garment.cover.piece] = Kept(shapeOf(garment.cover, checkNotNull(grown)), cutOf(garment.cover), garment)
                    made += garment
                    run = null
                    finish = null
                    cutting = null
                    recut = null
                }
                val nextCover = pending.removeFirstOrNull() ?: return null.also { clips.settle() }
                val nextField = garmentField(field, nextCover, body, solids, clips) ?: continue
                val last = kept[nextCover.garment.id to nextCover.piece]
                if (last != null && last.shape == shapeOf(nextCover, nextField)) {
                    if (last.cut == cutOf(nextCover)) {
                        made += last.garment
                        continue
                    }
                    cover = nextCover
                    grown = nextField
                    recut = last.garment.surface
                    continue
                }
                cover = nextCover
                grown = nextField
                run = meshers.getOrPut(Triple(nextCover.garment.id, nextCover.piece, nextField.cell)) { SurfaceMesher(nextField.cell) }.begin(nextField.field, mesh.channels)
            }
        }

        /** What a garment is cut by: its cover, and the members of the parts that come out through its holes and its ends as they read. */
        private fun cutOf(worn: WornCover): Any = listOf(worn.key, membersOf(worn.holes + worn.outThroughEnds, field, body, solids).map { it.key })

        /** The garments made, innermost first, once [next] has given null. */
        val garments: List<SurfaceGarment> get() = made.toList()
    }

    /** A build of [covers]' garments, worn over [body], on the body [mesh] meshes from [field]. */
    fun start(field: CharacterField, mesh: SurfaceMesh, body: AssembledCharacter, covers: List<GarmentCover>): Build = Build(field, mesh, body, covers)

    /** What a garment's surface is grown from: equal for the same shape of cover over members that read the same at the same cell. */
    private fun shapeOf(cover: WornCover, field: CharacterField): Any =
        listOf(cover.shapeKey, field.field.members.map { it.key to (it.blend to it.parent) }, field.cell)

    /** A garment as last built, with what its surface was grown from, [shape], and what it was cut by, [cut]. */
    private class Kept(val shape: Any, val cut: Any, val garment: SurfaceGarment)
}
