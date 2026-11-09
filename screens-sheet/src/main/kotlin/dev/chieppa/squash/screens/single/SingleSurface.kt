package dev.chieppa.squash.screens.single

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.references.rememberTraces
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.FurViews
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.furCamera
import dev.chieppa.squash.screens.sheet.furDraws
import dev.chieppa.squash.screens.sheet.rememberFurView
import dev.chieppa.squash.screens.sheet.SceneSurface
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.largestColorScale
import dev.chieppa.squash.screens.sheet.SurfaceRibbons
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.hovered
import dev.chieppa.squash.screens.sheet.paneDisagreements
import dev.chieppa.squash.screens.sheet.paneOutlineOverlays
import dev.chieppa.squash.screens.sheet.rememberOutlineLook
import dev.chieppa.squash.screens.sheet.rememberPaneColor
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.posesPane
import dev.chieppa.stretch.frame.Toolbar
import dev.chieppa.stretch.frame.Viewport
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.squash.screens.sheet.RigShading
import dev.chieppa.stretch.gpu.orbitNavigation

/**
 * The Single layout: one large viewport with Snap view floating top-left, the HUD top-right and the thumbnails bottom-left, a toast at its foot and
 * the fit's progress bottom-right while the graph works. The camera orbits freely and [tool] takes the pointer only while it is snapped to a view.
 * Pressing the Snap view the camera is on looks from that view's other side.
 */
@Composable
internal fun SingleSurface(
    state: AppState,
    tool: PaneTool?,
    paneToast: (@Composable () -> Unit)?,
    modifier: Modifier,
    surface: SurfaceRibbons? = null,
    foot: (@Composable (toViewer: Vec3, turn: (Vec3) -> Unit) -> Unit)? = null,
    rig: RigShading? = null,
    posedSurface: SurfaceRibbons? = null,
) {
    val selection = rememberSheetSelection(state)
    val part = selection?.part
    val camera = remember { SingleCamera() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val open by state.session.collectAsState()
    val sized = size != IntSize.Zero
    LaunchedEffect(open, part?.id, sized, state.paneNavigation.freeFits) {
        if (part != null && sized) camera.frame(part.box, part.axes, size.width, size.height)
    }
    val hold = tool?.holdsFit == true || tool?.drawingIn != null
    val snapped = part?.let { camera.snap(it.axes, hold) }
    val direction = if (part != null && snapped != null) part.axes.bodyViewOf(snapped) else null
    val flipped = part != null && snapped != null && camera.isFlipped(snapped, part.axes)
    val locked = if (selection != null && snapped != null && sized) camera.lockedCamera(snapped, selection, size.width, size.height, hold) else null
    val outlineLook = rememberOutlineLook()
    val shownCameras = locked?.let { mapOf(it.view to it) }.orEmpty()
    val disagreements = paneDisagreements(selection, shownCameras, tool)
    SideEffect {
        state.curves.snapped = snapped
        state.curves.conflicts = disagreements.size
        if (snapped != null) state.activeView.value = snapped
    }
    val overlay = locked?.let { paneOutlineOverlays(selection, shownCameras, tool, outlineLook, disagreements)[it.view] } ?: ViewportOverlay.Empty
    val color = rememberPaneColor(state)
    val free = remember(camera, size) { FullView({ camera.orbit.view }, size.width, size.height) }
    SideEffect { open?.showColorAt(largestColorScale(shownCameras.values, camera.orbit.view, size.height)) }
    val look = rememberSheetLook()
    val stage by state.stage.collectAsState()
    val poseView by state.poseView.collectAsState()
    val posedLocked = posesPane(stage, poseView, locked = true)
    val fur = rememberFurView(
        open,
        FurViews.SINGLE,
        if (!sized || selection == null) null else locked?.let { furCamera(it) } ?: furCamera(camera.orbit.view, size.width, size.height),
        posed = selection?.posed != null && (locked == null || posedLocked),
    )
    val frame = singleFrame(
        SingleInput(
            width = size.width,
            height = size.height,
            look = look,
            selection = selection,
            locked = locked,
            free = camera.orbit.view,
            toolOverlay = overlay,
            freeOverlay = tool?.takeIf { it.takesFreeCamera && locked == null }?.fullOverlay(free) ?: ViewportOverlay.Empty,
            hints = SheetHints(state.curves.templateHint, state.curves.ghost, tool?.showsWhole == true || rig != null),
            color = color,
            surface = surface,
            fur = fur,
            traces = rememberTraces(state),
            rig = rig,
            posedSurface = posedSurface,
            posedLocked = posedLocked,
        ),
    )
    val fitting = rememberFitting(state)
    val drawing = if (locked != null) tool?.pointerInput(locked.view, locked) ?: Modifier else tool?.takeIf { it.takesFreeCamera }?.fullInput(free) ?: Modifier
    Box(modifier.onSizeChanged { size = it }) {
        SceneSurface(frame.takeIf { sized }, Modifier.fillMaxSize())
        Viewport(
            Modifier.orbitNavigation(camera.orbit).then(drawing).hovered(SheetPane.FULL, null, state).testTag(SingleTags.VIEWPORT),
            nativeContent = true,
            topStart = {
                Toolbar(Modifier.testTag(SingleTags.TOOLBAR)) {
                    SnapViewControl(direction) { body ->
                        part?.let { camera.snapTo(it.axes.framing.getValue(body), it.axes, flipped = body == direction && !flipped) }
                    }
                }
            },
            topEnd = { SingleHud(state, direction, flipped) },
            bottomStart = selection?.let {
                {
                    ThumbnailStrip(
                        it,
                        snapped,
                        tool?.liveStroke,
                        state.curves.templateHint,
                        { view -> tool?.takeIf { it.drawingIn == null }?.fitErrorPixels(view) },
                        { view -> camera.snapTo(view, it.part.axes) },
                        tool?.liveAlongside.orEmpty(),
                    )
                }
            },
            bottomEnd = fitting?.let { { FitProgressBar(it) } },
            bottomCenter = if (foot != null) ({ foot(camera.toViewerNow()) { camera.turnTo(it) } }) else paneToast,
        ) {}
    }
}
