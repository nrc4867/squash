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

/** What a part's build reads: its template and what the part resolves to. */
data class PartBuildInput(val template: TemplateRef, val data: PartData)

/**
 * The derived step from what one part resolves to, its parameters and outlines, to its patches and their slices. A
 * change that leaves those as they were does not reach it, and a change to a pair's data reaches both sides. A run stops
 * between blocks once cancelled. A part missing from the document has no patches.
 */
fun Graph.sliceRowNode(
    document: Flow<SquashDocument>,
    part: PartId,
    templates: TemplateLibrary,
): Node<PartBuildInput?, PartBuild> =
    node(document.map { it.partBuildInput(part) }.distinctUntilChanged()) { input ->
        if (input == null) return@node PartBuild(null, emptyList())
        val template = templates[input.template]
        PartBuild(template, PartBuilder.build(template, input.data, cancellationCheck()))
    }

private fun SquashDocument.partBuildInput(part: PartId): PartBuildInput? =
    character.part(part)?.let { PartBuildInput(it.template, character.resolved(it)) }
