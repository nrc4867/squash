package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.gpu.CameraView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val RING_SEGMENTS = 64
private const val RING_MARGIN = 1.1f

/** The ground's half size in a pane that looks along it, as multiples of how far the character reaches across and up the pane. */
private const val GROUND_ACROSS = 0.56f
private const val GROUND_TALL = 0.05f

/** The ground's radius in Full, as a multiple of the character's radius. */
private const val FULL_GROUND_RADIUS = 0.85f

/** How strongly a part nothing has been placed on shows in Full. */
private const val PLANNED_OPACITY = 0.4f

/** What one frame of the panes shows; [sheetFrame] turns it into draws. */
class SheetInput(
    val rects: List<PixelRect>,
    val width: Int,
    val height: Int,
    val look: SheetLook,
    val selection: SheetSelection? = null,
    val cameras: Map<View, LockedCamera> = emptyMap(),
    val full: CameraView? = null,
    val active: View? = null,
    val toolOverlays: Map<View, ViewportOverlay> = emptyMap(),
    val fullOverlay: ViewportOverlay = ViewportOverlay.Empty,
    val hints: SheetHints = SheetHints(),
)

/**
 * One immutable frame of the panes for the render thread. Full always shows the whole character; a locked pane shows the
 * selection filled once its view is drawn and as the mesh's dashed outline until then, with everything else ghosted behind
 * it. A pane with no part stays empty.
 */
fun sheetFrame(input: SheetInput): SceneFrame {
    val views = SheetPane.entries.mapNotNull { sceneView(it, input) }
    val dividers = buildOverlay { sheetDividers(input.rects, input.width, input.height).forEach { rect(it.toRect(), input.look.divider) } }
    val panes = SheetPane.entries.map { paneOverlay(it, input).clippedTo(input.rects[it.ordinal].toRect()) }
    return SceneFrame(input.look.well, views, panes.fold(dividers, ViewportOverlay::plus))
}

private fun sceneView(pane: SheetPane, input: SheetInput): SceneView? {
    val selection = input.selection ?: return null
    val rect = input.rects[pane.ordinal]
    val look = input.look
    val body = pane.body
    if (body == null) {
        val camera = input.full ?: return null
        val (planned, placed) = (selection.others + selection.part).partition { it.planned }
        val eye = camera.eye
        val draws = listOf(fullGround(selection, look)) +
            placed.map { look.draw(it, look.fullOutline) } +
            planned.sortedByDescending { (it.center.toGpu() - eye).length }.map { look.draw(it, look.fullOutline, PLANNED_OPACITY) }
        return SceneView(rect, camera.viewProjection(rect.width, rect.height), draws)
    }
    val view = selection.part.axes.framing.getValue(body)
    val camera = input.cameras[view] ?: return null
    return SceneView(rect, camera.viewProjection(), lockedDraws(view, body, camera.basis, selection, look, input.hints))
}

/** What a locked camera on [view] draws, from [basis] in the pane of [body]: the ground, the character ghosted and the selection once [view] is drawn. */
internal fun lockedDraws(view: View, body: BodyView, basis: ViewBasis, selection: SheetSelection, look: SheetLook, hints: SheetHints): List<MeshDraw> = buildList {
    lockedGround(body, basis, selection, look)?.let(::add)
    addAll(ghosts(selection, basis, look, hints))
    if (view in selection.outlines) add(look.draw(selection.part, look.lockedOutline, layer = DepthLayer.FRONT))
}

/** Everything but the selection, furthest from the viewer first so a nearer ghost blends over a further one. */
private fun ghosts(selection: SheetSelection, basis: ViewBasis, look: SheetLook, hints: SheetHints): List<MeshDraw> {
    val strength = hints.ghost
    if (strength <= 0f) return emptyList()
    return selection.others
        .sortedBy { it.center dot basis.back }
        .map { look.draw(it, look.ghostOutline, strength) }
}

/**
 * The ground as an ellipse behind the character, in a pane that looks along it: the disc under the character as [basis] sees
 * it from its tilt off the ground, and no flatter than a thin ellipse. A pane looking along the body's up has none.
 */
private fun lockedGround(body: BodyView, basis: ViewBasis, selection: SheetSelection, look: SheetLook): MeshDraw? {
    if (body == BodyView.TOP) return null
    val box = selection.box
    val across = box.spanAlong(basis.screenRight) * GROUND_ACROSS
    val deep = box.spanAlong(basis.screenRight cross Vec3.Y) * GROUND_ACROSS * abs(basis.back.y)
    val tall = max(box.spanAlong(basis.screenUp) * GROUND_TALL, deep)
    val middle = Vec3(box.center.x, selection.floor, box.center.z)
    val model = placedDisc(middle, basis.screenRight * across, basis.screenUp * tall, basis.back)
    return MeshDraw(RenderMesh.UnitDisc, look.groundMaterial, model, layer = DepthLayer.BACK)
}

internal fun fullGround(selection: SheetSelection, look: SheetLook): MeshDraw {
    val radius = selection.box.radius * FULL_GROUND_RADIUS
    val middle = Vec3(selection.box.center.x, selection.floor, selection.box.center.z)
    return MeshDraw(RenderMesh.UnitDisc, look.groundMaterial, placedDisc(middle, Vec3.X * radius, Vec3(0f, 0f, -radius), Vec3.Y), layer = DepthLayer.BACK)
}

private fun paneOverlay(pane: SheetPane, input: SheetInput): ViewportOverlay {
    val rect = input.rects[pane.ordinal]
    val selection = input.selection ?: return ViewportOverlay.Empty
    val body = pane.body
    val inPane = if (body == null) {
        fullRing(selection.part, input, rect) + input.fullOverlay
    } else {
        val view = selection.part.axes.framing.getValue(body)
        val camera = input.cameras[view] ?: return ViewportOverlay.Empty
        lockedOverlay(view, camera, selection, input.look, input.hints, input.active) + (input.toolOverlays[view] ?: ViewportOverlay.Empty)
    }
    return inPane.translated(Offset(rect.x.toFloat(), rect.y.toFloat()))
}

/** What a locked camera on [view] draws over the scene: the template's outline until [view] is drawn, Side's guides, and the accent frame when [view] is [active]. */
internal fun lockedOverlay(view: View, camera: LockedCamera, selection: SheetSelection, look: SheetLook, hints: SheetHints, active: View?): ViewportOverlay = buildOverlay {
    if (hints.template && view !in selection.outlines) {
        selection.part.silhouette(view).forEach { path ->
            dashed(path.map(camera::toPixel), look.template, look.templateWidth, look.templateDash, look.templateGap)
        }
    }
    guideLines(paneGuides(camera, sideExtents(selection.outlines[View.SIDE], selection.part.extents, selection.drawingScale, selection.part.lengthFactor)), camera, look)
    if (view == active) {
        val inset = look.activeWidth / 2f
        outline(Rect(inset, inset, camera.width - inset, camera.height - inset), look.active, look.activeWidth)
    }
}

private fun OverlayBuilder.guideLines(guides: PaneGuides, camera: LockedCamera, look: SheetLook) {
    guides.lines.forEach { dashed(listOf(it.start, it.end), look.guide, look.guideWidth, look.guideDash, look.guideDash) }
}

/** The dashed ring around the selected part in Full, in pane pixels. */
private fun fullRing(part: SheetPart, input: SheetInput, rect: PixelRect): ViewportOverlay {
    val camera = input.full ?: return ViewportOverlay.Empty
    val look = input.look
    val target = part.center.toGpu()
    val center = camera.project(target, rect.width, rect.height) ?: return ViewportOverlay.Empty
    val across = (camera.up cross (camera.eye - target)).normalized()
    val edge = camera.project(target + across * part.radius, rect.width, rect.height) ?: return ViewportOverlay.Empty
    val radius = (edge - center).getDistance() * RING_MARGIN
    return buildOverlay {
        val path = (0..RING_SEGMENTS).map {
            val angle = 2.0 * PI * it / RING_SEGMENTS
            center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
        }
        dashed(path, look.ring, look.ringWidth, look.ringDash, look.ringGap)
    }
}

internal fun PixelRect.toRect() = Rect(x.toFloat(), y.toFloat(), right.toFloat(), bottom.toFloat())
