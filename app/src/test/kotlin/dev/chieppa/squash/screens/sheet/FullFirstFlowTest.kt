package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.move.SocketHandle
import dev.chieppa.squash.screens.move.moveHandles
import dev.chieppa.squash.screens.move.rememberMoveTool
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.test.setStretchContent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val RADIUS = 30f
private const val LOOP_POINTS = 40
private const val DRAG_PIXELS = 30f
private const val PANES_WIDTH = 1000
private const val PANES_HEIGHT = 800

private object NoFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** Full first with the real tools: Draw outline refused in Full and writing in a sheet, Move in Full, and the switch to Single and back. */
@OptIn(ExperimentalTestApi::class)
class FullFirstFlowTest {
    private val work = TestWork()

    private fun ComposeUiTest.workspaceOnALeg(state: AppState): CharacterSession {
        setStretchContent { SquashApp(state, NoFiles) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        onNodeWithTag(ShellTags.ADD_PART_ADD).performClick()
        settle(work, state)
        return assertNotNull(state.session.value)
    }

    private fun ComposeUiTest.pickLayout(label: String) {
        onNodeWithTag(ShellTags.LAYOUT).performClick()
        waitForIdle()
        onAllNodesWithText(label).onLast().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.loopIn(pane: SheetPane) {
        val node = onNodeWithTag(sheetPaneTag(pane))
        val size = node.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)
        node.performMouseInput {
            moveTo(middle + Offset(RADIUS, 0f))
            press()
            for (i in 1..LOOP_POINTS) {
                val angle = 2.0 * PI * i / LOOP_POINTS
                moveTo(middle + Offset(RADIUS * cos(angle).toFloat(), 2f * RADIUS * sin(angle).toFloat()))
            }
            release()
        }
        waitForIdle()
    }

    @Test
    fun drawOutlineWritesNothingInFullAndAnOutlineInASheetOfFullFirst() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped, work)
        val session = workspaceOnALeg(state)
        pickLayout("Full first")
        settle(work, state)
        assertEquals(SheetLayout.FULL_FIRST, state.layout.value)
        val leg = assertNotNull(state.selectedPart.value)

        val before = session.document.value
        loopIn(SheetPane.FULL)
        assertSame(before, session.document.value, "the free pane takes no outline")

        loopIn(SheetPane.SIDE)
        settle(work) { session.graph.settled.value }
        val character = session.document.value.character
        assertTrue(View.SIDE in character.resolved(assertNotNull(character.part(leg))).outlines, "a sheet takes it")
        assertEquals(leg, state.selectedPart.value)
    }

    @Test
    fun aSwitchToSingleAndBackKeepsTheSelectionAndTheOutlines() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped, work)
        val session = workspaceOnALeg(state)
        pickLayout("Full first")
        waitForIdle()
        loopIn(SheetPane.SIDE)
        val leg = state.selectedPart.value
        val document = session.document.value
        pickLayout("Single")
        waitForIdle()
        assertEquals(leg, state.selectedPart.value)
        pickLayout("Full first")
        waitForIdle()
        assertEquals(leg, state.selectedPart.value)
        assertSame(document, session.document.value)
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).assertExists()
    }

    @Test
    fun moveDragsASocketsDotInTheFreePaneOfFullFirst() = runComposeUiTest {
        val state = AppState(shipped, work)
        state.createCharacter(shipped.plans.first(), "Fennec")
        state.layout.value = SheetLayout.FULL_FIRST
        val session = assertNotNull(state.session.value)
        var fullView: FullView? = null
        val noSurface: SceneSurfaceContent = { _, _ -> }
        setStretchContent {
            val move = rememberMoveTool(state)
            val tool = object : PaneTool by move {
                override fun fullOverlay(view: FullView): ViewportOverlay {
                    fullView = view
                    return move.fullOverlay(view)
                }
            }
            CompositionLocalProvider(LocalSceneSurface provides noSurface) {
                Box(Modifier.size(PANES_WIDTH.dp, PANES_HEIGHT.dp)) { SheetPanes(state, Modifier.fillMaxSize(), tool = tool) }
            }
        }
        settle(work) { session.graph.settled.value && session.assembly.output.value != null && fullView != null }
        waitForIdle()
        val view = assertNotNull(fullView)
        val pane = onNodeWithTag(sheetPaneTag(SheetPane.FULL)).fetchSemanticsNode().size
        val handle = moveHandles(assertNotNull(session.assembly.output.value), state.selectedPart.value)
            .filterIsInstance<SocketHandle>()
            .first { handle ->
                val at = view.toPixel(handle.position)
                at != null && at.x in DRAG_PIXELS..(pane.width - DRAG_PIXELS) && at.y in DRAG_PIXELS..(pane.height - DRAG_PIXELS)
            }
        val at = assertNotNull(view.toPixel(handle.position))
        val before = session.document.value
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(at)
            press()
            moveBy(Offset(DRAG_PIXELS, 0f))
            moveBy(Offset(0f, DRAG_PIXELS / 2))
            release()
        }
        waitForIdle()
        assertNotSame(before, session.document.value, "the dot's drag writes through the free pane")
    }
}
