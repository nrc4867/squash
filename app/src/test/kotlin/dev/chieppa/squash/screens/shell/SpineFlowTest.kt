package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.draw.OutlineDrawerTags
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.spine.SpineDrawerTags
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 20_000L
private const val STROKE_POINTS = 40
private const val STROKE_REACH = 160f
private const val DRAG_PIXELS = 6f

private object NoSpineDialogs : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** Spine and width from the key that arms it, in the whole workspace. */
@OptIn(ExperimentalTestApi::class)
class SpineFlowTest {
    private fun ComposeUiTest.workspace(state: AppState) {
        setStretchContent { SquashApp(state, NoSpineDialogs) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.W) }
        waitForIdle()
    }

    private fun ComposeUiTest.strokeInSide() {
        val side = onNodeWithTag(sheetPaneTag(SheetPane.SIDE))
        val size = side.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)
        side.performMouseInput {
            moveTo(middle - Offset(STROKE_REACH / 2f, 0f))
            press()
            for (i in 1..STROKE_POINTS) moveTo(middle + Offset(STROKE_REACH * (i.toFloat() / STROKE_POINTS - 0.5f), 0f))
            release()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.settled(state: AppState) =
        waitUntil(timeoutMillis = SETTLE_MILLIS) { checkNotNull(state.session.value).graph.settled.value }

    private fun ComposeUiTest.statusEnd(text: String) = onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains(text)

    @Test
    fun theKeyArmsTheToolAndTheDrawerReadsAsTheBoardDrawsIt() = runDesktopComposeUiTest(1900, 900) {
        val state = AppState(shipped)
        workspace(state)
        assertEquals(BuildTool.SPINE_WIDTH, state.tool.value)
        onNodeWithTag(ShellTags.tool(BuildTool.SPINE_WIDTH)).assertExists()
        onNodeWithText("Spine and width").assertExists()
        onNodeWithText("STROKE").assertExists()
        onNodeWithText("WIDTH").assertExists()
        onNodeWithText("HINT").assertExists()
        onNodeWithText("PEN").assertDoesNotExist()
        onNodeWithText("OUTLINES").assertExists()
        onNodeWithText("Pressure").assertDoesNotExist()
        onNodeWithTag(SpineDrawerTags.WIDTH).assertTextContains("none")
        onNodeWithText("Smooth").assertExists()
        onNodeWithText("Stabilizer").assertExists()
        onNodeWithTag(OutlineDrawerTags.STABILIZER_VALUE).assertTextContains("20%")
        onNodeWithText("Snap to guides").assertExists()
        assertEquals(0, onAllNodesWithText("Hold to snap").fetchSemanticsNodes().size, "Draw outline's alone")
    }

    @Test
    fun theDrawersRowsShareDrawOutlinesSettings() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped)
        workspace(state)
        onNodeWithTag(OutlineDrawerTags.SNAP_GUIDES).performClick()
        waitForIdle()
        assertFalse(state.curves.snapGuides)
        state.curves.stabilizer = 0.5f
        waitForIdle()
        onNodeWithTag(OutlineDrawerTags.STABILIZER_VALUE).assertTextContains("50%")
    }

    @Test
    fun aStrokeInHandShowsInTheStatusBarAndTheOutlineRowsAndItsWidthWrites() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped)
        workspace(state)
        strokeInSide()
        statusEnd("Side · writes Side and Top")
        assertEquals(2, onAllNodesWithText("to write").fetchSemanticsNodes().size, "Side and Top")
        assertNotNull(state.spine.widthPixels)

        val session = checkNotNull(state.session.value)
        val before = session.history.steps.value.done.size
        val side = onNodeWithTag(sheetPaneTag(SheetPane.SIDE))
        val size = side.fetchSemanticsNode().size
        val onSpine = Offset(size.width / 2f, size.height / 2f)
        side.performMouseInput {
            moveTo(onSpine)
            press()
            moveTo(onSpine + Offset(0f, DRAG_PIXELS))
            release()
        }
        waitForIdle()
        settled(state)
        assertEquals(before + 1, session.history.steps.value.done.size)
        statusEnd("2 drawn · 1 from template")
        assertFalse(state.spine.active)
        session.history.undo()
        waitForIdle()
        settled(state)
        statusEnd("0 drawn · 3 from template")
    }

    @Test
    fun anotherToolDropsTheStrokeInHand() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped)
        workspace(state)
        strokeInSide()
        assertTrue(state.spine.active)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.E) }
        waitForIdle()
        assertFalse(state.spine.active)
        assertEquals(null, state.curves.drawing)
    }

    @Test
    fun theDrawerIsTheToolsWhateverThePartHolds() {
        assertEquals(PartDrawerKind.SPINE_WIDTH, partDrawerKind(BuildTool.SPINE_WIDTH, hasPart = true, drawn = false))
        assertEquals(PartDrawerKind.SPINE_WIDTH, partDrawerKind(BuildTool.SPINE_WIDTH, hasPart = true, drawn = true))
        assertEquals(PartDrawerKind.PART, partDrawerKind(BuildTool.SPINE_WIDTH, hasPart = false, drawn = false))
    }
}
