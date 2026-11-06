package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.clipClosed
import dev.chieppa.squash.geom.cutShape
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.Tones
import dev.chieppa.squash.screens.sheet.colorOf

/** Below this a mouth has not parted. */
private const val LEAST_OPEN = 0.01f

/** How many gaps a mouth's inner shape runs along each lip, between the lip's own places. */
private const val LIP_STEPS = 6

/** What a mouth adds to a view: fills over its part in draw order, and its lip lines as marks, each with its [ResolvedMark.points] where the view shows them. */
class MouthDraws(val layers: List<ColorLayerDraw>, val lips: List<ResolvedMark>)

/**
 * [group] as [bin] draws it on [surface], which sits at [origin]: each lip line shaped by its controls and, once open, a lower lip parted from it with the inner role's color
 * between them and the teeth and tongue inside that. Cut to the mesh in the patch's u and v.
 */
fun mouthDraws(
    group: MarkGroup,
    marks: Map<MarkId, ResolvedMark>,
    expressed: Map<MarkRole, List<ResolvedMark>>?,
    surface: PartSurface,
    bin: CameraBin,
    palette: Palette,
    tolerance: Float,
    origin: Vec3 = Vec3.ZERO,
): MouthDraws {
    val controls = group.controls
    val open = controls.open >= LEAST_OPEN
    val layers = ArrayList<ColorLayerDraw>()
    val lips = ArrayList<ResolvedMark>()
    val inners = ArrayList<List<Vec2>>()
    val innerFill = palette.colorOf(group.roles.firstOrNull { it.role == MarkRole.INNER }?.fill)
    for (shape in roleShapes(group, MarkRole.LIP_LINE, marks, expressed, bin)) {
        val patch = surface.patches.firstOrNull { it.name == shape.patch } ?: continue
        val grid = surface.grid(shape.patch) ?: continue
        val like = shape.source.pointsIn(bin).first()
        fun marked(places: List<Vec2>, id: String) = shape.source.copy(
            id = MarkId(id),
            points = places.map { resolvedOn(surface, origin, like, patch, it.x, it.y) },
            versions = emptyMap(),
            width = shape.width,
        )
        val upper = shapedLip(shape.places, controls)
        lips += marked(upper, shape.source.id.value)
        if (!open) continue
        val lower = lowerLip(upper, controls.open)
        lips += marked(lower, "${shape.source.id.value}~lower")
        val inner = closedLoop(upper, lower)
        inners += inner
        if (innerFill != null) {
            val cut = cutShape(ColorShape.Border(inner.map { SurfaceUv(it.x, it.y) }), grid, tolerance)
            if (!cut.isEmpty) layers += ColorLayerDraw(cut.mesh, Tones(innerFill, innerFill))
        }
    }
    if (open) {
        for (role in listOf(MarkRole.TONGUE, MarkRole.TEETH)) {
            val color = palette.colorOf(group.roles.firstOrNull { it.role == role }?.fill) ?: continue
            for (shape in roleShapes(group, role, marks, expressed, bin)) {
                val grid = surface.grid(shape.patch) ?: continue
                val loop = closedThrough(shape.places)
                for (piece in inners.flatMap { clipClosed(loop, it) }) {
                    val cut = cutShape(ColorShape.Border(piece.map { SurfaceUv(it.x, it.y) }), grid, tolerance)
                    if (!cut.isEmpty) layers += ColorLayerDraw(cut.mesh, Tones(color, color))
                }
            }
        }
    }
    return MouthDraws(layers, lips)
}

/** The closed shape between [upper] and [lower], run along one and back along the other, in short straight edges. */
private fun closedLoop(upper: List<Vec2>, lower: List<Vec2>): List<Vec2> {
    fun fine(places: List<Vec2>) = places.zipWithNext().flatMap { (a, b) -> List(LIP_STEPS) { a + (b - a) * (it.toFloat() / LIP_STEPS) } } + places.last()
    return fine(upper) + fine(lower).reversed().drop(1).dropLast(1)
}
