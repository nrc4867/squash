package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node

/**
 * The derived step from a part's patches to its mesh, following [patches]. [templateOf] gives the part's template
 * while it is in the document; no patches make an empty mesh.
 */
fun Graph.partMeshNode(
    patches: Node<*, List<Patch>>,
    part: PartId,
    templateOf: () -> PartTemplate?,
): Node<List<Patch>, PartMesh> = follow(patches) { built ->
    val template = templateOf()
    if (built.isEmpty() || template == null) PartMesh(part.value, emptyList())
    else PartBuilder.mesh(part.value, template, built)
}
