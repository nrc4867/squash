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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.stretch.frame.NativeSurface
import dev.chieppa.stretch.gpu.OrbitCamera
import dev.chieppa.stretch.gpu.WebGpuRenderer
import dev.chieppa.stretch.gpu.orbitNavigation
import dev.chieppa.stretch.theme.Stretch
import kotlin.math.roundToInt
import kotlin.math.sin

/** The camera Full starts at: three quarters from the front, with the character's forward to the left as on the board. */
private const val FULL_YAW = 2.54f
private const val FULL_PITCH = 0.35f

/** Room around the character in Full's view, as a multiple of what fits it exactly. */
private const val ORBIT_MARGIN = 0.9f

/** The view that shows as active until a pane is pressed. */
private val FIRST_ACTIVE = View.SIDE

/** The test tag of [pane] in the sheet. */
fun sheetPaneTag(pane: SheetPane) = "sheet.pane.${pane.name}"

/**
 * The workspace's viewport area as the board draws it: the strip with [tools] and the Layout control, then Top, Full,
 * Side and Front in one native surface. A [tool] gets the locked panes' pointer input and overlay.
 */
@Composable
fun SheetPanes(
    state: AppState,
    modifier: Modifier = Modifier,
    tools: @Composable RowScope.() -> Unit = {},
    tool: PaneTool? = null,
) {
    Column(modifier) {
        SheetStrip(tools = tools)
        SheetSurface(state, tool, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun SheetSurface(state: AppState, tool: PaneTool?, modifier: Modifier) {
    val selection = rememberSheetSelection(state)
    val part = selection?.part
    val renderer = remember { WebGpuRenderer<SceneFrame> { ViewsScene(it) } }
    val orbit = remember { OrbitCamera(yaw = FULL_YAW, pitch = FULL_PITCH) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var active by remember { mutableStateOf(FIRST_ACTIVE) }
    val gap = with(LocalDensity.current) { Stretch.stroke.toPx() }.roundToInt().coerceAtLeast(1)
    val rects = sheetPaneRects(size.width, size.height, gap)
    val held = remember(part?.id, size) { HeldCameras() }
    val cameras = if (selection == null || size == IntSize.Zero) emptyMap() else held.camerasFor(selection, rects, tool?.holdsFit == true || tool?.drawingIn != null)
    val refitting = rememberRefitting(state)
    val framed = selection?.box
    val open by state.session.collectAsState()
    val count = selection?.others?.count { !it.planned }
    LaunchedEffect(open, count, framed != null) {
        if (framed != null) orbit.lookAt(framed.center.toGpu(), framed.radius / sin(orbit.fovY / 2f) * ORBIT_MARGIN)
    }
    val frame = sheetFrame(
        SheetInput(
            rects = rects,
            width = size.width,
            height = size.height,
            look = rememberSheetLook(),
            selection = selection,
            cameras = cameras,
            full = if (part != null) orbit.view else null,
            active = tool?.drawingIn ?: active,
            toolOverlays = paneOutlineOverlays(selection, cameras, tool, rememberOutlineLook()),
            hints = SheetHints(state.curves.templateHint, state.curves.ghost),
        ),
    )
    Box(modifier.onSizeChanged { size = it }) {
        NativeSurface(frame, renderer, Modifier.fillMaxSize(), onFailure = { it.printStackTrace() })
        SheetPaneGrid(gap, Modifier.fillMaxSize()) { pane ->
            val body = pane.body
            val axes = part?.axes
            val view = if (body == null) null else axes?.framing?.getValue(body) ?: plainView(body)
            val camera = view?.let { cameras[it] }
            val input = when {
                body == null -> Modifier.orbitNavigation(orbit)
                camera == null -> Modifier
                else -> Modifier.pressed(camera.view) { active = it }.then(tool?.pointerInput(camera.view, camera) ?: Modifier)
            }
            val chip = if (selection == null) null else paneChip(view, selection.outlines.keys, tool?.drawingIn, refitting)
            PaneViewport(view, chip, input.testTag(sheetPaneTag(pane)), view?.let { tool?.fitErrorPixels(it) })
        }
    }
}

/** Whether the open character's graph is still deriving, which is what a refit is. */
@Composable
private fun rememberRefitting(state: AppState): Boolean {
    val session by state.session.collectAsState()
    return session?.graph?.settled?.collectAsState()?.value == false
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
    private var cameras: Map<View, LockedCamera> = emptyMap()

    /** [hold] keeps them still while a stroke or a drag is under way, whatever the part does beneath it. */
    fun camerasFor(selection: SheetSelection, rects: List<PixelRect>, hold: Boolean): Map<View, LockedCamera> {
        if (hold && cameras.isNotEmpty()) return cameras
        val part = selection.part
        val next = keepFit(fit, part.box, rects, depthReach(selection, fit))
        if (next != fit || part.origin != origin || selection.drawingScale != scale) {
            fit = next
            origin = part.origin
            scale = selection.drawingScale
            cameras = SheetPane.entries.mapNotNull { pane ->
                val body = pane.body ?: return@mapNotNull null
                val rect = rects[pane.ordinal]
                val view = part.axes.framing.getValue(body)
                view to LockedCamera(view, part.axes, next, rect.width, rect.height, part.origin, selection.drawingScale)
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
