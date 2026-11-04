package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.chieppa.stretch.frame.ViewportOverlay
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.single.SingleSurface
import dev.chieppa.stretch.gpu.OrbitCamera
import dev.chieppa.stretch.gpu.orbitNavigation
import dev.chieppa.stretch.theme.Stretch
import kotlin.math.roundToInt
import kotlin.math.sin

/** The camera Full starts at: three quarters from the front, with the character's forward to the left as on the board. */
private const val FULL_YAW = 2.54f
private const val FULL_PITCH = 0.35f

/** Room around the character in Full's view, as a multiple of what fits it exactly. */
private const val ORBIT_MARGIN = 0.9f

/** The test tag of [pane] in the sheet. */
fun sheetPaneTag(pane: SheetPane) = "sheet.pane.${pane.name}"

/**
 * The workspace's viewport area as the boards draw it, in the layout the Layout control picks. In Sheet, the strip with
 * [tools] and the control, then Top, Full, Side and Front in one native surface; in Full first, the same panes in that
 * surface with Full large above the other three; in Single, the strip with the control above one viewport, with [tools]
 * floating on it. A [tool] gets a locked view's pointer input and overlay, and Full's when it takes the free camera.
 * [paneToast] shows over the surface, at the bottom left of the panes or the foot of the one viewport. [surface] draws the
 * character's lines, in place of the meshes' own outlines.
 */
@Composable
fun SheetPanes(
    state: AppState,
    modifier: Modifier = Modifier,
    tools: @Composable RowScope.() -> Unit = {},
    tool: PaneTool? = null,
    paneToast: (@Composable () -> Unit)? = null,
    surface: SurfaceRibbons? = null,
) {
    val layout by state.layout.collectAsState()
    val pick: (SheetLayout) -> Unit = { state.layout.value = it }
    Column(modifier) {
        if (layout == SheetLayout.SINGLE) {
            SheetStrip(layout = layout, onLayout = pick)
            SingleSurface(state, tool, tools, paneToast, Modifier.weight(1f).fillMaxWidth(), surface)
        } else {
            SheetStrip(layout = layout, onLayout = pick, tools = tools)
            SheetSurface(state, layout, tool, paneToast, Modifier.weight(1f).fillMaxWidth(), surface)
        }
    }
}

@Composable
private fun SheetSurface(state: AppState, layout: SheetLayout, tool: PaneTool?, paneToast: (@Composable () -> Unit)?, modifier: Modifier, surface: SurfaceRibbons?) {
    val selection = rememberSheetSelection(state)
    val part = selection?.part
    val orbit = remember { OrbitCamera(yaw = FULL_YAW, pitch = FULL_PITCH) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val active by state.activeView.collectAsState()
    val mirror by state.mirror.collectAsState()
    val gap = with(LocalDensity.current) { Stretch.stroke.toPx() }.roundToInt().coerceAtLeast(1)
    val rects = paneRects(layout, size.width, size.height, gap)
    val held = remember(part?.id, size, layout) { HeldCameras() }
    val holding = tool?.holdsFit == true || tool?.drawingIn != null
    LaunchedEffect(part?.id) { state.paneNavigation.fitAll() }
    val cameras = if (selection == null || size == IntSize.Zero) emptyMap() else held.camerasFor(selection, rects, holding, state.paneNavigation)
    val refitting = rememberRefitting(state)
    val progress = rememberRefitProgress(state)
    val shownActive = tool?.drawingIn ?: active
    SideEffect { state.curves.snapped = null }
    val disagreements = paneDisagreements(selection, cameras, tool)
    SideEffect { state.curves.conflicts = disagreements.size }
    val framed = selection?.box
    val open by state.session.collectAsState()
    val count = selection?.others?.count { !it.planned }
    LaunchedEffect(open, count, framed != null) {
        if (framed != null) orbit.lookAt(framed.center.toGpu(), framed.radius / sin(orbit.fovY / 2f) * ORBIT_MARGIN)
    }
    var refused by remember { mutableStateOf(false) }
    val fullRect = rects[SheetPane.FULL.ordinal]
    val fullView = remember(orbit, fullRect.width, fullRect.height) { FullView({ orbit.view }, fullRect.width, fullRect.height) }
    val color = rememberPaneColor(state)
    val places = remember { PreviewPlaces() }
    SideEffect { open?.showColorAt(largestColorScale(cameras.values, orbit.view, fullRect.height)) }
    val frame = sheetFrame(
        SheetInput(
            rects = rects,
            width = size.width,
            height = size.height,
            look = rememberSheetLook(),
            selection = selection,
            cameras = cameras,
            full = if (part != null) orbit.view else null,
            active = shownActive,
            toolOverlays = paneOutlineOverlays(selection, cameras, tool, rememberOutlineLook(), disagreements, shownActive),
            fullOverlay = tool?.fullOverlay(fullView) ?: ViewportOverlay.Empty,
            hints = SheetHints(state.curves.templateHint, state.curves.ghost, tool?.showsWhole == true),
            layout = layout,
            color = color,
            previews = places.previews(tool?.showsPreviews == true, color?.shading ?: ObjectShading()),
            surface = surface,
        ),
    )
    Box(modifier.onSizeChanged { size = it }.onGloballyPositioned { places.surface = it.positionInRoot() }) {
        SceneSurface(frame.takeIf { size != IntSize.Zero }, Modifier.fillMaxSize())
        SheetPaneGrid(gap, Modifier.fillMaxSize(), layout) { pane ->
            val body = pane.body
            val axes = part?.axes
            val view = if (body == null) null else axes?.framing?.getValue(body) ?: plainView(body)
            val camera = view?.let { cameras[it] }
            val input = when {
                body == null -> Modifier.orbitNavigation(orbit).then(freePaneInput(tool, fullView, layout) { refused = it })
                camera == null -> Modifier
                else -> Modifier
                    .paneNavigation(state.paneNavigation, camera, holding)
                    .pressed(camera.view) { state.activeView.value = it }
                    .then(tool?.pointerInput(camera.view, camera) ?: Modifier)
            }
            val chip = if (selection == null) {
                null
            } else {
                paneChip(view, selection.outlines.keys, tool?.drawingIn, refitting, selection.overridden, mirror, disagreements.mapTo(mutableSetOf()) { it.view })
            }
            val fitError = if (tool?.drawingIn != null) null else view?.let { tool?.fitErrorPixels(it) }
            val shownChip = if (layout == SheetLayout.FULL_FIRST) fullFirstChip(chip, refused && body == null, fitError) else chip
            val zoom = view?.takeIf { camera != null }?.let { state.paneNavigation[it] }?.takeIf { it != PaneNav() }?.zoom
            PaneViewport(
                body,
                shownChip,
                input.testTag(sheetPaneTag(pane)),
                fitError,
                progress,
                if (pane == SheetPane.FULL && tool?.showsPreviews == true) ({ ShadingPreviewTiles(state, places) }) else paneToast?.takeIf { pane == SheetPane.SIDE },
                zoom,
                { view?.let(state.paneNavigation::fit) },
                short = layout == SheetLayout.FULL_FIRST,
            )
        }
    }
}

/** Whether the open character's graph is still deriving, which is what a refit is. */
@Composable
private fun rememberRefitting(state: AppState): Boolean {
    val session by state.session.collectAsState()
    return session?.graph?.settled?.collectAsState()?.value == false
}

/** How far through its refit the open character's graph is, as the graph counts it. */
@Composable
private fun rememberRefitProgress(state: AppState): Float {
    val session by state.session.collectAsState()
    return session?.graph?.progress?.collectAsState()?.value?.fraction ?: 1f
}

/** Reports a press in the pane without taking it from anything else. */
private fun Modifier.pressed(view: View, onPress: (View) -> Unit): Modifier = pointerInput(view) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPress(view)
    }
}

/** The view a body view holds before there is a part to frame it. */
private fun plainView(body: BodyView): View = when (body) {
    BodyView.TOP -> View.TOP
    BodyView.SIDE -> View.SIDE
    BodyView.FRONT -> View.FRONT
}

/** The locked panes' cameras, kept between compositions so they stay the same objects while the fit and the socket hold. */
private class HeldCameras {
    private var fit: SheetFit? = null
    private var origin: Vec3? = null
    private var scale: Float? = null
    private var lengthFactor: Float? = null
    private var axes: PartAxes? = null
    private var navs: Map<View, PaneNav> = emptyMap()
    private var cameras: Map<View, LockedCamera> = emptyMap()

    /** [hold] keeps them still while a stroke or a drag is under way, whatever the part does beneath it. */
    fun camerasFor(selection: SheetSelection, rects: List<PixelRect>, hold: Boolean, navigation: PaneNavigation): Map<View, LockedCamera> {
        if (hold && cameras.isNotEmpty()) return cameras
        val part = selection.part
        val next = keepFit(fit, part.box, part.axes, rects, depthReach(selection, fit))
        val nextNavs = View.entries.associateWith { navigation[it] }
        if (next != fit || part.origin != origin || selection.drawingScale != scale || part.lengthFactor != lengthFactor || part.axes != axes || nextNavs != navs) {
            fit = next
            axes = part.axes
            navs = nextNavs
            origin = part.origin
            scale = selection.drawingScale
            lengthFactor = part.lengthFactor
            cameras = SheetPane.entries.mapNotNull { pane ->
                val body = pane.body ?: return@mapNotNull null
                val rect = rects[pane.ordinal]
                val view = part.axes.framing.getValue(body)
                view to LockedCamera(view, part.axes, navigation[view].applied(next), rect.width, rect.height, part.origin, selection.drawingScale, part.lengthFactor)
            }.toMap()
        }
        return cameras
    }

    /** How far from the panes' middle the character reaches, so the depth range holds all of it. */
    private fun depthReach(selection: SheetSelection, fit: SheetFit?): Float {
        val center = fit?.center ?: selection.part.box.center
        return (selection.box.center - center).length + selection.box.radius
    }
}
