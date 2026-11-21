package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.boxCells
import dev.chieppa.squash.geom.PartSurface
import kotlin.math.floor

/** The rows of a part's chart beside each end that count as reaching that end, as a [dev.chieppa.squash.document.ChartBox] reads them. */
private const val END_ROWS = 1

/**
 * The painted cloth of [garment] over each part of [branches] as a [ClothField], each of its areas' cells, read on a pair's leader
 * through [twins]. A part no area paints has none and is not covered.
 */
fun clothOf(garment: Garment, branches: Map<PartId, GarmentBranch>, twins: Map<PartId, PartId>): Map<PartId, ClothField> {
    if (garment.areas.isEmpty()) return emptyMap()
    val out = HashMap<PartId, ClothField>()
    val made = HashMap<PartId, ClothField?>()
    for (part in branches.keys) {
        val leader = if (garment.areas.any { part in it.cells }) part else twins[part] ?: continue
        val field = made.getOrPut(leader) {
            val cells = DrawnCloth.cellsOf(garment, leader)
            ClothField.of(cells, DrawnCloth.of(garment, leader))
        } ?: continue
        out[part] = field
    }
    return out
}

/** The cloth of a cuff, [branch], a band round its part from its root: the cells its box holds, its run past the root kept by [branch]. */
fun cuffCloth(branch: GarmentBranch): ClothField = checkNotNull(ClothField.of(listOf(boxCells(branch))))

/** Whether this cloth reaches the low end of its part, the root, as a range from 0 does. */
val ClothReach.reachesLow: Boolean get() = firstRow <= END_ROWS

/** Whether this cloth reaches the high end of its part, as a range to 1 does. */
val ClothReach.reachesHigh: Boolean get() = lastRow >= CHART_ROWS - 1 - END_ROWS

/**
 * Where along its part, in u, [branch]'s cloth [reach] runs: from the edge of its first painted row to that of its last, from 0 or to 1
 * where it reaches an end, and on past an end where [branch] runs past it, below 0 or past 1.
 */
fun alongOf(reach: ClothReach, branch: GarmentBranch): ClosedFloatingPointRange<Float> {
    val from = if (reach.reachesLow) minOf(branch.from, 0f) else reach.firstRow.toFloat() / CHART_ROWS
    val to = if (reach.reachesHigh) maxOf(branch.to, 1f) else (reach.lastRow + 1f) / CHART_ROWS
    return from..to
}

/**
 * [branch] over the region its cloth [reach] paints: along its part as [alongOf] reads it, round it from the edge of the cells' first
 * column to that of their last, its drawing and ease kept. What decides the parts that come out through a garment, with a cuff or none,
 * and those that hang inside it, so a garment painted and one given as boxes decide alike from the same cells.
 */
fun clothBranch(branch: GarmentBranch, reach: ClothReach): GarmentBranch {
    val along = alongOf(reach, branch)
    return branch.copy(from = along.start, to = along.endInclusive, fromV = reach.fromV, toV = reach.toV)
}

/**
 * Whether (u, v) of its part lies in the region this cloth's cells paint, as [clothBranch] reads it: along from its first row's edge to
 * its last's, from 0 or to 1 where it reaches an end, and round from its first column's edge to its last's. What decides whether a part
 * on a socket there sits inside the cloth.
 */
fun ClothReach.holds(u: Float, v: Float): Boolean {
    val from = if (reachesLow) 0f else firstRow.toFloat() / CHART_ROWS
    val to = if (reachesHigh) 1f else (lastRow + 1f) / CHART_ROWS
    if (u < from || u > to) return false
    if (allRound) return true
    val ahead = v - fromV - floor(v - fromV)
    return ahead <= toV - fromV
}

/** Whether [anchor], on [surface], its part's, lies under this cover's cloth on its part's first run, whose (u, v) the cloth reads. */
fun GarmentCover.coversAnchor(anchor: Anchor, surface: PartSurface): Boolean {
    val held = cloth[anchor.part] ?: return false
    if (surface.patches.getOrNull(firstRunIndex(surface.patches))?.name != anchor.patch) return false
    return held.at(anchor.u, anchor.v) >= CLOTH_HEM_LEVEL
}
