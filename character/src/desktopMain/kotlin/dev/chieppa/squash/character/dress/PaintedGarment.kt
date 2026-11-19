package dev.chieppa.squash.character.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.shape.BodyChart
import dev.chieppa.squash.character.surface.ClothField
import dev.chieppa.squash.character.surface.ClothReach
import dev.chieppa.squash.character.surface.areaLevel
import dev.chieppa.squash.character.surface.onLevel
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.ChartBox
import dev.chieppa.squash.character.shape.MaskEdge
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.character.shape.edgesOf
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentHole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName

/** How far along a part, in u, the cuff band round a hole at its root reaches. */
const val CUFF_BAND_U = 0.12f

/** What new area ids start with, before their number. */
const val NEW_AREA = "area-"

/**
 * One painted area of a garment as a [mask] on a body's chart, by its [id] and [name], blank while it has no name of its own, in its own
 * palette slot [color] or the garment's, gathering its cloth in to [gather] where it sets one.
 */
class PaintedArea(val id: GarmentAreaId, val name: String, val mask: SurfaceMask, val color: SlotName? = null, val gather: Float? = null) {
    /** This area as the garment saves it. */
    fun saved(): GarmentArea = GarmentArea(id, name, mask.runs(), color, gather)

    fun with(mask: SurfaceMask): PaintedArea = PaintedArea(id, name, mask, color, gather)
}

/** One edge of a garment's cloth: an [edge] of the area at [area] in the garment's areas. */
class GarmentEdgeOn(val area: Int, val edge: MaskEdge)

/**
 * A garment's cloth on a body's [chart]: its [areas] painted and its [holes]. The [cloth] is every area's cells but those of each holed
 * part and the parts beyond it, with a band round the root of each part whose hole has a cuff; its [edges] are each area's, a seam
 * where it meets another area.
 */
class PaintedGarment(val chart: BodyChart, val areas: List<PaintedArea>, val holes: List<GarmentHole>) {
    /** The parts the holes let out, with every part beyond each. */
    val holed: Set<PartId> by lazy {
        val out = HashSet<PartId>()
        val queue = ArrayDeque(holes.mapNotNull { chart.part(it.part)?.id })
        while (queue.isNotEmpty()) {
            val part = queue.removeFirst()
            if (out.add(part)) queue += chart.childrenOf(part).map { it.id }
        }
        out
    }

    /** Every area's cells together, without those of [holed] parts, with each cuff's band. */
    val cloth: SurfaceMask by lazy {
        val all = areas.fold(SurfaceMask.empty(chart)) { held, area -> held + area.mask }
        if (holed.isEmpty()) return@lazy all
        val cuffed = holes.filter { it.cuff }.mapNotNullTo(HashSet()) { chart.part(it.part)?.id }
        val bands = SurfaceMask.build(chart) { part, cell -> part in cuffed && (cell / CHART_COLUMNS + 0.5f) / CHART_ROWS < CUFF_BAND_U }
        all - SurfaceMask.whole(chart, holed) + bands
    }

    /** What of each part the cloth holds. */
    val boxes: Map<PartId, ChartBox> by lazy { cloth.boxes() }

    /** Where the cloth's cells lie on each part it holds, by the pair's leader. */
    private val reaches: Map<PartId, ClothReach> by lazy {
        chart.parts.mapNotNull { part -> ClothReach.of(BooleanArray(CHART_CELLS) { cloth.held(part.id, it) })?.let { part.id to it } }.toMap()
    }

    /** Where the cloth's cells lie on [part], read on a pair's leader, or null where it holds none of it. */
    fun reachOf(part: PartId): ClothReach? = reaches[chart.leaderOf(part)]

    /** Each area's cells, without those of [holed] parts. */
    private val shown: List<SurfaceMask> by lazy { areas.map { if (holed.isEmpty()) it.mask else it.mask - SurfaceMask.whole(chart, holed) } }

    /** Each part's painted cloth as the garment is cut on it, by the pair's leader, its areas' fields in the areas' order. */
    val fields: Map<PartId, ClothField> by lazy {
        chart.parts.mapNotNull { part ->
            ClothField.of(shown.map { mask -> BooleanArray(CHART_CELLS) { mask.held(part.id, it) } })?.let { part.id to it }
        }.toMap()
    }

    /**
     * Each area's edges in turn, those along another area's cells seams: the curves its cloth is cut on (see [areaLevel]), found along its
     * cells' sides and moved onto the level, those on a part's root ring kept there.
     */
    val edges: List<GarmentEdgeOn> by lazy {
        areas.indices.flatMap { i ->
            val others = shown.filterIndexed { j, _ -> j != i }.fold(SurfaceMask.empty(chart)) { held, other -> held + other }
            edgesOf(shown[i], others.takeUnless { it.isEmpty }).map { edge ->
                val field = fields[chart.leaderOf(edge.part)]
                val rooted = chart.part(edge.part)?.parent != null
                val curve = if (field == null) edge.curve else onLevel(edge.curve, { rooted && it.u <= 0f }) { u, v -> field.areaLevel(i, u, v) }
                GarmentEdgeOn(i, MaskEdge(edge.part, curve, edge.seam))
            }
        }
    }

    /** Each edge's hem as few handles (see [HemHandles]), in the order of [edges]; null for an edge too short to fit. */
    val hems: List<HemHandles?> by lazy { edges.map { HemHandles.of(it.edge.curve) } }

    /** The parts where one area's cells meet another's across the root ring: the part's root cells in one, its parent's cell in another. */
    val seams: Set<PartId> by lazy {
        if (areas.size < 2) return@lazy emptySet()
        chart.parts.filter { part ->
            val parent = part.parent ?: return@filter false
            if (part.id in holed) return@filter false
            val socket = chart.socketCell(part)
            val outer = areas.indexOfFirst { it.mask.held(parent, socket) }
            if (outer < 0) return@filter false
            val root = (0 until CHART_COLUMNS)
            areas.withIndex().any { (j, area) -> j != outer && root.count { area.mask.held(part.id, it) } * 2 > root.count() }
        }.mapTo(HashSet()) { it.id }
    }

    /** The area of [id], or null. */
    fun area(id: GarmentAreaId?): PaintedArea? = areas.firstOrNull { it.id == id }

    /** This garment with its areas [areas] and holes [holes]. */
    fun with(areas: List<PaintedArea> = this.areas, holes: List<GarmentHole> = this.holes): PaintedGarment = PaintedGarment(chart, areas, holes)

    /** The areas as the garment saves them. */
    fun savedAreas(): List<GarmentArea> = areas.map { it.saved() }
}

/** [garment]'s cloth on [body]: its painted areas. The last one asked for is kept for the same garment and body. */
fun paintedOf(character: Character, body: AssembledCharacter, garment: Garment): PaintedGarment = PaintMemo.of(character, body, garment)

private object PaintMemo {
    private var key: Triple<Garment, AssembledCharacter, Any>? = null
    private var held: PaintedGarment? = null

    @Synchronized
    fun of(character: Character, body: AssembledCharacter, garment: Garment): PaintedGarment {
        val now = key
        val last = held
        if (last != null && now != null && now.first == garment && now.second === body && now.third == character.parts) return last
        val chart = bodyChart(character, body)
        val areas = garment.areas.map { PaintedArea(it.id, it.name, SurfaceMask.of(chart, it.cells), it.color, it.gather) }
        return PaintedGarment(chart, areas, garment.holes).also {
            key = Triple(garment, body, character.parts)
            held = it
        }
    }
}
