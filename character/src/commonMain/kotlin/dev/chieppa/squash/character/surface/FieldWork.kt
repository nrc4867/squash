package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.placedBy
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [work], every part's surface work in body space, with each point whose anchor names a run of [field] landed on the field's surface:
 * from where its run's own surface puts it, along the field's gradient to the zero of the joined field, and off by its offset along
 * the normal there. Points on a part's other patches, on parts not in the field, and floating in the air with no normal stay where
 * they are. Each part is one piece of
 * [runAll]'s work.
 */
suspend fun fieldWork(work: BodySurfaceWork, field: CharacterField, runAll: RunAll = IN_TURN): BodySurfaceWork {
    val runs = field.runs.mapTo(HashSet()) { it.partId to it.patch.name }
    val anchors = FieldAnchors(field)
    val landed = { point: ResolvedPoint ->
        val anchor = point.anchor
        if (anchor.part to anchor.patch !in runs || point.normal == Vec3.ZERO) {
            point
        } else {
            val (position, normal) = anchors.onSurface(point.position - point.normal * anchor.offset, point.normal, anchors.cellOf(anchor.part, anchor.patch))
            ResolvedPoint(anchor, position + normal * anchor.offset, normal)
        }
    }
    val ids = work.parts.keys.toList()
    val out = arrayOfNulls<ResolvedSurfaceWork>(ids.size)
    runAll(ids.indices.map { i -> { out[i] = work.parts.getValue(ids[i]).placedBy(landed) } })
    return BodySurfaceWork(ids.indices.associate { ids[it] to requireNotNull(out[it]) })
}

private sealed interface FieldWorkSource {
    class Work(val work: BodySurfaceWork) : FieldWorkSource

    class Mesh(val held: HeldMesh) : FieldWorkSource
}

private enum class FieldWorkKey { WORK, MESH }

/**
 * [work] landed on the one surface [mesh] meshes, by [fieldWork], its parts in parallel on the graph's dispatcher; [work]'s own result
 * for a character without one.
 */
fun Graph.fieldWorkNode(work: Node<*, BodySurfaceWork>, mesh: Node<*, HeldMesh>): Node<*, BodySurfaceWork> {
    val sources: Map<FieldWorkKey, Node<*, FieldWorkSource>> = mapOf(
        FieldWorkKey.WORK to follow(work, cacheSize = 1, stage = BuildStages.SURFACE_WORK) { FieldWorkSource.Work(it) },
        FieldWorkKey.MESH to follow(mesh, cacheSize = 1, stage = BuildStages.SURFACE_WORK) { FieldWorkSource.Mesh(it) },
    )
    return gather(MutableStateFlow(Unit), sourcesOf = { sources }, cacheSize = 1, stage = BuildStages.SURFACE_WORK) { (_, held) ->
        val placed = (held.getValue(FieldWorkKey.WORK) as FieldWorkSource.Work).work
        val field = (held.getValue(FieldWorkKey.MESH) as FieldWorkSource.Mesh).held.meshed?.field ?: return@gather placed
        fieldWork(placed, field, IN_PARALLEL)
    }
}

