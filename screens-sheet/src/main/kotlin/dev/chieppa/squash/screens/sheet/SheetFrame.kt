package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.character.build.PlacedPart
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
    val posedPanes: Boolean = false,
    val posedSurface: SurfaceRibbons? = null,
)

/**
 * One immutable frame of the panes for the render thread. Full always shows the whole character, with what is not placed yet
 * faded except in Full first, which is there to judge the whole, and in its pose when the pose moves a bone; a locked pane shows the selection filled once its view is
 * drawn and as the mesh's dashed outline until then, with everything else ghosted behind it. With a [SheetInput.rig] every pane shows
 * the whole character as the Rig stage colors it, in its pose. With [SheetInput.posedPanes] the locked panes show the character in its
 * pose too while the pose moves a bone; otherwise they keep the rest pose to draw on. A pane in the pose draws as one at rest does,
 * with its lines, face and fur from [SheetInput.posedSurface] and the posed fur views, all found on the posed surface. A pane with no
 * part stays empty.
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
        val (planned, placed) = selection.whole.partition { it.planned }
        val eye = camera.eye
        val plannedOpacity = if (input.layout == SheetLayout.FULL_FIRST) 1f else PLANNED_OPACITY
        val toCamera = Vec3(eye.x, eye.y, eye.z) - selection.box.center
        val sight = FullView({ camera }, rect.width, rect.height)
        input.rig?.let { rig ->
            val draws = listOf(fullGround(selection, look)) + placed.mapNotNull { look.rigDraw(it, rig, look.fullOutline, selection.posed) } +
                look.rigSurfaceDraws(rig, look.fullOutline, selection.posed != null)
            return SceneView(rect, camera.viewProjection(rect.width, rect.height), draws)
        }
        val posed = selection.posed
        val surface = if (posed != null) input.posedSurface else input.surface
        if (input.hints.focus) {
            val draws = listOf(fullGround(selection, look)) + selectionOverGhosts(selection, look, input.color, camera, sight, surface, input.hints.ghost)
            val ribbons = selection.part.id?.let { surface?.selected(it, sight) }.orEmpty()
            val fur = shownFurDraws(input.fullFur, true, toCamera, selection, look, input.hints, input.color)
            return SceneView(rect, camera.viewProjection(rect.width, rect.height), draws, ribbons, fur.layers)
        }
        val shownPlanned = if (posed != null) emptyList() else planned
        val draws = listOf(fullGround(selection, look)) +
            placed.mapNotNull { look.colored(it, input.color, toCamera, 0f, posed = posed.of(it))?.withFace(it, surface, sight) } +
            look.surfaceDraws(input.color, toCamera, posed != null, toViewer = sight::toViewer) +
            shownPlanned.sortedByDescending { (it.center.toGpu() - eye).length }.map { look.draw(it, look.fullOutline, plannedOpacity) }
        val ribbons = surface?.figure(sight).orEmpty()
        val fur = look.furDraws(input.fullFur, input.color, toCamera, selection)
        return SceneView(rect, camera.viewProjection(rect.width, rect.height), draws, ribbons, fur.layers)
    }
    val view = selection.part.axes.framing.getValue(body)
    val camera = input.cameras[view] ?: return null
    input.rig?.let { rig -> return SceneView(rect, camera.viewProjection(), rigDraws(body, camera.basis, selection, look, rig)) }
    val posed = selection.posed?.takeIf { input.posedPanes }
    val surface = if (posed != null) input.posedSurface else input.surface
    val fur = input.lockedFur[view]?.let { shownFurDraws(it, view in selection.outlines, camera.basis.back, selection, look, input.hints, input.color) } ?: FurDraws.NONE
    val draws = lockedDraws(view, body, camera.basis, selection, look, input.hints, input.color, posed) { (part, draw) -> draw.withFace(part, surface, camera) }
    val traces = input.traces(camera.basis.back)
    return SceneView(rect, camera.viewProjection(), draws, lockedRibbons(view, camera, selection, look, input.hints, input.color, surface), fur.layers, traces.behind, traces.over)
}

/** [part] as this pose shows it, or null for a part the pose holds none of or with no pose. */
internal fun Map<PartId, PlacedPart>?.of(part: SheetPart): PlacedPart? = part.id?.let { this?.get(it) }

/**
 * What [fur] adds to a pane with the camera at [toCamera]: the whole figure's when [hints] show every part in full, else the selection's in front when
 * [drawn] or when [hints] focus on it.
 */
internal fun shownFurDraws(fur: ShownFur, drawn: Boolean, toCamera: Vec3, selection: SheetSelection, look: SheetLook, hints: SheetHints, color: PaneColor?): FurDraws = when {
    hints.allInFull -> look.furDraws(fur, color, toCamera, selection)
    drawn || hints.focus -> look.furDraws(fur, color, toCamera, selection, selection.part.id?.let(::setOf).orEmpty(), DepthLayer.FRONT)
    else -> FurDraws.NONE
}

/**
 * What a locked camera on [view] draws, from [basis] in the pane of [body]: the ground, the character ghosted and the selection once
 * [view] is drawn, or always where [hints] focus on it, their outlines left to the ribbons.
 */
internal fun lockedDraws(view: View, body: BodyView, basis: ViewBasis, selection: SheetSelection, look: SheetLook, hints: SheetHints, color: PaneColor? = null, posed: Map<PartId, PlacedPart>? = null, faced: (Pair<SheetPart, MeshDraw>) -> MeshDraw = { it.second }): List<MeshDraw> = buildList {
    lockedGround(body, basis, selection, look)?.let(::add)
    if (hints.allInFull) {
        selection.whole.filter { !it.planned }.forEach { part -> look.colored(part, color, basis.back, 0f, posed = posed.of(part))?.let { add(faced(part to it)) } }
        addAll(look.surfaceDraws(color, basis.back, posed != null))
        return@buildList
    }
    addAll(ghosts(selection, basis, look, hints, color, posed))
    if (view !in selection.outlines && !hints.focus) return@buildList
    look.colored(selection.part, color, basis.back, 0f, layer = DepthLayer.FRONT, posed = posed.of(selection.part))?.let { add(faced(selection.part to it)) }
    if (color.holds(selection.part)) addAll(look.surfaceDraws(color, basis.back, posed != null, layer = DepthLayer.FRONT, only = selection.part.id))
}

/**
 * The lines through a locked [camera] on [view]: the whole figure's when [hints] show every part in full, else the selection's once it is drawn in that
 * view or where [hints] focus on it.
 */
internal fun lockedRibbons(view: View, camera: LockedCamera, selection: SheetSelection, look: SheetLook, hints: SheetHints, color: PaneColor?, surface: SurfaceRibbons?): List<RibbonDraw> {
    if (surface == null) return emptyList()
    if (hints.allInFull) return surface.figure(camera)
    val part = selection.part.id
    return if (part != null && (view in selection.outlines || hints.focus)) surface.selected(part, camera) else emptyList()
}

/** This draw of [part] with its face over it, as [surface] shows it through [view]. */
internal fun MeshDraw.withFace(part: SheetPart, surface: SurfaceRibbons?, view: SurfaceView): MeshDraw {
    val id = part.id ?: return this
    return surface?.faceLayers(id, view)?.takeIf { it.isNotEmpty() }?.let { copy(layers = layers + it) } ?: this
}

/** The selection in full in front, over the rest of the character at [ghost] strength, furthest ghost first, as the free [camera] seen through [sight] shows them. */
internal fun selectionOverGhosts(
    selection: SheetSelection,
    look: SheetLook,
    color: PaneColor?,
    camera: CameraView,
    sight: FullView,
    surface: SurfaceRibbons?,
    ghost: Float,
): List<MeshDraw> = buildList {
    val toCamera = Vec3(camera.eye.x, camera.eye.y, camera.eye.z) - selection.box.center
    val posed = selection.posed
    look.colored(selection.part, color, toCamera, 0f, layer = DepthLayer.FRONT, posed = posed.of(selection.part))?.let { add(it.withFace(selection.part, surface, sight)) }
    if (color.holds(selection.part)) addAll(look.surfaceDraws(color, toCamera, posed != null, layer = DepthLayer.FRONT, only = selection.part.id, toViewer = sight::toViewer))
    if (ghost <= 0f) return@buildList
    addAll(look.surfaceDraws(color, toCamera, posed != null, look.ghostOutline, ghost, toViewer = sight::toViewer))
    selection.others
        .sortedByDescending { (it.center.toGpu() - camera.eye).length }
        .forEach { part -> look.colored(part, color, toCamera, look.ghostOutline, ghost, posed = posed.of(part))?.let(::add) }
}

/** Everything but the selection, furthest from the viewer first so a nearer ghost blends over a further one. */
private fun ghosts(selection: SheetSelection, basis: ViewBasis, look: SheetLook, hints: SheetHints, color: PaneColor?, posed: Map<PartId, PlacedPart>?): List<MeshDraw> {
    val strength = hints.ghost
    if (strength <= 0f) return emptyList()
    return look.surfaceDraws(color, basis.back, posed != null, look.ghostOutline, strength) + selection.others
        .filter { posed == null || !it.planned }
        .sortedBy { it.center dot basis.back }
        .mapNotNull { look.colored(it, color, basis.back, look.ghostOutline, strength, posed = posed.of(it)) }
}

/** What a locked camera from [basis] in the pane of [body] draws as the Rig stage shows it: the ground and every placed part in [rig]'s colors, posed. */
internal fun rigDraws(body: BodyView, basis: ViewBasis, selection: SheetSelection, look: SheetLook, rig: RigShading): List<MeshDraw> =
    listOfNotNull(lockedGround(body, basis, selection, look)) +
        (selection.others + selection.part).filter { !it.planned }.mapNotNull { look.rigDraw(it, rig, look.lockedOutline, selection.posed) } +
        look.rigSurfaceDraws(rig, look.lockedOutline, selection.posed != null)

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
