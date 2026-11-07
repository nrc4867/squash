package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What a part's build reads: its template, what the part resolves to and the length the character's other legs leave it. */
data class PartBuildInput(val template: TemplateRef, val data: PartData, val lengthFactor: Float = 1f, val onSocket: Boolean = true)

/**
 * The derived step from what one part resolves to, its parameters and outlines, to its patches and their slices. A
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
        PartBuild(template, PartBuilder.build(template, input.data, cancellationCheck(), input.lengthFactor))
    }

private fun SquashDocument.partBuildInput(part: PartId, lengths: LegLengthsOf): PartBuildInput? =
    character.part(part)?.let { instance ->
        val data = character.resolved(instance)
        PartBuildInput(instance.template, data.shape.copy(params = data.params.filterKeys { !PlacementParams.isSocket(it) && !HandSockets.isHandSocket(it) && !RigParams.isRig(it) }), lengths.of(character).ofPart(part), instance.socket != null)
    }
