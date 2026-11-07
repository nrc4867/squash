package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.screens.references.TraceDraws
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.RibbonDraw
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
    val layout: SheetLayout = SheetLayout.SHEET,
    val color: PaneColor? = null,
    val surface: SurfaceRibbons? = null,
    val fullFur: ShownFur = ShownFur.NONE,
    val lockedFur: Map<View, ShownFur> = emptyMap(),
    val traces: (toViewer: Vec3) -> TraceDraws = { TraceDraws.None },
    val rig: RigShading? = null,
)

/**
 * One immutable frame of the panes for the render thread. Full always shows the whole character, with what is not placed yet
 * faded except in Full first, which is there to judge the whole, and in its pose when the pose moves a bone; a locked pane shows the selection filled once its view is
 * drawn and as the mesh's dashed outline until then, with everything else ghosted behind it. With a [SheetInput.rig] every pane shows
 * the whole character as the Rig stage colors it, in its pose. A pane with no part stays empty.
 */
fun sheetFrame(input: SheetInput): SceneFrame {
    val views = SheetPane.entries.mapNotNull { sceneView(it, input) }
    val dividers = buildOverlay { paneDividers(input.layout, input.rects, input.width, input.height).forEach { rect(it.toRect(), input.look.divider) } }
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
        val plannedOpacity = if (input.layout == SheetLayout.FULL_FIRST) 1f else PLANNED_OPACITY
        val toCamera = Vec3(eye.x, eye.y, eye.z) - selection.box.center
        val outline = if (input.surface != null) 0f else look.fullOutline
        val sight = FullView({ camera }, rect.width, rect.height)
        input.rig?.let { rig ->
            val draws = listOf(fullGround(selection, look)) + placed.map { look.rigDraw(it, rig, look.fullOutline, selection.posed) }
            return SceneView(rect, camera.viewProjection(rect.width, rect.height), draws)
        }
        selection.posed?.let { posed ->
            val draws = listOf(fullGround(selection, look)) + placed.map { look.posedDraw(it, posed, input.color, toCamera) }
            return SceneView(rect, camera.viewProjection(rect.width, rect.height), draws)
        }
        val draws = listOf(fullGround(selection, look)) +
            placed.map { look.colored(it, input.color, toCamera, outline).withFace(it, input.surface, sight) } +
            planned.sortedByDescending { (it.center.toGpu() - eye).length }.map { look.draw(it, look.fullOutline, plannedOpacity) }
        val ribbons = input.surface?.figure(sight) ?: look.borderRibbons(input.color)
        val fur = look.furDraws(input.fullFur, input.color, toCamera, selection)
        return SceneView(rect, camera.viewProjection(rect.width, rect.height), draws, ribbons, fur.layers)
    }
    val view = selection.part.axes.framing.getValue(body)
    val camera = input.cameras[view] ?: return null
    input.rig?.let { rig -> return SceneView(rect, camera.viewProjection(), rigDraws(body, camera.basis, selection, look, rig)) }
    val fur = input.lockedFur[view]?.let { shownFurDraws(it, view in selection.outlines, camera.basis.back, selection, look, input.hints, input.color) } ?: FurDraws.NONE
    val draws = lockedDraws(view, body, camera.basis, selection, look, input.hints, input.color, input.surface != null) { (part, draw) -> draw.withFace(part, input.surface, camera) }
    val traces = input.traces(camera.basis.back)
    return SceneView(rect, camera.viewProjection(), draws, lockedRibbons(view, camera, selection, look, input.hints, input.color, input.surface), fur.layers, traces.behind, traces.over)
}

/**
 * [part] in the character's pose, from [posed]: filled in its base color with the mesh's own outline. Color layers, lines, marks
 * and fur lie on the rest surface, so a posed Full leaves them out.
 */
private fun SheetLook.posedDraw(part: SheetPart, posed: Map<PartId, RenderMesh>, color: PaneColor?, toCamera: Vec3): MeshDraw {
    val draw = colored(part, color, toCamera, fullOutline)
    return draw.copy(mesh = part.id?.let(posed::get) ?: draw.mesh, layers = emptyList())
}

/** What [fur] adds to a pane with the camera at [toCamera]: the whole figure's when [hints] show it whole, else the selection's in front when [drawn]. */
internal fun shownFurDraws(fur: ShownFur, drawn: Boolean, toCamera: Vec3, selection: SheetSelection, look: SheetLook, hints: SheetHints, color: PaneColor?): FurDraws = when {
    hints.whole -> look.furDraws(fur, color, toCamera, selection)
    drawn -> look.furDraws(fur, color, toCamera, selection, selection.part.id?.let(::setOf).orEmpty(), DepthLayer.FRONT)
    else -> FurDraws.NONE
}

/**
 * What a locked camera on [view] draws, from [basis] in the pane of [body]: the ground, the character ghosted and the selection once
 * [view] is drawn, with the outlines left to the ribbons when [ribbons] draw them.
 */
internal fun lockedDraws(view: View, body: BodyView, basis: ViewBasis, selection: SheetSelection, look: SheetLook, hints: SheetHints, color: PaneColor? = null, ribbons: Boolean = false, faced: (Pair<SheetPart, MeshDraw>) -> MeshDraw = { it.second }): List<MeshDraw> = buildList {
    lockedGround(body, basis, selection, look)?.let(::add)
    val outline = if (ribbons) 0f else look.lockedOutline
    if (hints.whole) {
        (selection.others + selection.part).filter { !it.planned }.forEach { add(faced(it to look.colored(it, color, basis.back, outline))) }
        return@buildList
    }
    addAll(ghosts(selection, basis, look, hints, color))
    if (view in selection.outlines) add(faced(selection.part to look.colored(selection.part, color, basis.back, outline, layer = DepthLayer.FRONT)))
}

/** The lines through a locked [camera] on [view]: the whole figure's when [hints] show it whole, else the selection's once it is drawn in that view. */
internal fun lockedRibbons(view: View, camera: LockedCamera, selection: SheetSelection, look: SheetLook, hints: SheetHints, color: PaneColor?, surface: SurfaceRibbons?): List<RibbonDraw> {
    if (surface == null) return look.borderRibbons(color)
    if (hints.whole) return surface.figure(camera)
    val part = selection.part.id
    return if (part != null && view in selection.outlines) surface.selected(part, camera) else emptyList()
}

/** This draw of [part] with its face over it, as [surface] shows it through [view]. */
internal fun MeshDraw.withFace(part: SheetPart, surface: SurfaceRibbons?, view: SurfaceView): MeshDraw {
    val id = part.id ?: return this
    return surface?.faceLayers(id, view)?.takeIf { it.isNotEmpty() }?.let { copy(layers = layers + it) } ?: this
}

/** Everything but the selection, furthest from the viewer first so a nearer ghost blends over a further one. */
private fun ghosts(selection: SheetSelection, basis: ViewBasis, look: SheetLook, hints: SheetHints, color: PaneColor?): List<MeshDraw> {
    val strength = hints.ghost
    if (strength <= 0f) return emptyList()
    return selection.others
        .sortedBy { it.center dot basis.back }
        .map { look.colored(it, color, basis.back, look.ghostOutline, strength) }
}

/** What a locked camera from [basis] in the pane of [body] draws as the Rig stage shows it: the ground and every placed part in [rig]'s colors, posed. */
internal fun rigDraws(body: BodyView, basis: ViewBasis, selection: SheetSelection, look: SheetLook, rig: RigShading): List<MeshDraw> =
    listOfNotNull(lockedGround(body, basis, selection, look)) +
        (selection.others + selection.part).filter { !it.planned }.map { look.rigDraw(it, rig, look.lockedOutline, selection.posed) }

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
        val rig = input.full?.let { camera -> input.rig?.overlay?.invoke(FullView({ camera }, rect.width, rect.height)) } ?: ViewportOverlay.Empty
        (if (input.hints.whole) ViewportOverlay.Empty else fullRing(selection.part, input, rect)) + rig + input.fullOverlay
    } else {
        val view = selection.part.axes.framing.getValue(body)
        val camera = input.cameras[view] ?: return ViewportOverlay.Empty
        val rig = input.rig?.overlay?.invoke(camera) ?: ViewportOverlay.Empty
        lockedOverlay(view, camera, selection, input.look, input.hints, input.active) + rig + (input.toolOverlays[view] ?: ViewportOverlay.Empty)
    }
    return inPane.translated(Offset(rect.x.toFloat(), rect.y.toFloat()))
}

/** What a locked camera on [view] draws over the scene: the template's outline until [view] is drawn, Side's guides, and the accent frame when [view] is [active]. */
internal fun lockedOverlay(view: View, camera: LockedCamera, selection: SheetSelection, look: SheetLook, hints: SheetHints, active: View?): ViewportOverlay = buildOverlay {
    if (hints.whole) return@buildOverlay
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
