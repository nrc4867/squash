package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.ResolvedMark
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

/** How many straight edges a filled mark's curve is flattened to between two of its places before it is clipped. */
private const val STEPS_PER_GAP = 8

/** How far from the iris's centre a camera-locked highlight may sit, as a share of the iris's reach. */
private const val HIGHLIGHT_REACH = 0.42f

private const val MIN_LEAN = 1e-4f

/** What an eye adds to a view: fills over its part in draw order, and the lines over everything. */
class EyeDraws(val layers: List<ColorLayerDraw>, val ribbons: List<RibbonDraw>)

/**
 * [group] as [bin] draws it on [surface]: each filled role cut to the mesh, clipped by the role its clips name through
 * [clipClosed] in the patch's u and v, each inked role a ribbon. A camera-locked mark keeps its drawn shape and moves to where
 * the half vector between [toLight] and [toCamera] puts it in the iris. Marks are graphic, so their fills take no shadow tone.
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
): EyeDraws {
    fun of(role: MarkRole) = group.roles.firstOrNull { it.role == role }?.marks.orEmpty().mapNotNull(marks::get)
    fun loop(mark: ResolvedMark) = closedThrough(mark.pointsIn(bin).map { Vec2(it.anchor.u, it.anchor.v) })
    val iris = of(MarkRole.IRIS).firstOrNull()
    val layers = ArrayList<ColorLayerDraw>()
    val ribbons = ArrayList<RibbonDraw>()
    for (role in group.roles) {
        for (mark in of(role.role)) {
            val first = mark.pointsIn(bin).first().anchor
            val patch = surface.patches.firstOrNull { it.name == first.patch } ?: continue
            val grid = surface.grid(first.patch) ?: continue
            palette.colorOf(role.fill)?.let { color ->
                var shape = loop(mark)
                if (mark.cameraLocked && iris != null) shape = shape.movedTo(lockedCenter(loop(iris), patch, surface, toLight, toCamera))
                val clippers = group.clips.filter { it.clipped == role.role }.flatMap { of(it.by) }.map(::loop)
                val clipped = clippers.fold(listOf(shape)) { held, by -> held.flatMap { clipClosed(it, by) } }
                for (piece in clipped) {
                    val cut = cutShape(ColorShape.Border(piece.map { SurfaceUv(it.x, it.y) }), grid, tolerance)
                    if (!cut.isEmpty) layers += ColorLayerDraw(cut.mesh, Tones(color, color))
                }
            }
            if (role.ink && mark.ink) mark.ribbonIn(bin, rule, ink)?.let { ribbons += it }
        }
    }
    return EyeDraws(layers, ribbons)
}

/** A closed curve through [places], as a chain of short straight edges. A last place on the first is dropped. */
private fun closedThrough(places: List<Vec2>): List<Vec2> {
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

private fun List<Vec2>.center(): Vec2 = Vec2(sumOf { it.x.toDouble() }.toFloat() / size, sumOf { it.y.toDouble() }.toFloat() / size)

private fun List<Vec2>.movedTo(place: Vec2): List<Vec2> {
    val by = place - center()
    return map { it + by }
}

/** Where a highlight sits in the iris [iris]: off its centre the way the half vector between light and camera leans in the surface. */
private fun lockedCenter(iris: List<Vec2>, patch: Patch, surface: PartSurface, toLight: Vec3, toCamera: Vec3): Vec2 {
    val center = iris.center()
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
