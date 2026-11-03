package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.document.PartInstance
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The derived step from one part instance to its patches and their slices. Other parts' changes do not reach it.
 * A part missing from the document has no patches.
 */
fun Graph.sliceRowNode(
    document: Flow<SquashDocument>,
    part: PartId,
    templates: TemplateLibrary,
): Node<PartInstance?, List<Patch>> =
    node(document.map { it.character.part(part) }.distinctUntilChanged()) { instance ->
        if (instance == null) return@node emptyList()
        currentCoroutineContext().ensureActive()
        PartBuilder.build(templates[instance.template], instance.params)
    }
