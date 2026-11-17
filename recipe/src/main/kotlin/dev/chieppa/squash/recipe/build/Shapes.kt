package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.View
import dev.chieppa.squash.recipe.model.RecipeOutline
import dev.chieppa.squash.recipe.model.RecipeRegion
import dev.chieppa.squash.recipe.model.RecipeShape
import dev.chieppa.squash.recipe.pen.PenCurves
import dev.chieppa.squash.recipe.pen.PenPoint

/** The fewest places a smooth outline passes through. */
private const val MIN_THROUGH = 2

/** [outline] as the document's Bézier curve, failing at [path] when it is not one. */
internal fun BuildContext.curve(path: String, outline: RecipeOutline): OutlineCurve {
    val through = outline.through
    val bezier = outline.bezier
    if ((through == null) == (bezier == null)) fail(path, "an outline gives either through or bezier, not ${if (through == null) "neither" else "both"}")
    if (bezier != null) {
        val points = bezier.mapIndexed { i, p -> pair(path, "bezier[$i]", p, "[x, y]").let { (x, y) -> CurvePoint(x, y) } }
        if (points.size < 4 || (points.size - 1) % 3 != 0) fail("$path.bezier", "a Bézier outline holds 3n + 1 points (a start, then two handles and an end per segment), not ${points.size}")
        return OutlineCurve(points)
    }
    val places = requireNotNull(through).mapIndexed { i, p -> pair(path, "through[$i]", p, "[x, y]") }
    if (places.size < MIN_THROUGH) fail("$path.through", "an outline passes through at least $MIN_THROUGH places, not ${places.size}")
    outline.corners.forEachIndexed { i, c -> if (c !in places.indices) fail("$path.corners[$i]", "corner $c is not an index into through (0 to ${places.lastIndex})") }
    val pens = places.mapIndexed { i, (x, y) -> PenPoint(x, y, corner = i in outline.corners) }
    return if (outline.closed) PenCurves.loop(pens) else PenCurves.drawn(pens)
}

/** [point] as two numbers, failing at [path].[field] with what it should be. */
internal fun BuildContext.pair(path: String, field: String, point: List<Float>, what: String): Pair<Float, Float> {
    if (point.size != 2) fail("$path.$field", "a place is $what, not $point")
    return point[0] to point[1]
}

/** The outlines of [outlines] as the document's curves. */
internal fun BuildContext.curves(path: String, outlines: Map<View, RecipeOutline>): Map<View, OutlineCurve> =
    outlines.mapValues { (view, outline) -> curve("$path.outlines.${view.name.lowercase()}", outline) }

/** The parameters a part's [size], [lean] and [outward] stand for, then [params]. */
internal fun shapeParams(size: Float?, lean: Float?, outward: Float?, params: Map<String, Float>): Map<String, Float> =
    listOfNotNull(size?.let { SIZE_PARAM to it }, lean?.let { PlacementParams.LEAN to it }, outward?.let { PlacementParams.OUTWARD to it }).toMap() + params

/** Warns at [path] of each parameter that is neither [template]'s nor a dotted part parameter such as `move.lean` or `socket.1.u`. */
internal fun BuildContext.checkParams(path: String, template: PartTemplate, params: Map<String, Float>) {
    params.keys.filter { '.' !in it && it !in template.params }.forEach { name ->
        warn("$path.params.$name", "template ${template.id.value} has no parameter \"$name\"; it has ${template.params.keys.sorted().joinToString().ifEmpty { "none" }}")
    }
}

/** A region on [part] from [region], its border given one of three ways. */
internal fun BuildContext.region(path: String, part: PartId, region: RecipeRegion): Region {
    val border = anchors(path, part, region.patch, region.border, region.face, region.anchors, "border")
    val slot = slot("$path.slot", region.slot)
    return guard(path) { Region(RegionId(region.id), border, slot, region.ink) }
}

/** Anchors on [part] from exactly one of [uv] on [patch], [face] places on its face, or [given] anchors. */
internal fun BuildContext.anchors(path: String, part: PartId, patch: String, uv: List<List<Float>>?, face: List<List<Float>>?, given: List<Anchor>?, uvName: String): List<Anchor> {
    val forms = listOfNotNull(uv?.let { uvName }, face?.let { "face" }, given?.let { "anchors" })
    if (forms.size != 1) fail(path, "give its places one way, as $uvName, face or anchors, not ${forms.joinToString(" and ").ifEmpty { "none" }}")
    return when {
        uv != null -> uv.mapIndexed { i, p -> pair(path, "$uvName[$i]", p, "[u, v]").let { (u, v) -> Anchor(part, patch, u, v) } }
        face != null -> face.mapIndexed { i, p -> faceAnchor("$path.face[$i]", part, pair(path, "face[$i]", p, "[x, y]")) }
        else -> requireNotNull(given).also { anchors -> anchors.forEachIndexed { i, a -> partOf("$path.anchors[$i].part", a.part.value) } }
    }
}

/** [shape] as part data laid over [part]: its outlines, parameters, colour and regions. */
internal fun BuildContext.partData(path: String, part: PartId, shape: RecipeShape): PartData = PartData(
    outlines = curves(path, shape.outlines),
    params = shapeParams(shape.size, shape.lean, shape.outward, shape.params),
    surface = SurfaceWork(
        baseSlot = shape.color?.let { slot("$path.color", it) },
        areaFills = shape.areas.mapValues { (area, slot) -> slot("$path.areas.$area", slot) },
        regions = shape.regions.mapIndexed { i, r -> region("$path.regions[$i]", part, r) },
    ),
)
