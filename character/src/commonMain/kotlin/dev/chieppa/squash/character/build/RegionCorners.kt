package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor

/**
 * Each of [regions]' corners as the points that are corners: its own, and every junction, an anchor it shares with another region
 * where the two borders part. Two regions drawn through the same anchors then meet along one edge, with no gap and no overlap, since
 * a stretch of border between corners is laid from its own anchors alone.
 */
fun regionCorners(regions: List<ResolvedRegion>): Map<ResolvedRegion, Set<Int>> {
    val neighbours = HashMap<Anchor, MutableList<Set<Anchor>>>()
    for (region in regions) region.border.forEachIndexed { i, point ->
        neighbours.getOrPut(point.anchor) { ArrayList() } += region.neighboursOf(i)
    }
    return regions.associateWith { region ->
        val junctions = region.border.indices.filter { i ->
            val beside = region.neighboursOf(i)
            val others = neighbours.getValue(region.border[i].anchor)
            others.size > 1 && others.any { it != beside && !it.containsAll(beside) }
        }
        region.corners + junctions
    }
}

private fun ResolvedRegion.neighboursOf(i: Int): Set<Anchor> =
    setOf(border[(i + border.size - 1) % border.size].anchor, border[(i + 1) % border.size].anchor)
