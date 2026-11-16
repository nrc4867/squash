package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.catalog.isLeader
import dev.chieppa.squash.character.shape.BodyChart
import dev.chieppa.squash.character.shape.ChartBox
import dev.chieppa.squash.character.shape.SurfaceRegions
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentEdge
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetBranchAround
import dev.chieppa.squash.document.SetBranchRange
import dev.chieppa.squash.document.SetGarmentEdges
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.UncoverPart

/** The leading side of each pair's other side in [character], by part. */
fun leadersOf(character: Character): Map<PartId, PartId> =
    character.parts.filterNot { it.isLeader }.mapNotNull { part -> character.twinOf(part)?.let { part.id to it.id } }.toMap()

/** [body]'s own parts as a chart, the other sides of pairs read as their leaders. */
fun bodyChart(character: Character, body: AssembledCharacter): BodyChart {
    val absent = character.parts.filter { it.garment == null && body.part(it.id) == null }.mapNotNull { part -> part.socket?.let { part.id to it } }.toMap()
    return BodyChart.of(body, leadersOf(character), absent) { it.owner == null }
}

/**
 * A garment's drawn shape on a body: its [edges] cutting the body's chart into [regions], and which of them are cloth, [filled]: those
 * holding a place of [fill], or with none pressed, the smaller side of each closed hem; never the smaller side of a hole.
 */
class GarmentShape(val chart: BodyChart, val edges: List<GarmentEdge>, val fill: List<Anchor>) {
    val regions = SurfaceRegions(chart, edges.map { it.curve })

    /** The regions inside the garment's holes: the smaller side of each, of those no place of [fill] lies in. */
    private val holes: Set<Int> by lazy {
        val pressed = fill.mapNotNullTo(HashSet()) { regions.regionAt(it) }
        edges.indices.filter { edges[it].hole }.mapNotNullTo(HashSet()) { i -> regions.smallest(regions.sides[i] - pressed) }
    }

    val filled: Set<Int> by lazy {
        if (fill.isNotEmpty()) {
            fill.mapNotNullTo(HashSet()) { regions.regionAt(it) }
        } else {
            edges.indices.filter { edges[it].curve.closed && !edges[it].hole }.mapNotNullTo(HashSet()) { regions.smallest(regions.sides[it]) } - holes
        }
    }

    /** A place in each filled region, which fills the same regions as [fill] does. */
    val places: List<Anchor> get() = filled.sorted().mapNotNull(regions::placeIn)

    /**
     * The fill after a press at [at]: an empty region pressed fills, and the filled regions across an edge from it empty, so a press
     * on the other side of a closed edge flips it; a filled region pressed empties. Unchanged on an edge or off the body.
     */
    fun pressed(at: Anchor): List<Anchor> {
        val region = regions.regionAt(at) ?: return fill
        val next = if (region in filled) filled - region else filled - regions.across(region) + region
        return next.sorted().mapNotNull { if (it == region) at else regions.placeIn(it) }
    }

    /** The places of the fill that lie outside [regionsOut]. */
    fun fillOutside(regionsOut: Set<Int>): List<Anchor> = places.filter { regions.regionAt(it) !in regionsOut }

    /**
     * This shape's cloth as [after], the same body cut by more edges, holds it: a place in each region of [after] most of whose cells
     * are cloth here, or with [anyCloth] any of whose cells are, but those of [out]. What was cloth stays cloth and what was bare stays
     * bare.
     */
    fun carriedTo(after: GarmentShape, out: Set<Int>, anyCloth: Boolean = false): List<Anchor> {
        val cloth = IntArray(after.regions.count)
        val all = IntArray(after.regions.count)
        for (part in after.chart.parts) {
            val next = after.regions.labels.getValue(part.id)
            val now = regions.labels[part.id] ?: continue
            for (cell in next.indices) {
                val region = next[cell]
                if (region < 0) continue
                all[region]++
                if (now[cell] in filled) cloth[region]++
            }
        }
        val kept = (0 until after.regions.count).filter { it !in out && if (anyCloth) cloth[it] > 0 else cloth[it] * 2 > all[it] }
        return kept.mapNotNull(after.regions::placeIn)
    }

    /**
     * The fill once edge [index] is taken away: a region the removed edge's sides merge into is cloth where most of it was, or, for a
     * hole's edge, where any of it was, as closing a hole covers what it opened; the inside of each hole that remains stays bare.
     */
    fun without(index: Int): List<Anchor> {
        val after = GarmentShape(chart, edges.filterIndexed { i, _ -> i != index }, fill)
        val insides = after.edges.indices.filter { after.edges[it].hole }.mapNotNullTo(HashSet()) { after.regions.smallest(after.regions.sides[it]) }
        return carriedTo(after, insides, anyCloth = edges[index].hole)
    }

    /** What of each part the cloth holds. */
    val boxes: Map<PartId, ChartBox> by lazy { regions.boxes(filled) }

    companion object {
        /** [garment]'s shape on [body], with [edges] and [fill] in place of its own. */
        fun of(character: Character, body: AssembledCharacter, edges: List<GarmentEdge>, fill: List<Anchor>) =
            GarmentShape(bodyChart(character, body), edges, fill)
    }
}

/**
 * The commands that give [garment] [edges] and [fill] and write its branches and changes from them, as one step: a branch over each
 * part the cloth reaches, its range along and round the part bounding what the cloth holds of it, kept where it had one, through
 * [SetBranchRange] and [SetBranchAround]; a part sitting inside the cloth that the cloth leaves out comes through with no cuff; and
 * the small parts inside it tucked or shaped by [tucksOf]. A garment left with no edges covers nothing; one that never had any keeps
 * the branches it has.
 */
fun shapeCommands(character: Character, body: AssembledCharacter, garment: Garment, edges: List<GarmentEdge>, fill: List<Anchor>): DocumentCommand {
    val set = SetGarmentEdges(garment.id, edges, fill)
    if (edges.isEmpty()) {
        if (!garment.drawn) return set
        val cleared = garment.branches.map { UncoverPart(garment.id, it.part) } + garment.changes.keys.map { SetBodyPartChange(garment.id, it, null) }
        return Together(listOf(set) + cleared)
    }
    val shape = GarmentShape.of(character, body, edges, fill)
    val worn = shapeOf(character, body, garment.copy(edges = edges, fill = fill), shape)
    val commands = ArrayList<DocumentCommand>()
    commands += set
    for (held in garment.branches) if (held.part !in worn.branches) commands += UncoverPart(garment.id, held.part)
    for ((part, box) in worn.branches) {
        val held = garment.branch(part)
        if (held == null) {
            commands += CoverPart(garment.id, GarmentBranch(part, box.from, box.to, fromV = box.fromV, toV = box.toV))
            continue
        }
        if (held.from != box.from || held.to != box.to) commands += SetBranchRange(garment.id, part, box.from, box.to)
        if (held.fromV != box.fromV || held.toV != box.toV) commands += SetBranchAround(garment.id, part, box.fromV, box.toV)
    }
    for (part in garment.changes.keys + worn.changes.keys) {
        val next = worn.changes[part]
        if (garment.changes[part] != next) commands += SetBodyPartChange(garment.id, part, next)
    }
    return Together(commands)
}

/** The branches and changes a garment's drawn shape writes. */
private class WornShape(val branches: Map<PartId, ChartBox>, val changes: Map<PartId, BodyPartChange>)

private fun shapeOf(character: Character, body: AssembledCharacter, garment: Garment, shape: GarmentShape): WornShape {
    val boxes = shape.boxes
    val tucks = tucksOf(character, body, garment, boxes)
    val changes = HashMap<PartId, BodyPartChange>()
    val hidden = tucks.filter { !it.kind.digits && it.tucked }.mapTo(HashSet()) { it.part }
    hidden.forEach { changes[it] = BodyPartChange.Hide }
    tucks.filter { it.kind.digits && it.tucked }.groupBy { it.part }.forEach { (part, items) ->
        changes[part] = BodyPartChange.Redraw(PartData(params = items.associate { digitParam(it.kind) to 0f }))
    }
    val branches = boxes.filterKeys { it !in hidden }
    for (part in shape.chart.parts) {
        val parent = part.parent ?: continue
        if (part.id in branches || part.id in hidden) continue
        val over = branches[parent] ?: continue
        if (part.socketU in over.from..over.to) changes[part.id] = BodyPartChange.Through(cuff = false)
    }
    return WornShape(branches, changes)
}
