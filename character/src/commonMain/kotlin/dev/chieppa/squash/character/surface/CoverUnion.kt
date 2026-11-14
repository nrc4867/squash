package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartId

/**
 * The garments [worn] together as one cover: over each part, the widest stretch their branches the whole way round it cover between
 * them, the ranges that meet or overlap joined, at the most ease of any; null for fewer than two garments. What lies deep inside it is
 * covered however the stretch is shared between the garments, as a leg under a sleeve that a boot's shaft overlaps.
 */
fun coverUnion(worn: List<WornCover>): WornCover? {
    if (worn.size < 2) return null
    val byPart = LinkedHashMap<PartId, MutableList<GarmentBranch>>()
    for (cover in worn) for ((part, branch) in cover.branches) if (branch.allRound) byPart.getOrPut(part, ::ArrayList) += branch
    val merged = LinkedHashMap<PartId, GarmentBranch>()
    for ((part, branches) in byPart) {
        val sorted = branches.sortedBy { it.from }
        var best: GarmentBranch? = null
        var from = sorted.first().from
        var to = sorted.first().to
        var ease = sorted.first().ease
        fun close() {
            val run = GarmentBranch(part, from, to, ease)
            if (best == null || to - from > checkNotNull(best).to - checkNotNull(best).from) best = run
        }
        for (branch in sorted.drop(1)) {
            if (branch.from <= to) {
                to = maxOf(to, branch.to)
                ease = maxOf(ease, branch.ease)
            } else {
                close()
                from = branch.from
                to = branch.to
                ease = branch.ease
            }
        }
        close()
        merged[part] = checkNotNull(best)
    }
    val open = merged.keys.associateWith { part ->
        val reaching = worn.filter { part in it.branches }
        val low = reaching.all { it.openEnds[part]?.low == true }
        val high = reaching.all { it.openEnds[part]?.high == true }
        OpenEnds(low, high, if (low || high) reaching.flatMapTo(HashSet()) { it.openEnds[part]?.parts.orEmpty() } else emptySet())
    }
    return WornCover(worn.first().cover, merged, emptySet(), emptySet(), emptyMap(), openEnds = open)
}
