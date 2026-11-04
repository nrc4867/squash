package dev.chieppa.squash.screens.single

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
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
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SceneSurface
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.paneDisagreements
import dev.chieppa.squash.screens.sheet.paneOutlineOverlays
import dev.chieppa.squash.screens.sheet.rememberOutlineLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.stretch.frame.Toolbar
import dev.chieppa.stretch.frame.ToolbarSeparator
import dev.chieppa.stretch.frame.Viewport
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.gpu.orbitNavigation

/**
 * The Single layout: one large viewport with [tools] and Snap view floating top-left, the HUD top-right and the thumbnails
 * bottom-left, a toast at its foot and the fit's progress bottom-right while the graph works. The camera orbits freely and
 * [tool] takes the pointer only while it is snapped to a view.
 */
@Composable
internal fun SingleSurface(
    state: AppState,
    tool: PaneTool?,
    tools: @Composable RowScope.() -> Unit,
    paneToast: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    val selection = rememberSheetSelection(state)
    val part = selection?.part
    val camera = remember { SingleCamera() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val open by state.session.collectAsState()
    val sized = size != IntSize.Zero
    LaunchedEffect(open, part?.id, sized) {
        if (part != null && sized) camera.frame(part.box, part.axes, size.width, size.height)
    }
    val hold = tool?.holdsFit == true || tool?.drawingIn != null
    val snapped = part?.let { camera.snap(it.axes, hold) }
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
    val frame = singleFrame(
        SingleInput(
            width = size.width,
            height = size.height,
            look = rememberSheetLook(),
            selection = selection,
            locked = locked,
            free = camera.orbit.view,
            toolOverlay = overlay,
            hints = SheetHints(state.curves.templateHint, state.curves.ghost),
        ),
    )
    val fitting = rememberFitting(state)
    val drawing = if (locked != null) tool?.pointerInput(locked.view, locked) ?: Modifier else Modifier
    Box(modifier.onSizeChanged { size = it }) {
        SceneSurface(frame.takeIf { sized }, Modifier.fillMaxSize())
        Viewport(
            Modifier.orbitNavigation(camera.orbit).then(drawing).testTag(SingleTags.VIEWPORT),
            nativeContent = true,
            topStart = {
                Toolbar(Modifier.testTag(SingleTags.TOOLBAR)) {
                    tools()
                    ToolbarSeparator()
                    SnapViewControl(snapped) { view -> part?.let { camera.snapTo(view, it.axes) } }
                }
            },
            topEnd = { SingleHud(state, snapped) },
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
            bottomCenter = paneToast,
        ) {}
    }
}
