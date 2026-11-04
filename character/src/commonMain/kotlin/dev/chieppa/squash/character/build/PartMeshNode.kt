package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck

/**
 * The derived step from a part's patches and template to its mesh, following [builds]. Both are its input, so either
 * changing runs it again. No patches make an empty mesh.
 */
fun Graph.partMeshNode(builds: Node<*, PartBuild>, part: PartId): Node<PartBuild, PartMesh> = follow(builds, stage = BuildStages.MESH) { build ->
    val template = build.template
    if (build.patches.isEmpty() || template == null) PartMesh(part.value, emptyList())
    else PartBuilder.mesh(part.value, template, build.patches, cancellationCheck())
}
