package dev.chieppa.squash.geom

/** An edge of the grid: from ring and segment, along u when [alongU] and around v otherwise. */
private data class GridEdge(val ring: Int, val segment: Int, val alongU: Boolean)

/**
 * The curves on this patch where [side] crosses zero, read at its grid points and placed along a grid edge in proportion to the
 * values at its ends. A closed curve repeats its first place last. Where a cell's four corners alternate, its edges pair in the
 * order they come round the cell, which leaves each curve unbroken though which pair joins is a guess.
 */
fun Patch.zeroCurves(side: (Vec3) -> Float): List<List<SurfaceUv>> {
    val values = FloatArray(nu * nv) { side(point(it)) }
    fun at(ring: Int, segment: Int) = values[index(ring, segment % nv)]
    val links = HashMap<GridEdge, MutableList<GridEdge>>()
    for (ring in 0 until nu - 1) {
        for (segment in 0 until segments) {
            val crossed = buildList {
                if (crosses(at(ring, segment), at(ring, segment + 1))) add(GridEdge(ring, segment, alongU = false))
                if (crosses(at(ring, segment + 1), at(ring + 1, segment + 1))) add(GridEdge(ring, (segment + 1) % nv, alongU = true))
                if (crosses(at(ring + 1, segment + 1), at(ring + 1, segment))) add(GridEdge(ring + 1, segment, alongU = false))
                if (crosses(at(ring + 1, segment), at(ring, segment))) add(GridEdge(ring, segment, alongU = true))
            }
            crossed.chunked(2).filter { it.size == 2 }.forEach { (a, b) ->
                links.getOrPut(a) { ArrayList(2) } += b
                links.getOrPut(b) { ArrayList(2) } += a
            }
        }
    }
    return walked(links).map { run -> run.map { uvOf(it, values) } }
}

private fun crosses(a: Float, b: Float) = (a < 0f) != (b < 0f)

/** Each run through [links], from an end where it has one, so an open curve comes out whole. */
private fun walked(links: Map<GridEdge, List<GridEdge>>): List<List<GridEdge>> {
    val seen = HashSet<GridEdge>()
    val runs = ArrayList<List<GridEdge>>()
    fun walk(start: GridEdge) {
        val run = arrayListOf(start)
        seen += start
        var at = start
        while (true) {
            val next = links.getValue(at).firstOrNull { it !in seen } ?: break
            seen += next
            run += next
            at = next
        }
        if (run.size > 2 && start in links.getValue(at)) run += start
        runs += run
    }
    links.filterValues { it.size == 1 }.keys.forEach { if (it !in seen) walk(it) }
    links.keys.forEach { if (it !in seen) walk(it) }
    return runs
}

private fun Patch.uvOf(edge: GridEdge, values: FloatArray): SurfaceUv {
    val rings = ringUs
    val a = values[index(edge.ring, edge.segment)]
    if (edge.alongU) {
        val b = values[index(edge.ring + 1, edge.segment)]
        return SurfaceUv(rings[edge.ring] + (rings[edge.ring + 1] - rings[edge.ring]) * a / (a - b), edge.segment.toFloat() / segments)
    }
    val b = values[index(edge.ring, (edge.segment + 1) % nv)]
    return SurfaceUv(rings[edge.ring], (edge.segment + a / (a - b)) / segments)
}
