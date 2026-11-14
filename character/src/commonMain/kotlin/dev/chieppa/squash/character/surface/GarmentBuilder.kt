package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.geom.surface.SurfaceMesher

/** The work a garment build's batch does: meshing a garment, finishing its surface, cutting it, or finding the skin it hides. */
enum class DressStage { MESH, FINISH, CUT, HIDE }

/**
 * Builds the garments a one surface wears, keeping a mesher per garment and cell between builds, so an edit meshes a garment again only
 * where the edited member reaches, and keeping each garment whole while nothing it covers changed. A garment needs only the body's field
 * and mesh, not its finished surface, and reads its channels from the body's blocks where they hold the same solids. One build at a
 * time; hand out its batches with [Build.next] until it has none.
 */
class GarmentBuilder {
    private val meshers = HashMap<Pair<GarmentId, Float>, SurfaceMesher>()

    /** Each garment's last build, with what it was grown from: its cover, its members as they read and its cell. */
    private val kept = HashMap<GarmentId, Pair<Any, SurfaceGarment>>()
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
        private val pending = ArrayDeque(covers.mapIndexed { i, cover -> WornCover.of(cover, body, covers.take(i), shells) }.also { shells.settle() })
        private val made = ArrayList<SurfaceGarment>()
        private var cover: WornCover? = null
        private var run: SurfaceMesher.MeshRun? = null
        private var finish: SurfaceFinish? = null
        private var cutting: GarmentCut? = null
        private var grown: CharacterField? = null

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
                    stage = DressStage.CUT
                    val worn = checkNotNull(cover)
                    val cut = cutting ?: GarmentCut(worn, making.surface, membersOf(worn.holes, field, body, solids)).also { cutting = it }
                    cut.next()?.let { return it }
                    val garment = cut.garment
                    kept[garment.cover.garment.id] = keyOf(garment.cover, checkNotNull(grown), holesOf(garment.cover)) to garment
                    made += garment
                    run = null
                    finish = null
                    cutting = null
                }
                val nextCover = pending.removeFirstOrNull() ?: return null.also { clips.settle() }
                val nextField = garmentField(field, nextCover, body, solids, clips) ?: continue
                val last = kept[nextCover.garment.id]
                if (last != null && last.first == keyOf(nextCover, nextField, holesOf(nextCover))) {
                    made += last.second
                    continue
                }
                cover = nextCover
                grown = nextField
                run = meshers.getOrPut(nextCover.garment.id to nextField.cell) { SurfaceMesher(nextField.cell) }.begin(nextField.field, mesh.channels)
            }
        }

        private fun holesOf(worn: WornCover): List<Any> = membersOf(worn.holes, field, body, solids).map { it.key }

        /** The garments made, innermost first, once [next] has given null. */
        val garments: List<SurfaceGarment> get() = made.toList()
    }

    /** A build of [covers]' garments, worn over [body], on the body [mesh] meshes from [field]. */
    fun start(field: CharacterField, mesh: SurfaceMesh, body: AssembledCharacter, covers: List<GarmentCover>): Build = Build(field, mesh, body, covers)

    /** What a garment is grown from: equal for the same cover over members that read the same at the same cell, with the same holes. */
    private fun keyOf(cover: WornCover, field: CharacterField, holes: List<Any>): Any =
        listOf(cover.key, field.field.members.map { it.key to (it.blend to it.parent) }, field.cell, holes)
}
