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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import dev.chieppa.squash.document.View
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

/** The camera Full starts at: three quarters from the front, with the part's forward to the left as on the board. */
private const val FULL_YAW = 2.54f
private const val FULL_PITCH = 0.35f

/** Room around a part in Full's view, as a multiple of what fits it exactly. */
private const val ORBIT_MARGIN = 1.5f

/** The view that shows as active until a pane is pressed. */
private val FIRST_ACTIVE = View.SIDE

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
    val cameras = if (part == null || size == IntSize.Zero) emptyMap() else held.camerasFor(part, rects)
    val partId = part?.id
    LaunchedEffect(partId) {
        if (part != null) orbit.lookAt(part.center.toGpu(), part.radius / sin(orbit.fovY / 2f) * ORBIT_MARGIN)
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
        ),
    )
    Box(modifier.onSizeChanged { size = it }) {
        NativeSurface(frame, renderer, Modifier.fillMaxSize(), onFailure = { it.printStackTrace() })
        SheetPaneGrid(gap, Modifier.fillMaxSize()) { pane ->
            val view = pane.view
            val camera = view?.let { cameras[it] }
            val input = when {
                view == null -> Modifier.orbitNavigation(orbit)
                camera == null -> Modifier
                else -> Modifier.pressed(view) { active = it }.then(tool?.pointerInput(view, camera) ?: Modifier)
            }
            val chip = if (selection == null) null else paneChip(pane, selection.outlines.keys, tool?.drawingIn)
            PaneViewport(pane, chip, input)
        }
    }
}

/** Reports a press in the pane without taking it from anything else. */
private fun Modifier.pressed(view: View, onPress: (View) -> Unit): Modifier = pointerInput(view) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPress(view)
    }
}

/** The locked panes' cameras, kept between compositions so they stay the same objects while the fit holds. */
private class HeldCameras {
    private var fit: SheetFit? = null
    private var cameras: Map<View, LockedCamera> = emptyMap()

    fun camerasFor(part: SheetPart, rects: List<PixelRect>): Map<View, LockedCamera> {
        val next = keepFit(fit, part.axes, part.extents, rects)
        if (next != fit) {
            fit = next
            cameras = SheetPane.entries.mapNotNull { pane ->
                val rect = rects[pane.ordinal]
                pane.view?.let { it to LockedCamera(it, part.axes, next, rect.width, rect.height) }
            }.toMap()
        }
        return cameras
    }
}
