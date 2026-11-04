package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PatchSlot
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.BorderClosing
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv

/** How near an end ring, as a share of a patch's rings' range of u, a border's stretch ends for it to close past that ring where it crosses to another patch of its own part. */
private const val NEAR_END_RING = 0.25f

private const val PATCH_SEPARATOR = ":"

/** What in the document a shape comes from. */
sealed interface ShapeSource {
    /** A template's colored [block], a patch or a row of them, that [holder] shows. */
    data class TemplateBlock(val holder: PartId, val block: String) : ShapeSource

    data class Area(val holder: PartId, val name: String) : ShapeSource

    data class Border(val holder: PartId, val id: RegionId) : ShapeSource

    data class Stroke(val holder: PartId, val id: BrushStrokeId) : ShapeSource
}

/** The order color layers in; a later rank overdraws an earlier one. See HLD: look. */
private const val RANK_PATCH_SLOT = 0
private const val RANK_AREA = 1
private const val RANK_REGION = 2
private const val RANK_STROKE = 3

/** Where a border crossing a socket from [parent] onto a part runs off that part. */
data class SocketEnd(val parent: PartId, val closing: BorderClosing)

/**
 * What a part's color reads besides its surface work: the parts its anchors name, its template's areas and colored patches, and for each
 * named part sitting on a socket, how a border crossing that socket closes on it.
 */
data class PartColorInput(
    val parts: Set<PartId> = emptySet(),
    val areas: List<AreaSpec> = emptyList(),
    val patchSlots: List<PatchSlot> = emptyList(),
    val socketEnds: Map<PartId, SocketEnd> = emptyMap(),
)

/** What [part] reads in [character], through its template in [templates]; empty when no such part is there. */
fun partColorInput(character: Character, part: PartId, templates: TemplateLibrary): PartColorInput {
    val instance = character.part(part) ?: return PartColorInput()
    val template = templates.find(instance.template) ?: return PartColorInput(surfaceWorkInput(character, part).parts)
    val params = character.resolved(instance).params
    val named = surfaceWorkInput(character, part).parts
    return PartColorInput(named, PartBuilder.areas(template, params), PartBuilder.patchSlots(template, params), socketEnds(character, named, templates))
}

private fun socketEnds(character: Character, parts: Set<PartId>, templates: TemplateLibrary): Map<PartId, SocketEnd> = parts.mapNotNull { id ->
    val instance = character.part(id) ?: return@mapNotNull null
    val parent = instance.socket?.parent ?: return@mapNotNull null
    val closing = if (templates.find(instance.template)?.openEnd == End.END) BorderClosing.PAST_LAST_RING else BorderClosing.PAST_FIRST_RING
    id to SocketEnd(parent, closing)
}.toMap()

/** A shape on one patch of one part, the part its anchors name, with the slot it shows, its layer's rank and what it comes from. */
data class LandedShape(val rank: Int, val slot: SlotName, val part: PartId, val patch: String, val shape: ColorShape, val source: ShapeSource? = null)

/** Every color shape [holder] holds, in layer order: its template's colored patches, its filled areas, its regions, its strokes. */
fun colorShapes(holder: PartId, work: ResolvedSurfaceWork, input: PartColorInput, surfaces: Map<PartId, PartSurface>): List<LandedShape> {
    val own = surfaces[holder]?.patches.orEmpty()
    return patchSlotShapes(holder, work, input, own) + areaShapes(holder, work, input, own) +
        work.regions.flatMap { region -> regionShapes(holder, region, input, surfaces) } +
        work.strokes.flatMap { stroke -> strokeShapes(holder, stroke, surfaces[holder]?.axisLength ?: 0f) }
}

/** Whether this patch is the one named [name] or one of a row so named. */
private fun Patch.isNamed(name: String) = this.name == name || this.name.startsWith(name + PATCH_SEPARATOR)

/** A colored patch shows its template's slot until a fill names it. */
private fun patchSlotShapes(holder: PartId, work: ResolvedSurfaceWork, input: PartColorInput, patches: List<Patch>) = input.patchSlots.flatMap { colored ->
    val slot = work.areaFills[colored.patch] ?: SlotName(colored.slot)
    patches.filter { it.isNamed(colored.patch) }.map {
        LandedShape(RANK_PATCH_SLOT, slot, holder, it.name, ColorShape.WholePatch, ShapeSource.TemplateBlock(holder, colored.patch))
    }
}

/** An area shows its template's slot until a fill names it, and with neither shows nothing. */
private fun areaShapes(holder: PartId, work: ResolvedSurfaceWork, input: PartColorInput, patches: List<Patch>) = input.areas.flatMap { area ->
    val slot = work.areaFills[area.name] ?: area.color?.let(::SlotName) ?: return@flatMap emptyList()
    patches.filter { it.isNamed(area.patch) }.map {
        LandedShape(RANK_AREA, slot, holder, it.name, ColorShape.Rectangle(area.u.start, area.u.end, area.v.start, area.vEnd), ShapeSource.Area(holder, area.name))
    }
}

/** Runs of neighbours in [points] that land on one patch of one part, as a closed loop starting where one begins. */
private fun runsAround(points: List<ResolvedPoint>): List<List<ResolvedPoint>> {
    fun place(point: ResolvedPoint) = point.anchor.part to point.anchor.patch
    val start = points.indices.firstOrNull { place(points[it]) != place(points[(it + points.size - 1) % points.size]) } ?: 0
    return runsAlong(points.drop(start) + points.take(start))
}

private fun runsAlong(points: List<ResolvedPoint>): List<List<ResolvedPoint>> {
    val runs = ArrayList<MutableList<ResolvedPoint>>()
    for (point in points) {
        val last = runs.lastOrNull()?.last()?.anchor
        if (last != null && last.part == point.anchor.part && last.patch == point.anchor.patch) runs.last() += point else runs += mutableListOf(point)
    }
    return runs
}

private fun List<ResolvedPoint>.onPatch() = map { SurfaceUv(it.anchor.u, it.anchor.v) }

private fun regionShapes(holder: PartId, region: ResolvedRegion, input: PartColorInput, surfaces: Map<PartId, PartSurface>): List<LandedShape> {
    val runs = runsAround(region.border)
    val source = ShapeSource.Border(holder, region.id)
    if (runs.size == 1) return listOf(landed(RANK_REGION, region.slot, runs.single(), ColorShape.Border(runs.single().onPatch()), source))
    val across = runs.indices.groupBy { runs[it].first().anchor.part to runs[it].first().anchor.patch }
    return across.map { (place, stretches) ->
        val points = stretches.flatMap { runs[it] }
        val beside = stretches.flatMap { listOf(runs[(it + runs.size - 1) % runs.size], runs[(it + 1) % runs.size]) }.map { it.first().anchor.part }
        val patch = surfaces[place.first]?.patches?.firstOrNull { it.name == place.second }
        landed(RANK_REGION, region.slot, points, ColorShape.Border(points.onPatch(), closingOf(place.first, points, beside, input, patch)), source)
    }
}

/**
 * How a stretch of border on [part] closes, given the parts of the stretches [beside] it. Crossing the socket from the part's parent, it
 * runs off the socket's ring. Crossing to another patch of the part, it runs off an end ring if both its ends lie near that ring. Otherwise,
 * as on a parent with the part holding its child's socket in the middle of its surface, its ends join.
 */
private fun closingOf(part: PartId, points: List<ResolvedPoint>, beside: List<PartId>, input: PartColorInput, patch: Patch?): BorderClosing {
    val socket = input.socketEnds[part]
    return when {
        socket != null && socket.parent in beside -> socket.closing
        beside.all { it == part } -> closingNearEnds(points, patch)
        else -> BorderClosing.ON_ITSELF
    }
}

private fun closingNearEnds(points: List<ResolvedPoint>, patch: Patch?): BorderClosing {
    val rings = patch?.ringUs ?: return BorderClosing.ON_ITSELF
    val first = rings.first()
    val last = rings.last()
    val reach = (last - first) * NEAR_END_RING
    val ends = listOf(points.first().anchor.u, points.last().anchor.u)
    return when {
        ends.all { it <= first + reach } -> BorderClosing.PAST_FIRST_RING
        ends.all { it >= last - reach } -> BorderClosing.PAST_LAST_RING
        else -> BorderClosing.ON_ITSELF
    }
}

/**
 * A stroke keeps one width wherever it lands: its radius is a share of [holderLength], the axis of the part that holds it. Each run stays open
 * where the stroke goes on elsewhere, so a soft stroke keeps its opacity to the crossing from both sides.
 */
private fun strokeShapes(holder: PartId, stroke: ResolvedBrushStroke, holderLength: Float): List<LandedShape> {
    val runs = runsAlong(stroke.points)
    return runs.mapIndexed { i, run ->
        val shape = ColorShape.Brush(run.onPatch(), stroke.radius * holderLength, stroke.softness, openStart = i > 0, openEnd = i < runs.lastIndex)
        landed(RANK_STROKE, stroke.slot, run, shape, ShapeSource.Stroke(holder, stroke.id))
    }
}

private fun landed(rank: Int, slot: SlotName, points: List<ResolvedPoint>, shape: ColorShape, source: ShapeSource) =
    LandedShape(rank, slot, points.first().anchor.part, points.first().anchor.patch, shape, source)
