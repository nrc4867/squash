package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.document.Membrane
import dev.chieppa.squash.document.MembraneEdge
import dev.chieppa.squash.document.MembraneId
import dev.chieppa.squash.document.SetMembrane
import dev.chieppa.squash.recipe.model.RecipeMembraneEdge

/** The membranes, each spanned between parts the recipe placed. */
internal fun BuildContext.membranes() = recipe.membranes.forEachIndexed { i, membrane ->
    val path = "$.membranes[$i]"
    if (membrane.first.isEmpty()) fail("$path.first", "a rail needs at least one edge")
    if (membrane.second.isEmpty()) fail("$path.second", "a rail needs at least one edge")
    val first = membrane.first.mapIndexed { j, edge -> edge("$path.first[$j]", edge) }
    val second = membrane.second.mapIndexed { j, edge -> edge("$path.second[$j]", edge) }
    val color = membrane.color?.let { slot("$path.color", it) }
    val id = MembraneId(membrane.id)
    send(path, SetMembrane(id, guard(path) { Membrane(id, first, second, membrane.edge, membrane.billow, membrane.thickness, membrane.sink, color, membrane.mirrored) }))
}

private fun BuildContext.edge(path: String, edge: RecipeMembraneEdge): MembraneEdge {
    val part = partOf("$path.part", edge.part)
    val (fromU, fromV) = pair(path, "from", edge.from, "[u, v]")
    val (toU, toV) = edge.to?.let { pair(path, "to", it, "[u, v]") } ?: (fromU to fromV)
    return MembraneEdge(part, fromU, fromV, toU, toV, edge.patch)
}
