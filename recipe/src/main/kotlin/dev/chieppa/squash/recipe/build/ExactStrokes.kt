package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.shape.BodyChart
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.character.surface.drawnStrokeCells
import dev.chieppa.squash.document.ClothOutline
import dev.chieppa.squash.document.ClothPoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.mirrored
import dev.chieppa.squash.recipe.model.RecipeStroke
import dev.chieppa.squash.recipe.model.RecipeStrokeShape

/** One exact stroke's shape on its part's chart, [part] a pair's leader, and the cells it lays there. */
internal class ExactStroke(val part: PartId, val outline: ClothOutline, val cells: SurfaceMask)

/**
 * The shapes [stroke], an exact stroke, lays on [chart], read at [path]: its band, box, ellipse or lasso in its part's u and v, and with
 * [ExactStroke.cells] the cells each lays (see [drawnStrokeCells]); mirrored, the same across the middle too.
 */
internal fun BuildContext.exactStrokes(path: String, stroke: RecipeStroke, chart: BodyChart): List<ExactStroke> {
    if (stroke.clean != 0f) fail("$path.clean", "an exact stroke keeps its own edge; clean it by its shape, not with clean")
    val part = partOf("$path.on", stroke.on)
    val chartPart = chart.part(part) ?: fail("$path.on", "${part.value} has no surface to paint on")
    val outline = when (stroke.shape) {
        RecipeStrokeShape.BAND -> {
            val (from, to) = span(path, "u", stroke.u ?: fail(path, "a band gives u: [from, to] along the part"))
            ClothOutline.Box(from, to)
        }
        RecipeStrokeShape.BOX, RecipeStrokeShape.ELLIPSE -> {
            val (from, to) = span(path, "u", stroke.u ?: fail(path, "a ${stroke.shape.name.lowercase()} gives u: [from, to] along the part"))
            val (fromV, toV) = stroke.v?.let { span(path, "v", it) } ?: (0f to 1f)
            if (stroke.shape == RecipeStrokeShape.BOX) ClothOutline.Box(from, to, fromV, toV) else ClothOutline.Ellipse(from, to, fromV, toV)
        }
        RecipeStrokeShape.LASSO -> {
            val points = stroke.points ?: fail(path, "an exact lasso gives its places as points [u, v]")
            if (points.size < ClothOutline.LEAST_PATH_POINTS) fail(path, "a lasso passes through at least ${ClothOutline.LEAST_PATH_POINTS} places, not ${points.size}")
            val places = points.mapIndexed { i, p -> if (p.size != 2) fail("$path.points[$i]", "give [u, v], not $p") else ClothPoint(p[0], p[1]) }
            stroke.corners.forEachIndexed { i, c -> if (c !in places.indices) fail("$path.corners[$i]", "corner $c is not one of the ${places.size} places") }
            ClothOutline.Path(places, stroke.curved, stroke.corners.toSet())
        }
        else -> fail("$path.exact", "a ${stroke.shape.name.lowercase()} is laid by cells; only a band, box, ellipse or lasso is exact")
    }
    val outlines = if (stroke.mirrored) listOf(outline, outline.mirrored()) else listOf(outline)
    return outlines.map { shape ->
        val laid = drawnStrokeCells(shape, stroke.unpaint)
        ExactStroke(chartPart.id, shape, SurfaceMask.build(chart) { p, cell -> p == chartPart.id && laid[cell] })
    }
}
