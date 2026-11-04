package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.PatchSlot
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.BorderClosing
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv

/** How near an end ring, as a share of a patch's rings' range of u, a border's stretch ends for it to close past that ring. */
private const val NEAR_END_RING = 0.25f

private const val PATCH_SEPARATOR = ":"

/** The order color layers in; a later rank overdraws an earlier one. See HLD: look. */
private const val RANK_PATCH_SLOT = 0
private const val RANK_AREA = 1
private const val RANK_REGION = 2
private const val RANK_STROKE = 3

/** What a part's color reads besides its surface work: the parts its anchors name, and its template's areas and colored patches. */
data class PartColorInput(
    val parts: Set<PartId> = emptySet(),
    val areas: List<AreaSpec> = emptyList(),
    val patchSlots: List<PatchSlot> = emptyList(),
)

/** What [part] reads in [character], through its template in [templates]; empty when no such part is there. */
fun partColorInput(character: Character, part: PartId, templates: TemplateLibrary): PartColorInput {
    val instance = character.part(part) ?: return PartColorInput()
    val template = templates.find(instance.template) ?: return PartColorInput(surfaceWorkInput(character, part).parts)
    val params = character.resolved(instance).params
    return PartColorInput(surfaceWorkInput(character, part).parts, PartBuilder.areas(template, params), PartBuilder.patchSlots(template, params))
}

/** A shape on one patch of one part, the part its anchors name, with the slot it shows and its layer's rank. */
data class LandedShape(val rank: Int, val slot: SlotName, val part: PartId, val patch: String, val shape: ColorShape)

/** Every color shape [holder] holds, in layer order: its template's colored patches, its filled areas, its regions, its strokes. */
fun colorShapes(holder: PartId, work: ResolvedSurfaceWork, input: PartColorInput, surfaces: Map<PartId, PartSurface>): List<LandedShape> {
    val own = surfaces[holder]?.patches.orEmpty()
    return patchSlotShapes(holder, input, own) + areaShapes(holder, work, input, own) +
        work.regions.flatMap { region -> regionShapes(region, surfaces) } +
        work.strokes.flatMap { stroke -> strokeShapes(stroke, surfaces[holder]?.axisLength ?: 0f) }
}

private fun patchSlotShapes(holder: PartId, input: PartColorInput, patches: List<Patch>) = input.patchSlots.flatMap { colored ->
    patches.filter { it.name == colored.patch || it.name.startsWith(colored.patch + PATCH_SEPARATOR) }.map {
        LandedShape(RANK_PATCH_SLOT, SlotName(colored.slot), holder, it.name, ColorShape.WholePatch)
    }
}

private fun areaShapes(holder: PartId, work: ResolvedSurfaceWork, input: PartColorInput, patches: List<Patch>) = input.areas.flatMap { area ->
    val slot = work.areaFills[area.name] ?: return@flatMap emptyList()
    patches.filter { it.slices != null }.map {
        LandedShape(RANK_AREA, slot, holder, it.name, ColorShape.Rectangle(area.u.start, area.u.end, area.v.start, area.v.end))
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

private fun regionShapes(region: ResolvedRegion, surfaces: Map<PartId, PartSurface>): List<LandedShape> {
    val runs = runsAround(region.border)
    if (runs.size == 1) return listOf(landed(RANK_REGION, region.slot, runs.single(), ColorShape.Border(runs.single().onPatch())))
    return runs.groupBy { it.first().anchor.part to it.first().anchor.patch }.map { (place, stretches) ->
        val points = stretches.flatten()
        val patch = surfaces[place.first]?.patches?.firstOrNull { it.name == place.second }
        landed(RANK_REGION, region.slot, points, ColorShape.Border(points.onPatch(), closingOf(points, patch)))
    }
}

/** A stretch of border whose ends both lie near an end ring of [patch] runs off that ring, as at a socket; any other joins its ends. */
private fun closingOf(points: List<ResolvedPoint>, patch: Patch?): BorderClosing {
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

/** A stroke keeps one width wherever it lands: its radius is a share of [holderLength], the axis of the part that holds it. */
private fun strokeShapes(stroke: ResolvedBrushStroke, holderLength: Float): List<LandedShape> = runsAlong(stroke.points).map { run ->
    landed(RANK_STROKE, stroke.slot, run, ColorShape.Brush(run.onPatch(), stroke.radius * holderLength, stroke.softness))
}

private fun landed(rank: Int, slot: SlotName, points: List<ResolvedPoint>, shape: ColorShape) =
    LandedShape(rank, slot, points.first().anchor.part, points.first().anchor.patch, shape)
