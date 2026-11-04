package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.LineGrid
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A part's surface work as positions, following what it resolves to and the surfaces of the parts its anchors name, so a
 * border running onto a parent follows that parent's redraw too. A change that leaves those as they were does not reach it.
 */
fun Graph.partSurfaceWorkNode(
    document: Flow<SquashDocument>,
    part: PartId,
    surface: (PartId) -> Node<*, PartSurface>,
): Node<*, ResolvedSurfaceWork> = gather(
    document.map { surfaceWorkInput(it.character, part) }.distinctUntilChanged(),
    sourcesOf = { input -> input.parts.associateWith(surface) },
    stage = BuildStages.SURFACE_WORK,
) { (input, surfaces) ->
    resolveSurfaceWork(input, surfaces, cancellationCheck())
}

/** What the character's surface work reads: where the parts sit, and each part's work. */
private sealed interface BodyWorkSource {
    data class Placed(val character: AssembledCharacter) : BodyWorkSource

    data class Parts(val work: Map<PartId, ResolvedSurfaceWork>) : BodyWorkSource
}

private enum class BodyWorkKey { PLACED, PARTS }

/** Every part's surface work in body space, following [assembly] and each of [parts]' work. */
fun Graph.bodySurfaceWorkNode(
    parts: Flow<List<PartId>>,
    assembly: Node<*, AssembledCharacter>,
    work: (PartId) -> Node<*, ResolvedSurfaceWork>,
): Node<*, BodySurfaceWork> {
    val placed = follow<AssembledCharacter, BodyWorkSource>(assembly, stage = BuildStages.SURFACE_WORK) { BodyWorkSource.Placed(it) }
    val held = gather<List<PartId>, PartId, ResolvedSurfaceWork, BodyWorkSource>(
        parts,
        sourcesOf = { ids -> ids.associateWith(work) },
        stage = BuildStages.SURFACE_WORK,
    ) { BodyWorkSource.Parts(it.values) }
    return gather(
        MutableStateFlow(Unit),
        sourcesOf = { mapOf(BodyWorkKey.PLACED to placed, BodyWorkKey.PARTS to held) },
        stage = BuildStages.SURFACE_WORK,
    ) { (_, sources) ->
        val character = (sources.getValue(BodyWorkKey.PLACED) as BodyWorkSource.Placed).character
        val work = (sources.getValue(BodyWorkKey.PARTS) as BodyWorkSource.Parts).work
        placeSurfaceWork(work, character, cancellationCheck())
    }
}

/** What the character's lines read: where the parts sit, their grids and creases, and their surface work. */
private sealed interface LinesSource {
    data class Placed(val character: AssembledCharacter) : LinesSource

    data class Grids(val grids: Map<PartId, List<LineGrid>>) : LinesSource

    data class Creases(val creases: List<SocketCrease>) : LinesSource

    data class Work(val work: BodySurfaceWork) : LinesSource
}

private enum class LinesKey { PLACED, GRIDS, CREASES, WORK }

/**
 * The character as its lines read it, following [assembly] and [work]. Its grids and creases come from nodes of their own that
 * follow [assembly] alone, so a surface edit reweighs them and finds neither again.
 */
fun Graph.bodyLinesNode(assembly: Node<*, AssembledCharacter>, work: Node<*, BodySurfaceWork>): Node<*, BodyLines> {
    val placed = follow<AssembledCharacter, LinesSource>(assembly, stage = BuildStages.SURFACE_WORK) { LinesSource.Placed(it) }
    val grids = follow<AssembledCharacter, LinesSource>(assembly, stage = BuildStages.SURFACE_WORK) { LinesSource.Grids(lineGrids(it, cancellationCheck())) }
    val creases = follow<AssembledCharacter, LinesSource>(assembly, stage = BuildStages.SURFACE_WORK) { LinesSource.Creases(socketCreases(it, cancellationCheck())) }
    val held = follow<BodySurfaceWork, LinesSource>(work, stage = BuildStages.SURFACE_WORK) { LinesSource.Work(it) }
    return gather(
        MutableStateFlow(Unit),
        sourcesOf = { mapOf(LinesKey.PLACED to placed, LinesKey.GRIDS to grids, LinesKey.CREASES to creases, LinesKey.WORK to held) },
        stage = BuildStages.SURFACE_WORK,
    ) { (_, sources) ->
        bodyLines(
            (sources.getValue(LinesKey.PLACED) as LinesSource.Placed).character,
            (sources.getValue(LinesKey.GRIDS) as LinesSource.Grids).grids,
            (sources.getValue(LinesKey.CREASES) as LinesSource.Creases).creases,
            (sources.getValue(LinesKey.WORK) as LinesSource.Work).work,
            cancellationCheck(),
        )
    }
}
