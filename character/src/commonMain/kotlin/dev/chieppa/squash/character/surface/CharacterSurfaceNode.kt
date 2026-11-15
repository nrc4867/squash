package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BuildStages
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

/** Whether a character is built as one surface, and whether a part of it is being dragged, which meshes it at the coarse cell. */
data class SurfaceMode(val on: Boolean, val dragging: Boolean)

/**
 * A character's field and the mesh it meshes to, before its surface is finished, and the placed [character] it is meshed from; [coarse]
 * when meshed at the coarse cell while a part is dragged.
 */
class MeshedSurface(val field: CharacterField, val mesh: SurfaceMesh, val character: AssembledCharacter, val coarse: Boolean = false)

/** What the one-surface mesh node holds: the character's mesh, or null for a character built as its parts' own meshes. */
class HeldMesh(val meshed: MeshedSurface?)

/** What the one-surface node holds: the character's surface, or null for a character built as its parts' own meshes; [coarse] while dragged. */
class HeldSurface(val surface: CharacterSurface?, val coarse: Boolean = false)

/**
 * The character [placed] holds, its parts on their sockets before any strip or garment, meshed as one surface while [mode] says so:
 * each batch of the mesher's work run in parallel on the graph's dispatcher, a stale run stopping between batches. A gesture that leaves
 * the placed character as it was, as a pose drag does, keeps its full mesh. See [SurfaceBuilder].
 */
fun Graph.characterMeshNode(placed: Node<*, AssembledCharacter>, mode: Flow<SurfaceMode>): Node<*, HeldMesh> {
    val builder = SurfaceBuilder()
    val lock = Mutex()
    var full: HeldMesh? = null
    return gather(mode, sourcesOf = { if (it.on) mapOf(PLACED to placed) else emptyMap() }, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { gathered ->
        val character = gathered.values[PLACED] ?: return@gather HeldMesh(null)
        lock.withLock {
            full?.takeIf { it.meshed?.character === character }?.let { return@withLock it }
            val dragging = gathered.shape.dragging
            val build = builder.start(character, dragging) ?: return@withLock HeldMesh(null)
            while (true) {
                currentCoroutineContext().ensureActive()
                IN_PARALLEL(build.nextMeshing() ?: break)
            }
            HeldMesh(MeshedSurface(build.field, build.mesh, character, dragging)).also { if (!dragging) full = it }
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
