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

/** Whether a character is built as one surface, and whether a part of it is being dragged, which meshes it at the coarse cell. */
data class SurfaceMode(val on: Boolean, val dragging: Boolean)

/** A character's field and the mesh it meshes to, before its surface is finished, and the placed [character] it is meshed from. */
class MeshedSurface(val field: CharacterField, val mesh: SurfaceMesh, val character: AssembledCharacter)

/** What the one-surface mesh node holds: the character's mesh, or null for a character built as its parts' own meshes. */
class HeldMesh(val meshed: MeshedSurface?)

/** What the one-surface node holds: the character's surface, or null for a character built as its parts' own meshes. */
class HeldSurface(val surface: CharacterSurface?)

/**
 * The character [placed] holds, its parts on their sockets before any strip or garment, meshed as one surface while [mode] says so:
 * each batch of the mesher's work run in parallel on the graph's dispatcher, a stale run stopping between batches. See [SurfaceBuilder].
 */
fun Graph.characterMeshNode(placed: Node<*, AssembledCharacter>, mode: Flow<SurfaceMode>): Node<*, HeldMesh> {
    val builder = SurfaceBuilder()
    val lock = Mutex()
    return gather(mode, sourcesOf = { if (it.on) mapOf(PLACED to placed) else emptyMap() }, cacheSize = 1, stage = BuildStages.ASSEMBLY) { gathered ->
        val character = gathered.values[PLACED] ?: return@gather HeldMesh(null)
        lock.withLock {
            val build = builder.start(character, gathered.shape.dragging) ?: return@withLock HeldMesh(null)
            while (true) {
                currentCoroutineContext().ensureActive()
                IN_PARALLEL(build.nextMeshing() ?: break)
            }
            HeldMesh(MeshedSurface(build.field, build.mesh, character))
        }
    }
}

/** The surface of [meshed]'s mesh, its shading and lines, each batch of the work run in parallel on the graph's dispatcher. See [SurfaceFinish]. */
fun Graph.characterSurfaceNode(meshed: Node<*, HeldMesh>): Node<*, HeldSurface> =
    follow(meshed, cacheSize = 1, stage = BuildStages.ASSEMBLY) { held ->
        val made = held.meshed ?: return@follow HeldSurface(null)
        val finish = SurfaceFinish(made.field, made.mesh)
        while (true) {
            currentCoroutineContext().ensureActive()
            IN_PARALLEL(finish.next() ?: break)
        }
        HeldSurface(finish.surface)
    }

private const val PLACED = 0
