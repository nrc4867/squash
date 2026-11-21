package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyRecesses
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.mouth.MouthRecess
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How many recent builds each node of the one surface's stack keeps by its inputs, so undo, redo and an outfit worn again take them as they were. */
const val KEPT_BUILDS = 4

/**
 * A character's field and the mesh it meshes to, before its surface is finished, and the placed [character] it is meshed from; [coarse]
 * when meshed at the coarse cell while a part is dragged.
 */
class MeshedSurface(
    val field: CharacterField,
    val mesh: SurfaceMesh,
    val character: AssembledCharacter,
    val coarse: Boolean = false,
    val recesses: List<MouthRecess> = emptyList(),
)

/** What the one-surface mesh node holds: the character's mesh, or null for a character with no part in the field. */
class HeldMesh(val meshed: MeshedSurface?)

/** What the one-surface node holds: the character's surface, or null for a character with no part in the field; [coarse] while dragged. */
class HeldSurface(val surface: CharacterSurface?, val coarse: Boolean = false)

/**
 * The character [placed] holds, its parts on their sockets before any garment, meshed as one surface, at the coarse cell while
 * [dragging] says a part is being dragged: each batch of the mesher's work run in parallel on the graph's dispatcher, a stale run stopping between batches. A gesture that leaves
 * the placed character as it was, as a pose drag does, keeps its full mesh. Its field is read through [reads]. See [SurfaceBuilder].
 */
fun Graph.characterMeshNode(
    placed: Node<*, AssembledCharacter>,
    dragging: Flow<Boolean>,
    reads: FieldReads = CpuFieldReads,
    recesses: Node<*, BodyRecesses>? = null,
): Node<*, HeldMesh> {
    val builder = SurfaceBuilder(reads = reads)
    val lock = Mutex()
    var full: HeldMesh? = null
    val character = follow<AssembledCharacter, MeshSource>(placed, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { MeshSource.Placed(it) }
    val cuts = recesses?.let { follow<BodyRecesses, MeshSource>(it, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { held -> MeshSource.Cuts(held) } }
    val sources = listOfNotNull(PLACED to character, cuts?.let { RECESSES to it }).toMap()
    return gather(dragging, sourcesOf = { sources }, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { gathered ->
        val placedNow = (gathered.values[PLACED] as? MeshSource.Placed)?.character ?: return@gather HeldMesh(null)
        val cut = (gathered.values[RECESSES] as? MeshSource.Cuts)?.recesses?.all.orEmpty()
        lock.withLock {
            full?.takeIf { it.meshed?.character === placedNow && it.meshed.recesses == cut }?.let { return@withLock it }
            val coarse = gathered.shape
            val build = builder.start(placedNow, coarse, cut) ?: return@withLock HeldMesh(null)
            while (true) {
                currentCoroutineContext().ensureActive()
                IN_PARALLEL(build.nextMeshing() ?: break)
            }
            HeldMesh(MeshedSurface(build.field, build.mesh, placedNow, coarse, cut)).also { if (!coarse) full = it }
        }
    }
}

/** The surface of [meshed]'s mesh, its shading and lines, each batch of the work run in parallel on the graph's dispatcher. See [SurfaceFinish]. */
fun Graph.characterSurfaceNode(meshed: Node<*, HeldMesh>): Node<*, HeldSurface> =
    follow(meshed, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { held ->
        val made = held.meshed ?: return@follow HeldSurface(null)
        val finish = SurfaceFinish(made.field, made.mesh)
        while (true) {
            currentCoroutineContext().ensureActive()
            IN_PARALLEL(finish.next() ?: break)
        }
        HeldSurface(finish.surface, made.coarse)
    }

private const val PLACED = 0
private const val RECESSES = 1

/** What the one-surface mesh node gathers: the placed character, and the recesses cut into it. */
private sealed interface MeshSource {
    class Placed(val character: AssembledCharacter) : MeshSource

    class Cuts(val recesses: BodyRecesses) : MeshSource
}
