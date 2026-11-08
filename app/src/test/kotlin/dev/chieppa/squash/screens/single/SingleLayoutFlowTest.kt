package dev.chieppa.squash.screens.single

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val RADIUS = 60f
private const val LOOP_POINTS = 60

/** A middle drag long enough to leave any snap: the orbit turns 0.008 radians a pixel. */
private const val ORBIT_PIXELS = 80f

private object NoFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

@OptIn(ExperimentalTestApi::class)
class SingleLayoutFlowTest {
    private val work = TestWork()

    private fun ComposeUiTest.workspaceOnALeg(state: AppState): CharacterSession {
        setStretchContent { SquashApp(state, NoFiles) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        onNodeWithTag(ShellTags.ADD_PART_ADD).performClick()
        waitForIdle()
        return assertNotNull(state.session.value)
    }

    /** A segment of a control in the view, which unlike the inspector's rows carries no tag. */
    private fun ComposeUiTest.segment(label: String) =
        onNode(hasText(label) and isSelectable() and SemanticsMatcher("has no tag") { SemanticsProperties.TestTag !in it.config })

    private fun ComposeUiTest.pickLayout(label: String) {
        onNodeWithTag(ShellTags.LAYOUT).performClick()
        waitForIdle()
        onAllNodesWithText(label).onLast().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.pickSingle() {
        pickLayout("Single")
        settle(work) { onAllNodesWithText("Front leg · L · side").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForHud(view: String) {
        settle(work) { onAllNodesWithText("Front leg · L · $view").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.stroke() {
        val viewport = onNodeWithTag(SingleTags.VIEWPORT)
        val size = viewport.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)
        viewport.performMouseInput {
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

    private fun ComposeUiTest.orbit(dx: Float) {
        val viewport = onNodeWithTag(SingleTags.VIEWPORT)
        val size = viewport.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)
        viewport.performMouseInput {
            moveTo(middle)
            press(MouseButton.Tertiary)
            moveTo(middle + Offset(dx / 2f, 0f))
            moveTo(middle + Offset(dx, 0f))
            release(MouseButton.Tertiary)
        }
        waitForIdle()
    }

    private fun outlines(state: AppState, session: CharacterSession) =
        assertNotNull(session.document.value.character.resolved(assertNotNull(state.selectedPart.value))).outlines

    @Test
    fun theLayoutControlOffersItsBuiltLayoutsAndTheChoiceIsNotInTheDocument() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped, work)
        val session = workspaceOnALeg(state)
        settle(work) { session.graph.settled.value }
        val before = session.document.value

        pickSingle()
        assertEquals(SheetLayout.SINGLE, state.layout.value)
        onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).assertDoesNotExist()
        onNodeWithTag(SingleTags.TOOLBAR).assertExists()
        onNodeWithTag(ShellTags.tool(BuildTool.DRAW_OUTLINE)).assertExists()
        onNodeWithTag(SingleTags.THUMBNAILS).assertExists()
        segment("Side").assertIsSelected()

        pickLayout("Sheet")
        waitForIdle()
        onNodeWithTag(SingleTags.VIEWPORT).assertDoesNotExist()
        onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).assertExists()
        assertSame(before, session.document.value, "switching layouts changes nothing in the document")
    }

    @Test
    fun aStrokeInSingleProducesAnOutlineOnTheViewSnappedTo() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped, work)
        val session = workspaceOnALeg(state)
        settle(work) { session.graph.settled.value }
        pickSingle()

        stroke()
        settle(work) { View.SIDE in outlines(state, session) }
        assertEquals(setOf(View.SIDE), outlines(state, session).keys)
        settle(work) { session.graph.settled.value }
        onNodeWithText("Front leg · L · side").assertExists()
        onNodeWithText("Side · 1 of 3 views").assertExists()
        onNodeWithText("SIDE · ", substring = true).assertTextContains(" PX", substring = true)

        state.undo()
        waitForIdle()
        assertTrue(outlines(state, session).isEmpty(), "the stroke is one undo step")
    }

    @Test
    fun drawingWorksOnlyWhileSnapped() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped, work)
        val session = workspaceOnALeg(state)
        settle(work) { session.graph.settled.value }
        pickSingle()

        orbit(ORBIT_PIXELS)
        waitForHud("free orbit")
        for (label in listOf("Side", "Top", "Front")) segment(label).assertIsNotSelected()
        val before = session.document.value
        stroke()
        assertSame(before, session.document.value, "a free camera takes no stroke")

        orbit(-ORBIT_PIXELS)
        waitForHud("side")
        segment("Side").assertIsSelected()
        stroke()
        settle(work) { View.SIDE in outlines(state, session) }
    }

    @Test
    fun aMissingViewsThumbnailAndSnapViewBothTurnTheCameraThere() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped, work)
        val session = workspaceOnALeg(state)
        settle(work) { session.graph.settled.value }
        pickSingle()

        onNodeWithTag(SingleTags.thumbnail(View.TOP)).performClick()
        waitForHud("front")
        segment("Front").assertIsSelected()
        stroke()
        settle(work) { View.TOP in outlines(state, session) }
        assertEquals(setOf(View.TOP), outlines(state, session).keys)

        segment("Top").performClick()
        waitForHud("top")
        assertEquals(View.FRONT, state.activeView.value, "looking down a leg shows its Front outline")
        segment("Side").performClick()
        waitForHud("side")
    }

    @Test
    fun pressingTheSnapViewTheCameraIsOnLooksFromItsOtherSide() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped, work)
        val session = workspaceOnALeg(state)
        settle(work) { session.graph.settled.value }
        pickSingle()
        waitForHud("side")
        segment("Side").performClick()
        waitForHud("far side")
        segment("Side").assertIsSelected()
        segment("Side").performClick()
        settle(work) { onAllNodesWithText("far side", substring = true).fetchSemanticsNodes().isEmpty() }
        waitForHud("side")
    }

    @Test
    fun theLayoutOutlivesTheCharacter() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped, work)
        val session = workspaceOnALeg(state)
        settle(work) { session.graph.settled.value }
        pickSingle()
        runOnIdle { state.open(session.document.value) }
        waitForIdle()
        assertEquals(SheetLayout.SINGLE, state.layout.value)
        onNodeWithTag(SingleTags.VIEWPORT).assertExists()
    }
}
