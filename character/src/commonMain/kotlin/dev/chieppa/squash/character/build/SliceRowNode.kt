package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What a part's build reads: its template, what the part resolves to and the length the character's other legs leave it. */
data class PartBuildInput(val template: TemplateRef, val data: PartData, val lengthFactor: Float = 1f, val onSocket: Boolean = true)

/**
 * The derived step from what one part resolves to under the garments worn (see [wornPart]), its parameters and outlines, to its patches and their slices. A
 * change that leaves those as they were does not reach it, and a change to a pair's data reaches both sides. A run stops
 * between blocks once cancelled. A part missing from the document has no patches.
 */
fun Graph.sliceRowNode(
    document: Flow<SquashDocument>,
    part: PartId,
    templates: TemplateLibrary,
    lengths: LegLengthsOf = LegLengthsOf(templates),
): Node<PartBuildInput?, PartBuild> =
    node(document.map { it.partBuildInput(part, lengths) }.distinctUntilChanged(), stage = BuildStages.SLICES) { input ->
        if (input == null) return@node PartBuild(null, emptyList())
        val template = templates[input.template].asBuilt(input.data.params, input.onSocket)
        PartBuilder.partBuild(template, input.data, cancellationCheck(), input.lengthFactor)
    }

/**
 * The derived step from what one part resolves to bare, before any worn garment's change to its outlines, template or shape, to the
 * patches its sockets are read from: its skeleton, so a garment that reshapes a part never moves what sits on it. It keeps the worn
 * part's placement, so the skeleton turns as the part shows. While no garment reshapes the part it has no patches, and the part's
 * own build is its skeleton.
 */
fun Graph.skeletonRowNode(
    document: Flow<SquashDocument>,
    part: PartId,
    templates: TemplateLibrary,
    lengths: LegLengthsOf = LegLengthsOf(templates),
): Node<PartBuildInput?, PartBuild> =
    node(document.map { it.skeletonBuildInput(part, lengths) }.distinctUntilChanged(), stage = BuildStages.SLICES) { input ->
        if (input == null) return@node PartBuild(null, emptyList())
        val template = templates[input.template].asBuilt(input.data.params, input.onSocket)
        PartBuilder.partBuild(template, input.data, cancellationCheck(), input.lengthFactor)
    }

private fun SquashDocument.partBuildInput(part: PartId, lengths: LegLengthsOf): PartBuildInput? =
    character.part(part)?.let { instance ->
        val worn = character.wornPart(instance)
        if (worn.hidden) return@let null
        PartBuildInput(worn.template, worn.data.forBuild(), lengths.of(character).ofPart(part), instance.socket != null)
    }

/** What [part] builds bare, with the placement it wears, or null when no worn garment reshapes it. */
private fun SquashDocument.skeletonBuildInput(part: PartId, lengths: LegLengthsOf): PartBuildInput? {
    val worn = partBuildInput(part, lengths) ?: return null
    val instance = character.part(part) ?: return null
    val bare = character.resolved(instance).forBuild()
    val placed = worn.data.params.filterKeys(PlacementParams::isPlacement)
    val skeleton = worn.copy(template = instance.template, data = bare.copy(params = bare.params.filterKeys { !PlacementParams.isPlacement(it) } + placed))
    return skeleton.takeIf { it != worn }
}

/** This data's shape as a part's build reads it, without what only says where its children sit or how its rig turns. */
private fun PartData.forBuild(): PartData = shape.copy(params = params.filterKeys { !PlacementParams.isSocket(it) && !HandSockets.isHandSocket(it) && !RigParams.isRig(it) })
