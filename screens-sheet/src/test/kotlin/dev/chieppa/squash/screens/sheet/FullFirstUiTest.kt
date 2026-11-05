package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.test.setStretchContent
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 20_000L
private const val PANES_WIDTH = 1000
private const val PANES_HEIGHT = 800

/** How close a surface hit comes back to the pixel it was found under. */
private const val HIT_PIXELS = 1f
private const val DRAG_PIXELS = 40f

/** An outline tool as Full sees one: it asks for no free camera, and counts anything that reaches it. */
private class LockedOnlyTool : PaneTool {
    var fullInputs = 0
    val pressedIn = mutableListOf<View>()
    var fullView: FullView? = null

    override fun pointerInput(view: View, camera: LockedCamera): Modifier =
        Modifier.pointerInput(view) { detectTapGestures { pressedIn += view } }

    override fun fullInput(view: FullView): Modifier {
        fullInputs++
        return Modifier
    }

    override fun fullOverlay(view: FullView): ViewportOverlay {
        fullView = view
        return ViewportOverlay.Empty
    }
}

@OptIn(ExperimentalTestApi::class)
class FullFirstUiTest {
    private val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))
    private val noSurface: SceneSurfaceContent = { _, _ -> }

    private fun opened(layout: SheetLayout = SheetLayout.FULL_FIRST) = AppState(folder).also {
        it.createCharacter(folder.plans.first(), "Fennec")
        it.layout.value = layout
    }

    private fun ComposeUiTest.show(state: AppState, tool: PaneTool) {
        setStretchContent {
            CompositionLocalProvider(LocalSceneSurface provides noSurface) {
                Box(Modifier.size(PANES_WIDTH.dp, PANES_HEIGHT.dp)) { SheetPanes(state, Modifier.fillMaxSize(), tool = tool) }
            }
        }
    }

    private fun ComposeUiTest.settle(state: AppState, view: () -> FullView?) {
        val session = assertNotNull(state.session.value)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && session.assembly.output.value != null && view() != null }
        waitForIdle()
    }

    private fun parts(state: AppState): List<SheetPart> =
        state.session.value?.assembly?.output?.value?.parts.orEmpty().filter { it.box != null }.map(::SheetPart)

    private fun placed(state: AppState): SheetPart = parts(state).first { !it.planned }

    /** The pixel in Full over the middle of the placed part. */
    private fun overThePart(state: AppState, view: FullView): Offset = assertNotNull(view.toPixel(placed(state).center))

    private fun ComposeUiTest.full() = onNodeWithTag(sheetPaneTag(SheetPane.FULL))

    @Test
    fun aPressInTheFreePaneFindsTheSurfaceByRayThroughTheOrbitCamera() = runComposeUiTest {
        val state = opened()
        val tool = RaySurfaceTool { parts(state) }
        show(state, tool)
        settle(state) { tool.fullView }
        val view = assertNotNull(tool.fullView)

        val at = overThePart(state, view)
        full().performMouseInput { click(at) }
        waitForIdle()
        val hit = assertNotNull(tool.hits.firstOrNull(), "the ray meets the part")
        assertEquals(at.x, assertNotNull(view.toPixel(hit)).x, HIT_PIXELS)
        assertEquals(at.y, assertNotNull(view.toPixel(hit)).y, HIT_PIXELS)

        tool.followed.clear()
        full().performMouseInput { click(Offset(2f, 2f)) }
        waitForIdle()
        assertTrue(tool.followed.isNotEmpty(), "a press off the character still reaches the tool")
        assertTrue(tool.hits.isEmpty(), "and meets nothing")
    }

    @Test
    fun aMiddleDragOrbitsAndALeftDragStrokesAndNeitherDoesTheOthersWork() = runComposeUiTest {
        val state = opened()
        val tool = RaySurfaceTool { parts(state) }
        show(state, tool)
        settle(state) { tool.fullView }
        val view = assertNotNull(tool.fullView)
        val at = overThePart(state, view)
        val before = view.toViewer(Vec3.ZERO)

        full().performMouseInput {
            moveTo(at)
            press()
            moveBy(Offset(DRAG_PIXELS / 4, 0f))
            moveBy(Offset(DRAG_PIXELS / 4, 0f))
            release()
        }
        waitForIdle()
        assertTrue(tool.followed.size >= 2, "the left drag strokes: ${tool.followed.size}")
        assertEquals(before, view.toViewer(Vec3.ZERO), "and the camera stays")

        tool.followed.clear()
        full().performMouseInput {
            moveTo(at)
            press(MouseButton.Tertiary)
            moveBy(Offset(DRAG_PIXELS, 0f))
            release(MouseButton.Tertiary)
        }
        waitForIdle()
        assertEquals(emptyList(), tool.followed, "the middle drag reaches no tool")
        assertNotEquals(before, view.toViewer(Vec3.ZERO), "and it orbits")

        val turned = overThePart(state, view)
        full().performMouseInput { click(turned) }
        waitForIdle()
        val hit = assertNotNull(tool.hits.firstOrNull(), "after the orbit the ray goes through the new camera")
        assertEquals(turned.x, assertNotNull(view.toPixel(hit)).x, HIT_PIXELS)
    }

    @Test
    fun anOutlineToolIsRefusedInTheFreePaneWithTheChipLitAndWorksInTheSheets() = runComposeUiTest {
        val state = opened()
        val tool = LockedOnlyTool()
        show(state, tool)
        settle(state) { tool.fullView }
        onNodeWithText("Surface strokes only").assertDoesNotExist()
        onNodeWithTag(SURFACE_REFUSED_TAG).assertDoesNotExist()

        full().performMouseInput {
            moveTo(center)
            press()
        }
        waitForIdle()
        onNodeWithTag(SURFACE_REFUSED_TAG).assertExists()
        assertEquals(0, tool.fullInputs, "the pane never hands it the free camera")
        full().performMouseInput { release() }
        waitForIdle()
        onNodeWithTag(SURFACE_REFUSED_TAG).assertDoesNotExist()

        full().performMouseInput {
            moveTo(center)
            press(MouseButton.Tertiary)
        }
        waitForIdle()
        onNodeWithTag(SURFACE_REFUSED_TAG).assertDoesNotExist()
        full().performMouseInput { release(MouseButton.Tertiary) }

        onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).performMouseInput { click() }
        waitForIdle()
        assertEquals(listOf(placed(state).axes.framing.getValue(BodyView.SIDE)), tool.pressedIn)
    }

    @Test
    fun aSwitchBetweenSheetAndFullFirstKeepsTheSelectionAndEachPanesState() = runComposeUiTest {
        val state = opened(SheetLayout.SHEET)
        val tool = RaySurfaceTool { parts(state) }
        show(state, tool)
        settle(state) { tool.fullView }
        full().performMouseInput {
            moveTo(center)
            press(MouseButton.Tertiary)
            moveBy(Offset(DRAG_PIXELS, 0f))
            release(MouseButton.Tertiary)
        }
        onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).performMouseInput {
            moveTo(center)
            scroll(-2f)
        }
        waitForIdle()
        val selected = state.selectedPart.value
        val zoomed = state.paneNavigation[View.SIDE]
        assertTrue(zoomed.zoom > 1f, "Side zooms")
        val turned = assertNotNull(tool.fullView).toViewer(Vec3.ZERO)
        val sheetFullHeight = assertNotNull(tool.fullView).height

        for (layout in listOf(SheetLayout.FULL_FIRST, SheetLayout.SHEET, SheetLayout.FULL_FIRST)) {
            state.layout.value = layout
            waitForIdle()
            assertEquals(selected, state.selectedPart.value, "$layout keeps the selection")
            assertEquals(zoomed, state.paneNavigation[View.SIDE], "$layout keeps Side's zoom")
            val view = assertNotNull(tool.fullView)
            assertEquals(turned, view.toViewer(Vec3.ZERO), "$layout keeps Full's orbit")
            assertEquals(full().fetchSemanticsNode().size.width, view.width, "$layout gives Full's view the pane's size")
            assertEquals(full().fetchSemanticsNode().size.height, view.height)
            if (layout == SheetLayout.FULL_FIRST) assertTrue(view.height > sheetFullHeight, "Full is the large pane")
        }
    }
}
