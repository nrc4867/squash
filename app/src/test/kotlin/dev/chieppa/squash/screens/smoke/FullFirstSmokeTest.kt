package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.sheet.SURFACE_REFUSED_TAG
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Tag
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val SETTLE_MILLIS = 30_000L

private object NoFullFirstFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The example fennec in the real workspace with Full first picked from the Layout control, its panes rendered where the real panes sit. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class FullFirstSmokeTest {
    @Test
    fun theFennecInFullFirstAndAnOutlineToolRefusedInFull() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = AppState(TemplateFolder.shipped(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        setStretchContent {
            SquashApp(state, NoFullFirstFiles)
            val selected = rememberSheetSelection(state)
            val paint = rememberSheetLook()
            SideEffect {
                selection = selected
                look = paint
            }
        }
        val session = runOnIdle { state.open(savedFennec()).also { state.screen.value = Screen.WORKSPACE } }
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && selection != null }
        runOnIdle { state.layout.value = SheetLayout.FULL_FIRST }
        waitForIdle()
        assertEquals(SheetLayout.FULL_FIRST, state.layout.value)
        assertEquals(BuildTool.DRAW_OUTLINE, state.tool.value)
        assertTrue(onAllNodesWithText("Surface strokes only").fetchSemanticsNodes().isEmpty(), "Full says so only when a tool is refused")

        val drawn = assertNotNull(selection)
        val paint = assertNotNull(look)
        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInWindow }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        val rects = bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val full = rects[SheetPane.FULL.ordinal]
        assertEquals(width, full.width, "Full runs across the panes")
        val sheets = listOf(SheetPane.TOP, SheetPane.SIDE, SheetPane.FRONT).map { rects[it.ordinal] }
        assertTrue(sheets.all { it.y >= full.bottom }, "the three sheets sit under it")
        assertEquals(sheets.sortedBy { it.x }, sheets, "Top, Side and Front in order")
        assertTrue(full.height > sheets.first().height, "Full is the large pane")

        val errors = CopyOnWriteArrayList<String>()
        val frame = sheetFrame(panesInput(drawn, paint, rects, width, height, layout = SheetLayout.FULL_FIRST))
        val image = renderSnapshot(frame, width, height, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, "the panes")
        val fill = paint.part.base
        assertNotEquals(0, image.countIn(rects[SheetPane.SIDE.ordinal], fill), "the selection fills Side")
        val inFull = image.countIn(full, paint.well)
        assertTrue(inFull < full.width * full.height, "the character shows in Full")
        keepPanesWorkspace(image, left.roundToInt(), top.roundToInt(), "full-first")

        val before = session.document.value
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(center)
            press()
        }
        waitForIdle()
        onNodeWithTag(SURFACE_REFUSED_TAG).assertExists()
        keepPanesWorkspace(image, left.roundToInt(), top.roundToInt(), "full-first-refused")
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput { release() }
        waitForIdle()
        onNodeWithTag(SURFACE_REFUSED_TAG).assertDoesNotExist()
        assertTrue(session.document.value === before, "a press in Full with an outline tool writes nothing")
    }
}
