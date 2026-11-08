package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.clipClosed
import dev.chieppa.squash.geom.cutShape
import dev.chieppa.squash.geom.surfaceAt
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.Tones
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.sheet.markProfile

/** How many straight edges a filled mark's curve is flattened to between two of its places before it is clipped. */
private const val STEPS_PER_GAP = 8

/** How far from the iris's centre a camera-locked highlight may sit, as a share of the iris's reach. */
private const val HIGHLIGHT_REACH = 0.42f

/** Below this a lid has shut, with no opening left to fill. */
private const val LEAST_OPEN = 0.02f

private const val MIN_LEAN = 1e-4f

/** What an eye adds to a view: fills over its part in draw order, and the lines over everything. */
class EyeDraws(val layers: List<ColorLayerDraw>, val ribbons: List<RibbonDraw>)

/** What an eye shows beyond its controls: the shown expression's marks by role, where its gaze has put the pupil, and the size that gaze draws it at. */
class EyeShow(val expressed: Map<MarkRole, List<ResolvedMark>>? = null, val gaze: Vec2 = Vec2(0f, 0f), val pupilScale: Float = 1f)

/** Where the place at [u] and [v] of [patch] on [surface] lands in body space, the surface sitting at [origin], as an anchor like [like]. */
internal fun resolvedOn(surface: PartSurface, origin: Vec3, like: ResolvedPoint, patch: Patch, u: Float, v: Float): ResolvedPoint {
    val at = patch.surfaceAt(u, v)
    return ResolvedPoint(like.anchor.copy(u = u, v = v), origin + surface.shown(at.position), surface.shown(at.normal))
}

/**
 * [group] as [bin] draws it on [surface]: each filled role cut to the mesh, clipped by the role its clips name through [clipClosed] in the patch's u and v, each inked role a
 * ribbon. Its [dev.chieppa.squash.document.MarkControls] apply: the lid opens, the iris and pupil move by the gaze inside it, the pupil dilates. A camera-locked mark keeps its
 * drawn shape and moves to where the half vector between [toLight] and [toCamera] puts it in the iris. Marks are graphic, so their fills take no shadow tone.
 */
fun eyeDraws(
    group: MarkGroup,
    marks: Map<MarkId, ResolvedMark>,
    surface: PartSurface,
    bin: CameraBin,
    palette: Palette,
    toLight: Vec3,
    toCamera: Vec3,
    tolerance: Float,
    rule: LineRule,
    ink: Color,
    show: EyeShow = EyeShow(),
    origin: Vec3 = Vec3.ZERO,
): EyeDraws {
    val controls = group.controls
    fun roleOf(role: MarkRole) = roleShapes(group, role, marks, show.expressed, bin)
    val opened = roleOf(MarkRole.LID).map { it.withPlaces(openedLid(it.places, controls.open)) }
    val lidLoop = opened.firstOrNull()?.places
    val irisFrom = (roleOf(MarkRole.IRIS) + roleOf(MarkRole.PUPIL)).firstOrNull()?.places?.middle()
    val total = Vec2(controls.gazeU, controls.gazeV) + show.gaze
    val look = if (lidLoop != null && irisFrom != null) heldInLid(irisFrom, total, lidLoop) else total
    fun shown(role: MarkRole): List<ShownShape> = when (role) {
        MarkRole.LID -> opened
        MarkRole.IRIS -> roleOf(role).map { it.withPlaces(it.places.movedBy(look)) }
        MarkRole.PUPIL -> roleOf(role).map { it.withPlaces(it.places.movedBy(look).scaledBy((1f + controls.dilation) * show.pupilScale)) }
        MarkRole.HIGHLIGHT -> roleOf(role).map { it.withPlaces(it.places.movedBy(look)) }
        else -> roleOf(role)
    }
    val irisLoop = shown(MarkRole.IRIS).firstOrNull()?.let { closedThrough(it.places) }
    val layers = ArrayList<ColorLayerDraw>()
    val ribbons = ArrayList<RibbonDraw>()
    val shut = controls.open < LEAST_OPEN
    for (role in group.roles) {
        for (shape in shown(role.role)) {
            val patch = surface.patches.firstOrNull { it.name == shape.patch } ?: continue
            val grid = surface.grid(shape.patch) ?: continue
            val color = palette.colorOf(role.fill)
            if (color != null && !(shut && role.role != MarkRole.BROW && role.role != MarkRole.LASHES)) {
                var loop = closedThrough(shape.places)
                if (shape.source.cameraLocked && irisLoop != null) loop = loop.movedTo(lockedCenter(irisLoop, patch, surface, toLight, toCamera))
                val clippers = group.clips.filter { it.clipped == role.role }.flatMap { shown(it.by) }.map { closedThrough(it.places) } +
                    (if (role.role == MarkRole.HIGHLIGHT && lidLoop != null) listOf(closedThrough(lidLoop)) else emptyList())
                val clipped = clippers.fold(listOf(loop)) { held, by -> held.flatMap { clipClosed(it, by) } }
                for (piece in clipped) {
                    val cut = cutShape(ColorShape.Border(piece.map { SurfaceUv(it.x, it.y) }), grid, tolerance)
                    if (!cut.isEmpty) layers += ColorLayerDraw(cut.mesh, Tones(color, color))
                }
            }
            if (role.ink && shape.source.ink) {
                val like = shape.source.pointsIn(bin).first()
                ribbonThrough(shape.places.map { resolvedOn(surface, origin, like, patch, it.x, it.y) }, markProfile(shape.width), rule, ink)?.let { ribbons += it }
            }
        }
    }
    return EyeDraws(layers, ribbons)
}

/** A closed curve through [places], as a chain of short straight edges. A last place on the first is dropped. */
internal fun closedThrough(places: List<Vec2>): List<Vec2> {
    val held = if (places.first() == places.last()) places.dropLast(1) else places
    val n = held.size
    return (0 until n).flatMap { i ->
        val p0 = held[(i + n - 1) % n]
        val p1 = held[i]
        val p2 = held[(i + 1) % n]
        val p3 = held[(i + 2) % n]
        List(STEPS_PER_GAP) { step ->
            val t = step.toFloat() / STEPS_PER_GAP
            val tt = t * t
            (p1 * 2f + (p2 - p0) * t + (p0 * 2f - p1 * 5f + p2 * 4f - p3) * tt + (p1 * 3f - p0 - p2 * 3f + p3) * (tt * t)) * 0.5f
        }
    }
}

private fun List<Vec2>.movedTo(place: Vec2): List<Vec2> = movedBy(place - middle())

/** Where a highlight sits in the iris [iris]: off its centre the way the half vector between light and camera leans in the surface. */
private fun lockedCenter(iris: List<Vec2>, patch: Patch, surface: PartSurface, toLight: Vec3, toCamera: Vec3): Vec2 {
    val center = iris.middle()
    val at = patch.surfaceAt(center.x, center.y)
    val half = (toLight.normalized() + toCamera.normalized()).normalized()
    val normal = surface.shown(at.normal)
    val lean = half - normal * (half dot normal)
    if (lean.length < MIN_LEAN) return center
    val toward = lean.normalized()
    val reachU = iris.maxOf { it.x } - center.x
    val reachV = iris.maxOf { it.y } - center.y
    return Vec2(
        center.x + (toward dot surface.shown(at.alongU).normalized()) * reachU * HIGHLIGHT_REACH,
        center.y + (toward dot surface.shown(at.aroundV).normalized()) * reachV * HIGHLIGHT_REACH,
    )
}
