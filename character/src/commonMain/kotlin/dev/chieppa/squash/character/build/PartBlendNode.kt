package dev.chieppa.squash.character.build

import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck

/**
 * The derived step from a part's slice build to its patches with their crossings filleted, following [builds]: the same patches,
 * grids and slice coordinates, with the vertices near a crossing moved, when the part's template has a `blend` block switched on.
 */
fun Graph.partBlendNode(builds: Node<*, PartBuild>): Node<PartBuild, PartBuild> = follow(builds, stage = BuildStages.MESH) { build ->
    val blend = build.blend ?: return@follow build
    PartBuild(build.template, blend.applyTo(build.patches, cancellationCheck()))
}
