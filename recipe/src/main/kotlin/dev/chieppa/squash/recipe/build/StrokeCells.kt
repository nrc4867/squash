package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.dress.placeOnBody
import dev.chieppa.squash.character.shape.BodyChart
import dev.chieppa.squash.character.shape.ChartPart
import dev.chieppa.squash.character.shape.ChartPlaces
import dev.chieppa.squash.character.shape.Opening
import dev.chieppa.squash.character.shape.OpeningCut
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.character.shape.opening
import dev.chieppa.squash.character.shape.cellMiddle
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.recipe.model.RecipeOpeningCut
import dev.chieppa.squash.recipe.model.RecipeStroke
import dev.chieppa.squash.recipe.model.RecipeStrokeShape
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** How many path samples a brush takes a cell it crosses, along or round. */
private const val BRUSH_SAMPLES_PER_CELL = 4

/** How many samples a line takes a brush's reach along it. */
private const val LINE_SAMPLES_PER_SIZE = 4

/** The fewest places a lasso takes. */
private const val LASSO_PLACES = 3

/**
 * The cells of [chart] [stroke] lays, read at [path]: a band, box, ellipse or lasso holds the cells of its part whose middles lie in it in
 * u and v; a brush or line the cells of its part, and of the parts beyond it, whose middles at rest lie within its size of its path on
 * the body as [painted] places them; an opening the cells round where its part comes out ([opening]).
 */
internal fun BuildContext.strokeCells(path: String, stroke: RecipeStroke, chart: BodyChart, painted: BodyPlaces): SurfaceMask {
    val part = partOf("$path.on", stroke.on)
    val chartPart = chart.part(part) ?: fail("$path.on", "${part.value} has no surface to paint on")
    return when (stroke.shape) {
        RecipeStrokeShape.BAND -> {
            val (from, to) = span(path, "u", stroke.u ?: fail(path, "a band gives u: [from, to] along the part"))
            chartCells(chart, chartPart) { u, _ -> u in from..to }
        }
        RecipeStrokeShape.BOX, RecipeStrokeShape.ELLIPSE -> {
            val (uFrom, uTo) = span(path, "u", stroke.u ?: fail(path, "a ${stroke.shape.name.lowercase()} gives u: [from, to] along the part"))
            val (vFrom, vTo) = stroke.v?.let { span(path, "v", it) } ?: (0f to 1f)
            val whole = vTo - vFrom >= 1f
            if (stroke.shape == RecipeStrokeShape.BOX) {
                chartCells(chart, chartPart) { u, v -> u in uFrom..uTo && (whole || near(v, vFrom, vTo) in vFrom..vTo) }
            } else {
                val uMiddle = (uFrom + uTo) / 2f
                val vMiddle = (vFrom + vTo) / 2f
                val uReach = (uTo - uFrom) / 2f
                val vReach = (vTo - vFrom) / 2f
                chartCells(chart, chartPart) { u, v ->
                    val du = (u - uMiddle) / uReach
                    val dv = (near(v, vMiddle - 0.5f, vMiddle + 0.5f) - vMiddle) / vReach
                    du * du + dv * dv <= 1f
                }
            }
        }
        RecipeStrokeShape.LASSO -> {
            val places = unwrapped(uvPlaces(path, part, chartPart, stroke))
            if (places.size < LASSO_PLACES) fail(path, "a lasso passes through at least $LASSO_PLACES places, not ${places.size}")
            chartCells(chart, chartPart) { u, v -> (-1..1).any { turn -> inside(places, u, v + turn) } }
        }
        RecipeStrokeShape.OPENING -> {
            if (chartPart.parent == null) fail("$path.on", "${part.value} comes out of no part, so no opening rings it")
            if (stroke.reach < 0f) fail("$path.reach", "an opening reaches 0 or more past its ring, not ${stroke.reach}")
            if (stroke.depth < 0f) fail("$path.depth", "an opening drops 0 or more down its front, not ${stroke.depth}")
            val cut = if (stroke.cut == RecipeOpeningCut.V) OpeningCut.V else OpeningCut.ROUND
            painted.places.opening(part, Opening(cut, stroke.reach, stroke.depth))
        }
        RecipeStrokeShape.BRUSH, RecipeStrokeShape.LINE -> {
            if (stroke.size <= 0f) fail("$path.size", "a brush's size is above 0, not ${stroke.size}")
            val places = uvPlaces(path, part, chartPart, stroke)
            if (places.isEmpty()) fail(path, "a ${stroke.shape.name.lowercase()} passes through at least one place")
            val centers = if (stroke.shape == RecipeStrokeShape.BRUSH) brushPath(part, chartPart, places, painted) else linePath(path, part, chartPart, places, stroke.size, painted)
            val reached = painted.places.within(centers, stroke.size)
            val above = generateSequence(chartPart.parent) { chart.part(it)?.parent }.mapTo(HashSet()) { chart.leaderOf(it) }
            SurfaceMask.build(chart) { p, cell -> reached.held(p, cell) && chart.leaderOf(p) !in above }
        }
    }
}

/** The cells of [part] whose middles [holds], read in its u and v, the other parts holding none. */
private fun chartCells(chart: BodyChart, part: ChartPart, holds: (Float, Float) -> Boolean): SurfaceMask = SurfaceMask.build(chart) { p, cell ->
    p == part.id && cellMiddle(p, part.patch, cell).let { holds(it.u, it.v) }
}

/** [v] moved round by whole turns to lie as near as it can to [from] to [to]. */
private fun near(v: Float, from: Float, to: Float): Float {
    val middle = (from + to) / 2f
    return v + round(middle - v)
}

/** A [from, to] pair at [path].[field], from below to. */
private fun BuildContext.span(path: String, field: String, pair: List<Float>): Pair<Float, Float> {
    if (pair.size != 2) fail("$path.$field", "give [from, to], not $pair")
    if (pair[0] >= pair[1]) fail("$path.$field", "from lies below to: here ${pair[0]} to ${pair[1]}")
    return pair[0] to pair[1]
}

/** The stroke's places as (u, v) on [part]: its points, or its face places on a head. */
private fun BuildContext.uvPlaces(path: String, part: PartId, chartPart: ChartPart, stroke: RecipeStroke): List<Pair<Float, Float>> {
    val points = stroke.points
    val face = stroke.face
    if ((points == null) == (face == null)) fail(path, "give its places one way, as points or face, not ${if (points == null) "neither" else "both"}")
    if (points != null) return points.mapIndexed { i, p -> pair(path, "points[$i]", p, "[u, v]") }
    return requireNotNull(face).mapIndexed { i, p ->
        val anchor = faceAnchor("$path.face[$i]", part, pair(path, "face[$i]", p, "[x, y]"))
        if (anchor.patch != chartPart.patch) fail("$path.face[$i]", "lies on ${anchor.patch}, not the part's ${chartPart.patch}")
        anchor.u to anchor.v
    }
}

/** [places] with each v moved round by whole turns to lie within half a turn of the one before. */
private fun unwrapped(places: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    var last = places.firstOrNull()?.second ?: return places
    return places.map { (u, v) ->
        val moved = v + round(last - v)
        last = moved
        u to moved
    }
}

/** Whether (u, v) lies inside the closed polygon through [places], by crossings. */
private fun inside(places: List<Pair<Float, Float>>, u: Float, v: Float): Boolean {
    var inside = false
    var j = places.lastIndex
    for (i in places.indices) {
        val (ui, vi) = places[i]
        val (uj, vj) = places[j]
        if ((vi > v) != (vj > v) && u < (uj - ui) * (v - vi) / (vj - vi) + ui) inside = !inside
        j = i
    }
    return inside
}

/** Where a brush's path through [places], straight between them in u and v, lies on the body: a few samples a cell it crosses. */
private fun brushPath(part: PartId, chartPart: ChartPart, places: List<Pair<Float, Float>>, painted: BodyPlaces): List<Vec3> {
    val path = unwrapped(places)
    val samples = ArrayList<Pair<Float, Float>>()
    samples += path.first()
    for ((a, b) in path.zipWithNext()) {
        val cells = maxOf(abs(b.first - a.first) * painted.rows, abs(b.second - a.second) * painted.columns)
        val steps = maxOf(1, ceil(cells * BRUSH_SAMPLES_PER_CELL).toInt())
        for (k in 1..steps) {
            val t = k.toFloat() / steps
            samples += (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
        }
    }
    return samples.mapNotNull { (u, v) -> painted.at(Anchor(part, chartPart.patch, u.coerceIn(0f, 1f), v - floor(v))) }
}

/** Where a line's straight stretches in the body between [places] lie: [LINE_SAMPLES_PER_SIZE] samples a [size] along each. */
private fun BuildContext.linePath(path: String, part: PartId, chartPart: ChartPart, places: List<Pair<Float, Float>>, size: Float, painted: BodyPlaces): List<Vec3> {
    val points = places.mapIndexed { i, (u, v) ->
        painted.at(Anchor(part, chartPart.patch, u.coerceIn(0f, 1f), v - floor(v))) ?: fail("$path.points[$i]", "($u, $v) lies on no surface of ${part.value}")
    }
    if (points.size == 1) return points
    return points.zipWithNext().flatMap { (a, b) ->
        val steps = maxOf(1, ceil((b - a).length / size * LINE_SAMPLES_PER_SIZE).toInt())
        (0..steps).map { k -> a + (b - a) * (k.toFloat() / steps) }
    }
}

/** Where the cells of a body's chart lie at rest, and where any place on it does. */
internal class BodyPlaces(val places: ChartPlaces, private val body: AssembledCharacter) {
    val rows: Int get() = CHART_ROWS
    val columns: Int get() = CHART_COLUMNS

    fun at(anchor: Anchor): Vec3? = placeOnBody(body, anchor)
}
