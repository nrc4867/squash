package dev.chieppa.squash.recipe.export

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.model.SIZE_PARAM
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
import dev.chieppa.squash.recipe.model.RecipeSocket
import dev.chieppa.squash.recipe.pen.PenCurves
import dev.chieppa.squash.recipe.pen.PenPoint

/** The keys a single hand-made socket's parameters use, which a recipe socket stands for. */
private val SOCKET_FIELDS = setOf("u", "v", "outward", "pair", "sink")

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

/** A part's parameters split into the recipe's size, lean and outward, its hand-made sockets, and the rest by name. */
internal class SplitParams(params: Map<String, Float>) {
    val size: Float? = params[SIZE_PARAM]
    val lean: Float? = params[PlacementParams.LEAN]
    val outward: Float? = params[PlacementParams.OUTWARD]
    val sockets: List<RecipeSocket>
    val rest: Map<String, Float>

    init {
        val single = HandSockets.ids(params).filter { id ->
            val keys = params.keys.filter { it.startsWith(HandSockets.name(id) + ".") }.map { it.removePrefix(HandSockets.name(id) + ".") }
            keys.all { it in SOCKET_FIELDS } && "u" in keys && "v" in keys
        }
        sockets = single.map { id ->
            RecipeSocket(
                id, params.getValue(HandSockets.u(id)), params.getValue(HandSockets.v(id)), params[HandSockets.outward(id)] ?: 0f,
                (params[HandSockets.pair(id)] ?: 0f) != 0f, params[HandSockets.sink(id)],
            )
        }
        val lifted = single.flatMap { id -> SOCKET_FIELDS.map { "${HandSockets.name(id)}.$it" } }.toSet() + setOf(SIZE_PARAM, PlacementParams.LEAN, PlacementParams.OUTWARD)
        rest = params.filterKeys { it !in lifted }
    }
}

/** [anchors] in the shortest recipe form: u, v on [part]'s one patch when they all lie there, else the anchors themselves. */
private fun uvForm(part: PartId, anchors: List<Anchor>): Pair<String, List<List<Float>>>? {
    val patch = anchors.firstOrNull()?.patch ?: BODY_PATCH
    if (anchors.any { it.part != part || it.patch != patch || it.offset != 0f }) return null
    return patch to anchors.map { listOf(it.u, it.v) }
}

internal fun region(part: PartId, region: Region): RecipeRegion {
    val uv = uvForm(part, region.border)
    return RecipeRegion(region.id.value, region.slot.value, region.ink, uv?.first ?: BODY_PATCH, border = uv?.second, anchors = if (uv == null) region.border else null)
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
    val split = SplitParams(data.params)
    val surface = data.surface
    if (surface.marks.isNotEmpty() || surface.groups.isNotEmpty() || surface.lineWeights.isNotEmpty() || surface.fur != null) notCarried += "$where: marks, groups, fur or line weights on a garment's change"
    surfaceLeftOut(part, surface, notCarried)
    return RecipeShape(
        split.size, split.lean, split.outward, outlines(data.outlines), split.rest + split.sockets.flatMap { socketParams(it) },
        surface.baseSlot?.value, surface.areaFills.mapValues { it.value.value }, surface.regions.map { region(part, it) },
    )
}

/** A socket's parameters by name, for a shape that holds parameters alone. */
private fun socketParams(socket: RecipeSocket): List<Pair<String, Float>> = listOfNotNull(
    HandSockets.u(socket.n) to socket.u, HandSockets.v(socket.n) to socket.v, HandSockets.outward(socket.n) to socket.outward,
    if (socket.mirrored) HandSockets.pair(socket.n) to 1f else null, socket.sink?.let { HandSockets.sink(socket.n) to it },
)
