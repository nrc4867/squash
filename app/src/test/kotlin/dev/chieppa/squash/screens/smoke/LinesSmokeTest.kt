package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.PaneColor
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.rememberPaneColor
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.SurfaceRibbons
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.surface.LinesDrawerTags
import dev.chieppa.squash.screens.surface.MarksDrawerTags
import dev.chieppa.squash.screens.surface.rememberSurfaceLines
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val SETTLE_MILLIS = 30_000L

private object NoLineFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The saved fennec opened in the app whole, with the Lines and Marks drawers, lines from the graph and strokes drawn through its tools. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class LinesSmokeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errors = CopyOnWriteArrayList<String>()

    private class Bench(val state: AppState) {
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        var lines: SurfaceRibbons? = null
        var color: PaneColor? = null
    }

    private fun ComposeUiTest.fennecOpen(): Bench {
        val bench = Bench(AppState(TemplateFolder.shipped(), scope))
        bench.state.open(savedFennec())
        bench.state.screen.value = Screen.WORKSPACE
        setStretchContent {
            SquashApp(bench.state, NoLineFiles)
            val selection = rememberSheetSelection(bench.state)
            val look = rememberSheetLook()
            val lines = rememberSurfaceLines(bench.state)
            val color = rememberPaneColor(bench.state)
            SideEffect {
                bench.selection = selection
                bench.look = look
                bench.lines = lines
                bench.color = color
            }
        }
        settle(bench)
        return bench
    }

    private fun ComposeUiTest.settle(bench: Bench) {
        waitUntil(timeoutMillis = SETTLE_MILLIS) {
            bench.state.session.value?.graph?.settled?.value == true && bench.selection != null && bench.lines != null
        }
        waitForIdle()
    }

    private fun ComposeUiTest.paneRects(): List<PixelRect> {
        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        return bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
    }

    /** The panes' frame as the running app makes it, its lines from the same ribbons. */
    private fun ComposeUiTest.panes(bench: Bench): SheetInput {
        val rects = paneRects()
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val framed = panesInput(assertNotNull(bench.selection), assertNotNull(bench.look), rects, width, height)
        val hints = SheetHints(whole = bench.state.tool.value.inks)
        return SheetInput(rects, width, height, framed.look, framed.selection, framed.cameras, framed.full, hints = hints, color = bench.color, surface = bench.lines)
    }

    /** Leaves the window with the panes rendered into their place, and the panes alone. */
    private fun ComposeUiTest.keep(bench: Bench, input: SheetInput, name: String) {
        val image = renderSnapshot(sheetFrame(input), input.width, input.height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        assertNotBlank(image.pixels, name)
        keepImage(image.pixels, image.width, name)
        val workspace = onNodeWithTag(ShellTags.WORKSPACE)
        val window = workspace.captureToImage().argbPixels()
        val windowWidth = workspace.fetchSemanticsNode().size.width
        val corner = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInWindow }
        window.paste(windowWidth, image.pixels, image.width, corner.minOf { it.left }.roundToInt(), corner.minOf { it.top }.roundToInt())
        keepImage(window, windowWidth, "$name-window")
    }

    @Test
    fun theFennecOpensWithThickAndThinLinesAndTheDrawersListThem() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val bench = fennecOpen()
        val state = bench.state
        val session = assertNotNull(state.session.value)
        onNodeWithContentDescription("Lines").performClick()
        waitForIdle()
        assertEquals(BuildTool.LINE_WIDTH, state.tool.value, "the Lines drawer brings its tools")
        onNodeWithTag(ShellTags.tool(BuildTool.LINE_WIDTH)).assertExists()
        onNodeWithTag(ShellTags.tool(BuildTool.SURFACE_STROKE)).assertExists()
        onNodeWithTag(LinesDrawerTags.LIVES_ON).assertExists()
        onNodeWithText("body surface").assertExists()
        onNodeWithContentDescription("Marks").performClick()
        waitForIdle()
        onNodeWithTag(MarksDrawerTags.fur(TORSO)).assertExists()
        val input = panes(bench)
        val full = input.rects[SheetPane.FULL.ordinal]
        val lines = assertNotNull(bench.lines)
        val camera = assertNotNull(input.full)
        val whole = lines.figure(FullView({ camera }, full.width, full.height))
        assertTrue(whole.map { it.rule.interiorPixels }.toSet().size >= 2, "Full has thick and thin lines: ${whole.map { it.rule.interiorPixels }.toSet()}")
        keep(bench, input, "lines-fennec")
        assertTrue(assertNotNull(session.document.value.character.part(TORSO)).id == TORSO)
        scope.cancel()
        assertEquals(emptyList(), errors)
    }

    @Test
    fun strokesDrawnInFullAndALockedPaneLandAsAnchors() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val bench = fennecOpen()
        val state = bench.state
        val session = assertNotNull(state.session.value)
        fun loose() = session.document.value.character.parts.flatMap { part -> session.document.value.character.resolved(part).surface.marks }.filter { it.kind != MarkKind.FUR && it.id.value.startsWith("stroke-") }
        onNodeWithContentDescription("Lines").performClick()
        waitForIdle()
        state.tool.value = BuildTool.SURFACE_STROKE
        waitForIdle()
        val input = panes(bench)
        val full = input.rects[SheetPane.FULL.ordinal]
        val fullCamera = assertNotNull(input.full)
        val middle = assertNotNull(FullView({ fullCamera }, full.width, full.height).toPixel(assertNotNull(bench.selection).part.center))
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(middle)
            press()
            repeat(STROKE_STEPS) { moveTo(middle + Offset((it + 1) * STROKE_STEP, it * STROKE_STEP / 3f)) }
            release()
        }
        waitForIdle()
        val fromFull = loose()
        assertEquals(1, fromFull.size, "the drag in Full stored one stroke")
        assertTrue(fromFull.single().points.size >= 2)
        val side = assertNotNull(input.rects[SheetPane.SIDE.ordinal])
        val camera = assertNotNull(input.cameras.values.first { it.view == dev.chieppa.squash.document.View.SIDE })
        val over = camera.toPixel(assertNotNull(bench.selection).part.center)
        onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).performMouseInput {
            moveTo(over)
            press()
            repeat(STROKE_STEPS) { moveTo(over + Offset((it + 1) * STROKE_STEP / 2f, 0f)) }
            release()
        }
        waitForIdle()
        assertEquals(2, loose().map { it.id }.toSet().size, "the drag in ${side.width} wide Side stored another")
        settle(bench)
        keep(bench, panes(bench), "lines-strokes")
        state.undo()
        assertEquals(1, loose().size, "one undo takes back one stroke")
        scope.cancel()
        assertEquals(emptyList(), errors)
    }

    private companion object {
        const val STROKE_STEPS = 12
        const val STROKE_STEP = 8f
    }
}
