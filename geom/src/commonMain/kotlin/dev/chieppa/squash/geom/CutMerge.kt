package dev.chieppa.squash.geom

/** A color cut with the slot it shows. */
data class SlottedCut<S>(val slot: S, val cut: PatchCut)

/**
 * [layers] in draw order with each run of neighbours of one slot on one patch joined into one cut, so a later layer of another
 * slot still overdraws what comes before it. A cut with nothing in it goes.
 */
fun <S> mergedCuts(layers: List<SlottedCut<S>>): List<SlottedCut<S>> {
    val runs = ArrayList<MutableList<SlottedCut<S>>>()
    for (layer in layers) {
        if (layer.cut.isEmpty) continue
        val last = runs.lastOrNull()?.last()
        if (last != null && last.slot == layer.slot && last.cut.patch == layer.cut.patch) runs.last() += layer else runs += mutableListOf(layer)
    }
    return runs.map { run -> SlottedCut(run.first().slot, if (run.size == 1) run.first().cut else joined(run.map { it.cut })) }
}

private fun joined(cuts: List<PatchCut>): PatchCut {
    val vertices = FloatArray(cuts.sumOf { it.mesh.vertices.size })
    val indices = IntArray(cuts.sumOf { it.mesh.indices.size })
    val us = FloatArray(cuts.sumOf { it.us.size })
    val vs = FloatArray(us.size)
    var vertex = 0
    var index = 0
    for (cut in cuts) {
        cut.mesh.vertices.copyInto(vertices, vertex * RenderMesh.FLOATS_PER_VERTEX)
        cut.us.copyInto(us, vertex)
        cut.vs.copyInto(vs, vertex)
        for (i in cut.mesh.indices) indices[index++] = i + vertex
        vertex += cut.mesh.vertexCount
    }
    return PatchCut(cuts.first().patch, RenderMesh(vertices, indices), us, vs)
}
