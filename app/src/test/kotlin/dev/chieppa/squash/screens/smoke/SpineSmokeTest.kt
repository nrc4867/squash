package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.sheet.OutlineLook
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.paneOutlineOverlays
import dev.chieppa.squash.screens.sheet.rememberOutlineLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.single.SingleCamera
import dev.chieppa.squash.screens.single.SingleInput
import dev.chieppa.squash.screens.single.SingleTags
import dev.chieppa.squash.screens.single.singleFrame
import dev.chieppa.squash.screens.spine.SpineLook
import dev.chieppa.squash.screens.spine.SpineWidthTool
import dev.chieppa.squash.screens.spine.rememberSpineLook
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val SETTLE_MILLIS = 30_000L
private const val STROKE_POINTS = 50

/** How far along a the strokes reach, and how far a drag comes out from the spine, in screen pixels. */
private const val REACH_PIXELS = 220f
private const val DRAG_PIXELS = 26f

private object NoSpineFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** Board 3b composed whole with a width being dragged, with the panes rendered apart on a real GPU device. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class SpineSmokeTest {
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> failures += e })
    private val templates = TemplateFolder.shipped()

    @AfterTest
    fun nothingFailedInTheBackground() {
        scope.cancel()
        assertEquals(emptyList(), failures)
    }

    private class Looks(val sheet: SheetLook, val outline: OutlineLook, val spine: SpineLook)

    private class Bench(val state: AppState) {
        var selection: SheetSelection? = null
        var looks: Looks? = null
    }

    private fun ComposeUiTest.tailInWorkspace(): Bench {
        val bench = Bench(AppState(templates, scope))
        setStretchContent {
            SquashApp(bench.state, NoSpineFiles)
            val selected = rememberSheetSelection(bench.state)
            val looks = Looks(rememberSheetLook(), rememberOutlineLook(), rememberSpineLook())
            SideEffect {
                bench.selection = selected
                bench.looks = looks
            }
        }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { bench.state.screen.value == Screen.WORKSPACE }
        onNodeWithTag(ShellTags.partRow("tail")).performClick()
        onNodeWithTag(ShellTags.PICK_DRAW).performClick()
        settled(bench)
        onNodeWithContentDescription("Part").performClick()
        bench.state.tool.value = BuildTool.SPINE_WIDTH
        waitForIdle()
        return bench
    }

    private fun ComposeUiTest.settled(bench: Bench) {
        waitUntil(timeoutMillis = SETTLE_MILLIS) {
            bench.state.session.value?.graph?.settled?.value == true && bench.selection?.part?.id == bench.state.selectedPart.value && bench.selection != null
        }
    }

    /** A centre stroke through [camera]'s plane, and a press on its width handle dragged out and held. */
    private fun ComposeUiTest.strokeAndHoldWidth(
        tag: String,
        bench: Bench,
        camera: dev.chieppa.squash.screens.sheet.LockedCamera,
        at: (Float, Float) -> CurvePoint,
        drag: Float,
    ) {
        val node = onNodeWithTag(tag)
        node.performMouseInput {
            moveTo(camera.toPixel(at(0f, camera.planePixel)))
            press()
            for (i in 1..STROKE_POINTS) moveTo(camera.toPixel(at(i.toFloat() / STROKE_POINTS, camera.planePixel)))
            release()
        }
        waitForIdle()
        val spine = bench.state.spine
        val across = assertNotNull(spine.preview, "the stroke previews").shape.across(spine.handleAt)
        val held = camera.toPixel(CurvePoint(across.second.x, across.second.y))
        val other = camera.toPixel(CurvePoint(across.first.x, across.first.y))
        val out = (held - other) / (held - other).getDistance()
        node.performMouseInput {
            moveTo(held)
            press()
            moveTo(held + out * (drag / 2f))
            moveTo(held + out * drag)
        }
        waitForIdle()
    }

    private val tailStroke: (Float, Float) -> CurvePoint = { t, pixel ->
        CurvePoint(REACH_PIXELS * pixel * t, 0.3f * REACH_PIXELS * pixel * sin(t * 2.4f))
    }

    /** Out along the part and curling back over itself, so that its inner offsets lie inside what each plane reads. */
    private val bentStroke: (Float, Float) -> CurvePoint = { t, pixel ->
        val angle = t * PI.toFloat() * 0.85f
        CurvePoint(0.5f * REACH_PIXELS * pixel * sin(angle), -0.5f * REACH_PIXELS * pixel * (1f - cos(angle)))
    }

    @Test
    fun theBoardInSingleWithAWidthBeingDragged() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val bench = tailInWorkspace()
        onNode(hasText("Single") and isSelectable()).performClick()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { onAllNodesWithText("Tail · side").fetchSemanticsNodes().isNotEmpty() }
        val viewport = onNodeWithTag(SingleTags.VIEWPORT).fetchSemanticsNode().size
        val selection = assertNotNull(bench.selection)
        val looks = assertNotNull(bench.looks)
        val orbit = SingleCamera().apply { frame(selection.part.box, selection.part.axes, viewport.width, viewport.height) }
        val camera = orbit.lockedCamera(View.SIDE, selection, viewport.width, viewport.height, hold = false)

        strokeAndHoldWidth(SingleTags.VIEWPORT, bench, camera, tailStroke, DRAG_PIXELS)
        assertEquals(1, onAllNodesWithText("Side · writes Side and Top").fetchSemanticsNodes().size)
        assertEquals(2, onAllNodesWithText("to write").fetchSemanticsNodes().size, "Side and Top")
        assertEquals(emptyList(), assertNotNull(bench.state.spine.preview).unread, "a gentle tail is read whole")
        val tool = SpineWidthTool(bench.state, looks.spine)
        val overlay = paneOutlineOverlays(selection, mapOf(View.SIDE to camera), tool, looks.outline).getValue(View.SIDE)
        val errors = CopyOnWriteArrayList<String>()
        val image = renderSnapshot(
            singleFrame(SingleInput(viewport.width, viewport.height, looks.sheet, selection, camera, orbit.orbit.view, overlay)),
            viewport.width,
            viewport.height,
            options = GpuOptions(onError = errors::add),
        ) { ViewsScene(it) }
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, "the view")
        keepSingleWorkspace(image, "spine-single")
        val thumbnails = onNodeWithTag(SingleTags.THUMBNAILS).captureToImage().argbPixels()
        assertNotBlank(thumbnails, "the thumbnails")
        onNodeWithTag(SingleTags.VIEWPORT).performMouseInput { release() }
        waitForIdle()
        settled(bench)
        val outlines = bench.state.session.value?.document?.value?.character?.resolved(checkNotNull(bench.state.selectedPart.value))?.outlines
        assertEquals(setOf(View.SIDE, View.TOP), outlines?.keys)
    }

    private fun ComposeUiTest.panes(bench: Bench, name: String): SheetInput {
        val selection = assertNotNull(bench.selection)
        val looks = assertNotNull(bench.looks)
        val rects = paneRects()
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val framed = panesInput(selection, looks.sheet, rects, width, height)
        val tool = SpineWidthTool(bench.state, looks.spine)
        val overlays = paneOutlineOverlays(selection, framed.cameras, tool, looks.outline, active = View.SIDE)
        val input = SheetInput(rects, width, height, looks.sheet, selection, framed.cameras, framed.full, View.SIDE, overlays)
        val errors = CopyOnWriteArrayList<String>()
        val image = renderSnapshot(sheetFrame(input), width, height, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, "the panes")
        keepImage(image.pixels, image.width, name)
        return input
    }

    private fun sidePaneCamera(bench: Bench, rects: List<PixelRect>): dev.chieppa.squash.screens.sheet.LockedCamera {
        val selection = assertNotNull(bench.selection)
        val looks = assertNotNull(bench.looks)
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        return assertNotNull(panesInput(selection, looks.sheet, rects, width, height).cameras[View.SIDE])
    }

    private fun ComposeUiTest.paneRects(): List<PixelRect> {
        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        return bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
    }

    @Test
    fun theSheetShowsWhatItWillWriteInSideAndTop() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val bench = tailInWorkspace()
        val camera = sidePaneCamera(bench, paneRects())
        strokeAndHoldWidth(sheetPaneTag(SheetPane.SIDE), bench, camera, tailStroke, DRAG_PIXELS)
        val input = panes(bench, "spine-sheet")
        assertTrue(input.toolOverlays[View.TOP]?.lines.orEmpty().isNotEmpty(), "Top shows what it will write")
        onNodeWithText("Stabilizer").assertIsDisplayed()
        onNodeWithText("Snap to guides").assertIsDisplayed()
        keepImage(onNodeWithTag(ShellTags.WORKSPACE).captureToImage().argbPixels(), WIDTH, "spine-sheet-frame")
        onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).performMouseInput { release() }
        waitForIdle()
    }

    @Test
    fun aHardBendShowsThePartTheBuildWillNotRead() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val bench = tailInWorkspace()
        val camera = sidePaneCamera(bench, paneRects())
        strokeAndHoldWidth(sheetPaneTag(SheetPane.SIDE), bench, camera, bentStroke, DRAG_PIXELS)
        assertTrue(assertNotNull(bench.state.spine.preview).unread.isNotEmpty(), "the doubling back is marked")
        panes(bench, "spine-unread")
        onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).performMouseInput { release() }
        waitForIdle()
    }
}
