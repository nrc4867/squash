package dev.chieppa.squash.character.catalog

import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.graph.GraphProgress
import dev.chieppa.squash.graph.StageCount

/** The graph's stages behind each row of the Fit section. */
object FitStages {
    val SKELETON = listOf(BuildStages.SLICES)
    val SURFACE = listOf(BuildStages.MESH, BuildStages.SURFACE, BuildStages.ASSEMBLY)
}

/** The nodes behind [stages] together. */
fun GraphProgress.count(stages: List<String>): StageCount {
    val counts = stages.mapNotNull { this.stages[it] }
    return StageCount(counts.sumOf { it.done }, counts.sumOf { it.total })
}

/** A block as Built from names it: its own name in its template, or its kind. */
fun blockLabel(block: Block): String = displayName(block.name ?: block::class.simpleName.orEmpty()).lowercase()
