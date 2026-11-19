package dev.chieppa.squash.recipe.export

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.View
import dev.chieppa.squash.recipe.model.RecipeFur
import dev.chieppa.squash.recipe.model.RecipeLineWeight
import dev.chieppa.squash.recipe.model.RecipeMark
import dev.chieppa.squash.recipe.model.RecipeOutline
import dev.chieppa.squash.recipe.model.RecipeRegion
import dev.chieppa.squash.recipe.model.RecipeShape
import dev.chieppa.squash.recipe.pen.PenCurves
import dev.chieppa.squash.recipe.pen.PenPoint

/** [curve] as the few places it passes through when a smooth outline through them makes it exactly, else as its own Bézier points. */
internal fun outline(curve: OutlineCurve): RecipeOutline = throughForm(curve) ?: RecipeOutline(bezier = curve.points.map { listOf(it.x, it.y) })

/** [curve] as places it passes through, a place a corner where a handle beside it sits on it, when [PenCurves] remakes it exactly. */
private fun throughForm(curve: OutlineCurve): RecipeOutline? {
    val points = curve.points
    val ends = points.indices.filter { it % 3 == 0 }
    val corners = ends.filter { i -> (i > 0 && points[i - 1] == points[i]) || (i < points.lastIndex && points[i + 1] == points[i]) }
    val closed = points.size > 4 && points.first() == points.last()
    val places = (if (closed) ends.dropLast(1) else ends).map { PenPoint(points[it].x, points[it].y, corner = it in corners) }
    val remade = runCatching { if (closed) PenCurves.loop(places) else PenCurves.drawn(places) }.getOrNull()
    if (remade != curve) return null
    return RecipeOutline(through = places.map { listOf(it.x, it.y) }, corners = places.indices.filter { places[it].corner }, closed = closed)
}

internal fun outlines(curves: Map<View, OutlineCurve>): Map<View, RecipeOutline> = curves.mapValues { (_, c) -> outline(c) }

/** [anchors] in the shortest recipe form: u, v on [part]'s one patch when they all lie there, else the anchors themselves. */
private fun uvForm(part: PartId, anchors: List<Anchor>): Pair<String, List<List<Float>>>? {
    val patch = anchors.firstOrNull()?.patch ?: BODY_PATCH
    if (anchors.any { it.part != part || it.patch != patch || it.offset != 0f }) return null
    return patch to anchors.map { listOf(it.u, it.v) }
}

internal fun region(part: PartId, region: Region): RecipeRegion {
    val uv = uvForm(part, region.border)
    return RecipeRegion(
        region.id.value, region.slot.value, region.ink, uv?.first ?: BODY_PATCH, border = uv?.second, anchors = if (uv == null) region.border else null,
        corners = region.corners.sorted(), curved = region.curved, mirrored = region.mirrored,
    )
}

/** [region] printed on a garment's cloth, naming the part its border lies on where it lies on one. */
internal fun printedRegion(region: Region): RecipeRegion {
    val part = region.border.first().part
    return region(part, region).let { if (it.border != null) it.copy(on = part.value) else it }
}

/** [mark] printed on a garment's cloth, naming the part its places lie on where they lie on one. */
internal fun printedMark(mark: Mark): RecipeMark {
    val part = mark.points.first().part
    return mark(part, mark).let { if (it.points != null) it.copy(on = part.value) else it }
}

internal fun mark(part: PartId, mark: Mark): RecipeMark {
    val uv = uvForm(part, mark.points)
    return RecipeMark(
        mark.id.value, mark.kind, uv?.first ?: BODY_PATCH, points = uv?.second, anchors = if (uv == null) mark.points else null,
        width = mark.width.stops, ink = mark.ink, slot = mark.slot?.value, mirrored = mark.mirrored, cameraLocked = mark.cameraLocked,
        versions = mark.versions.mapKeys { it.key.value }, flight = mark.flight,
    )
}

/** [surface]'s fur rule and line weights on [part]; the rest a recipe part holds field by field. */
internal fun fur(surface: SurfaceWork): RecipeFur? = surface.fur?.let { RecipeFur(it.spacingPixels, it.lengthPixels) }

internal fun lineWeights(part: PartId, surface: SurfaceWork, notCarried: MutableList<String>): List<RecipeLineWeight> = surface.lineWeights.mapNotNull { weight ->
    val uv = uvForm(part, weight.points)
    if (uv == null) notCarried += "${part.value}: line weight ${weight.id.value} runs off the part"
    uv?.let { RecipeLineWeight(weight.id.value, it.second, weight.radius, weight.amount, it.first) }
}

/** What a recipe part cannot hold of [surface], noted against [part]. */
internal fun surfaceLeftOut(part: PartId, surface: SurfaceWork, notCarried: MutableList<String>) {
    if (surface.grain != null) notCarried += "${part.value}: combed grain"
    if (surface.paint.strokes.isNotEmpty()) notCarried += "${part.value}: ${surface.paint.strokes.size} brush strokes"
    if (surface.seams.isNotEmpty()) notCarried += "${part.value}: ${surface.seams.size} seam strokes"
}

/** [data], laid over [part] by a garment, as a recipe shape. */
internal fun shape(part: PartId, data: PartData, notCarried: MutableList<String>, where: String): RecipeShape {
    val split = SplitParams(data.params, anatomy = false)
    val surface = data.surface
    if (surface.marks.isNotEmpty() || surface.groups.isNotEmpty() || surface.lineWeights.isNotEmpty() || surface.fur != null) notCarried += "$where: marks, groups, fur or line weights on a garment's change"
    surfaceLeftOut(part, surface, notCarried)
    return RecipeShape(
        split.size, split.lean, split.outward, outlines(data.outlines), split.others,
        surface.baseSlot?.value, surface.areaFills.mapValues { it.value.value }, surface.regions.map { region(part, it) },
    )
}
