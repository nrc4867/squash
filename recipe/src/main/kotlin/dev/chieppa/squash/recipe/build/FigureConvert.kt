package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.surface.RunSolids
import dev.chieppa.squash.character.surface.fieldOf
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.recipe.CompactJson
import dev.chieppa.squash.recipe.RecipeException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dev.chieppa.squash.recipe.figure.FieldSilhouette
import dev.chieppa.squash.recipe.figure.FigureView
import dev.chieppa.squash.recipe.figure.P2
import dev.chieppa.squash.recipe.figure.cornersOf
import dev.chieppa.squash.recipe.figure.Silhouette
import dev.chieppa.squash.recipe.figure.fewestStrokePlaces
import dev.chieppa.squash.recipe.figure.traceBoundary
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipeFigure
import dev.chieppa.squash.recipe.model.RecipeFigureStroke
import dev.chieppa.squash.recipe.model.RecipePart
import dev.chieppa.squash.recipe.model.RecipeTube
import kotlin.math.abs

/** Grid steps across the figure's height its silhouette is traced on. */
private const val TRACE_ROWS = 360

/** How much past the body's reach the traced box runs, in body units. */
private const val TRACE_MARGIN = 0.02

/** A stroke shorter than this share of the figure's height is left out: a sliver between a hand and a hip. */
private const val SHORTEST_SHARE = 0.03

/** How far out along the boundary's normal a point is tested for lying under a part the figure leaves out, in body units. */
private const val UNDER_REACH = 0.002

/** A limb resting out from the body by at least this many degrees is left out of the side view, where it hangs over the trunk. */
private const val SPREAD_LIMB = 20f

/** How near the axis, in body units, an outline's first place lies for a tube drawn round its socket end. */
private const val ON_AXIS = 1e-4f

/** Rounds a traced value to this many places. */
private const val PLACES = 10_000.0

/** A recipe turned to a figure: its outline as traced and the recipe with its body's tubes taking their widths from it. */
class FigureConversion(val figure: RecipeFigure, val recipe: Recipe)

/**
 * [recipe] converted to a figure: the tube parts of its body (each a `tube` with drawn Side and Top outlines, sitting on the root or on
 * another such part) are built, their joined field's silhouette at rest traced from the front (the left half) and from the left side,
 * each stroke cut where a part sitting on one of them hides it (the head, the hands, the feet), and the tubes' outlines dropped for the figure's fit, with the sockets they sit
 * on placed by root: at the height along their parent where the build put each root, a part on the middle on its axis and a limb's
 * reach and v left for the fit to place.
 */
fun convertToFigure(recipe: Recipe, templates: TemplateFolder, library: TemplateLibrary = templates.library()): FigureConversion {
    val tubes = bodyTubes(recipe)
    if (tubes.isEmpty()) throw RecipeException("$.parts", "no tube part of the body with Side and Top outlines to convert")
    val character = RecipeBuilder(templates).build(recipe.copy(figure = null)).document.character
    val ids = tubes.flatMap { sideIds(it) }.toSet()
    val plan = library.plan(character.bodyPlan)
    val body = assembleNow(assemblyParts(character).filter { it.owner == null }, { character.resolved(it) ?: PartData() }, plan, library)
    val rootHeight = -body.floor.toDouble()
    val spread = tubes.filter { it.pair != null && (it.outward ?: 0f) >= SPREAD_LIMB }.flatMap { sideIds(it) }.toSet()
    val strokes = FigureView.entries.associateWith { view ->
        val shown = if (view == FigureView.SIDE) ids - spread else ids
        val ends = body.parts.filter { it.id?.value !in ids && it.socket?.parent?.value in ids }.mapNotNull { it.id?.value }
        val hidden = if (view == FigureView.SIDE) ends.filter { !underSpread(character, it, spread) }.toSet() else ends.toSet()
        traced(view, body, shown, hidden, rootHeight)
    }
    val figure = RecipeFigure(rootHeight = round(rootHeight), front = strokes.getValue(FigureView.FRONT), side = strokes.getValue(FigureView.SIDE), pose = emptyList())
    val tubeIds = tubes.map { it.id }.toSet()
    val converted = recipe.copy(
        figure = figure,
        parts = recipe.parts.map { part ->
            val shaped = if (part.id in tubeIds) asFigurePart(part) else part
            val children = recipe.parts.filter { it.on == part.id && it.id in tubeIds }
            if (part.id !in tubeIds || children.isEmpty()) return@map shaped
            shaped.copy(
                sockets = shaped.sockets.map { socket ->
                    val child = children.firstOrNull { it.socket == "socket.${socket.n}" } ?: return@map socket
                    val u = rootU(body, sideIds(part).first(), sideIds(child).first(), requireNotNull(shaped.tube).length)
                    socket.copy(u = u ?: socket.u, sink = null, reach = if (child.pair == null) 0f else null)
                },
            )
        },
    )
    return FigureConversion(figure, converted)
}

/**
 * How far along [parent]'s axis, as a share of its [length], the root of [child] sits as [body] places it: the u a socket by reach puts
 * it at the same height, wherever its sink put it.
 */
private fun rootU(body: AssembledCharacter, parent: String, child: String, length: Float): Float? {
    val under = body.parts.firstOrNull { it.id?.value == parent } ?: return null
    val root = body.parts.firstOrNull { it.id?.value == child }?.origin ?: return null
    val rest = PartBuilder.axes(under.template).along
    val along = (if (under.mirrored) rest.mirrored else rest).normalized()
    return round((((root - under.origin) dot along) / length).toDouble())
}

/** The tubes of [recipe]'s body: generic tubes with drawn Side and Top outlines or tube profiles, each on the root part or on another of them. */
private fun bodyTubes(recipe: Recipe): List<RecipePart> {
    val out = mutableListOf<RecipePart>()
    for (part in recipe.parts) {
        val shaped = (View.SIDE in part.outlines && View.TOP in part.outlines) || part.tube?.profiles?.isNotEmpty() == true
        val drawn = part.template == "tube" && part.garment == null && part.rest != null && shaped
        if (drawn && (part.on == null || out.any { it.id == part.on })) out += part
    }
    return out
}

private fun sideIds(part: RecipePart): List<String> = part.pair?.let { listOf(it.left ?: "${part.id}-l", it.right ?: "${part.id}-r") } ?: listOf(part.id)

/** Whether part [id] of [character] sits, through its sockets, on one of [spread]: a hand on a spread arm. */
private fun underSpread(character: Character, id: String, spread: Set<String>): Boolean {
    var at = character.parts.firstOrNull { it.id.value == id }
    while (at != null) {
        val parent = at.socket?.parent ?: return false
        if (parent.value in spread) return true
        at = character.part(parent)
    }
    return false
}

/** [part] with its drawn outlines or profiles given up for a tube the figure fits, as long as it ran and closed as it did. */
private fun asFigurePart(part: RecipePart): RecipePart {
    part.tube?.let { tube -> return part.copy(tube = RecipeTube(length = tube.length, closed = tube.closed, flat = tube.flat)) }
    val side = part.outlines.getValue(View.SIDE)
    val xs = (side.through ?: side.bezier).orEmpty().map { it[0] }
    val closed = side.closed || (xs.min() <= 0f && side.through?.firstOrNull()?.let { abs(it[1]) < ON_AXIS } == true)
    return part.copy(outlines = part.outlines - View.SIDE - View.TOP, tube = RecipeTube(length = xs.max(), closed = closed))
}

/** The members of [assembled]'s field whose parts are among [ids], joined as they are among themselves; null with none. */
private fun fieldOfParts(assembled: AssembledCharacter, ids: Set<String>): JoinedField? {
    val field = fieldOf(AssembledCharacter(assembled.parts.filter { it.id?.value in ids }, emptyList()), RunSolids()) ?: return null
    return field.field
}

/** Each of [assembled]'s parts among [ids] on its own, joined to nothing. */
private fun apartField(assembled: AssembledCharacter, ids: Set<String>): JoinedField? {
    val field = fieldOf(assembled, RunSolids()) ?: return null
    val kept = field.runs.indices.filter { field.runs[it].part.id?.value in ids }
    if (kept.isEmpty()) return null
    return JoinedField(kept.map { i -> field.field.members[i].let { JoinedMember(it.solid, it.origin, it.mirrored, 0f, -1, it.part, it.ease) } })
}

/**
 * The strokes of [view] traced off the field of [shown]: the silhouette's boundary, the front's cut to its left half where it crosses the
 * middle, then cut again wherever one of [hidden] covers it, each run kept as a few places with its corners.
 */
private fun traced(view: FigureView, assembled: AssembledCharacter, shown: Set<String>, hidden: Set<String>, rootHeight: Double): List<RecipeFigureStroke> {
    val body = FieldSilhouette(requireNotNull(fieldOfParts(assembled, shown)) { "figure: the body built no field" }, rootHeight)
    val cover = apartField(assembled, hidden)?.let { FieldSilhouette(it, rootHeight) }
    val box = requireNotNull(assembled.box)
    val front = view == FigureView.FRONT
    val reach = (if (front) maxOf(-box.low.z, box.high.z) else maxOf(-box.low.x, box.high.x)).toDouble()
    return tracedStrokes(view, body, reach, box.high.y + rootHeight, cover)
}

/**
 * The strokes of [view] traced off [body]'s silhouette within [reach] of the middle and up to [height]: its boundary, the front's cut to
 * its left half where it crosses the middle, then cut again wherever [cover] covers it, each run kept as a few places with its corners.
 */
internal fun tracedStrokes(view: FigureView, body: Silhouette, reach: Double, height: Double, cover: Silhouette? = null): List<RecipeFigureStroke> {
    val top = height + TRACE_MARGIN
    val step = top / TRACE_ROWS
    val front = view == FigureView.FRONT
    val reach = reach + TRACE_MARGIN
    val inside: (P2) -> Boolean = { q -> body.inside(view, if (front) P2(abs(q.x), q.y) else q) }
    val loops = traceBoundary(P2(-reach, -TRACE_MARGIN), P2(reach, top), step, inside)
    val runs = loops.flatMap { loop -> if (front) leftHalf(loop) else listOf(loop) }
        .flatMap { run -> uncovered(run, view, cover) }
        .filter { run -> run.zipWithNext().sumOf { (a, b) -> (b - a).length } >= SHORTEST_SHARE * top }
    return runs.mapIndexed { i, run ->
        val (places, corners) = fewestStrokePlaces(run, cornersOf(run), top)
        val onAxis = front && (places.first().x == 0.0 || places.last().x == 0.0)
        RecipeFigureStroke(
            id = "${view.name.lowercase()}-${i + 1}",
            through = places.map { listOf(round(it.x), round(it.y)) },
            corners = corners,
            mirrored = onAxis,
        )
    }
}

/** The stretches of a closed [loop] on the left half (x 0 or more), each cut exactly at the middle where it crosses. */
private fun leftHalf(loop: List<P2>): List<List<P2>> {
    val start = loop.indexOfFirst { it.x < 0 }.takeIf { it >= 0 } ?: return listOf(loop + loop.first())
    val ring = loop.drop(start) + loop.take(start) + loop[start]
    val out = mutableListOf<List<P2>>()
    var run: MutableList<P2>? = null
    for ((a, b) in ring.zipWithNext()) {
        if (a.x < 0 && b.x >= 0) run = mutableListOf(axisCrossing(a, b))
        if (b.x >= 0) run?.add(b)
        if (a.x >= 0 && b.x < 0) {
            run?.let { it += axisCrossing(a, b); out += it }
            run = null
        }
    }
    return out
}

private fun axisCrossing(a: P2, b: P2): P2 = P2(0.0, a.y + (b.y - a.y) * (-a.x) / (b.x - a.x))

/** [run] cut wherever [cover] covers it, a little out along its normal, into the stretches it leaves showing. */
private fun uncovered(run: List<P2>, view: FigureView, cover: Silhouette?): List<List<P2>> {
    if (cover == null) return listOf(run)
    val out = mutableListOf<List<P2>>()
    var current = mutableListOf<P2>()
    run.forEachIndexed { i, p ->
        val d = (run[minOf(i + 1, run.lastIndex)] - run[maxOf(i - 1, 0)]).unit()
        val outward = P2(-d.y, d.x)
        val probe = p + outward * UNDER_REACH
        val q = if (view == FigureView.FRONT) P2(abs(probe.x), probe.y) else probe
        if (cover.inside(view, q)) {
            if (current.size >= 2) out += current
            current = mutableListOf()
        } else {
            current += p
        }
    }
    if (current.size >= 2) out += current
    return out
}

private fun round(value: Double): Float = (Math.round(value * PLACES) / PLACES).toFloat()

/** The outline file format the kit's `readFigureOutline` reads. */
const val FIGURE_OUTLINE_FORMAT = "squash-figure-outline/1"

/** [figure]'s strokes as a figure outline file: its front and side strokes, each its id, places, corners and whether it is mirrored. */
fun figureOutlineJson(figure: RecipeFigure): String {
    fun strokes(list: List<RecipeFigureStroke>) = JsonArray(
        list.map { s ->
            JsonObject(
                buildMap {
                    put("id", JsonPrimitive(s.id))
                    put("points", JsonArray(s.through.orEmpty().map { p -> JsonArray(p.map(::JsonPrimitive)) }))
                    if (s.corners.isNotEmpty()) put("corners", JsonArray(s.corners.map(::JsonPrimitive)))
                    if (s.mirrored) put("mirrored", JsonPrimitive(true))
                },
            )
        },
    )
    val outline = JsonObject(mapOf("format" to JsonPrimitive(FIGURE_OUTLINE_FORMAT), "front" to strokes(figure.front), "side" to strokes(figure.side)))
    return CompactJson.print(outline) + "\n"
}
