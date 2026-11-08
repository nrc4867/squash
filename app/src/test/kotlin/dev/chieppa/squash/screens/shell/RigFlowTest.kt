package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.SetSeamStroke
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 60_000L
private const val WIDTH = 1440
private const val HEIGHT = 900

/** Where in Full's pane, as shares of its size, drags are tried until one starts on the character away from a joint's dot. */
private val TRIES = listOf(0.5f to 0.55f, 0.45f to 0.6f, 0.55f to 0.5f, 0.4f to 0.5f, 0.6f to 0.6f, 0.5f to 0.65f, 0.35f to 0.45f)
private const val DRAG_PIXELS = 30f
private const val DRAG_STEPS = 6

/** What one stroke paints: a seam, or both seams of a pair where the stroke lands on a paired part. */
private val PAIR_SEAMS_PAINTED = Regex("(^| )[12] seams painted")

private object NoRigFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Rig stage in the app whole, over the saved fennec: the key, its strip, a stroke from a pane, the status bar and the inspector's key. */
@OptIn(ExperimentalTestApi::class)
class RigFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), scope)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoRigFiles) }
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.session.value?.graph?.settled?.value == true }
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.key(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun fiveShowsRigAndADragLaysOneSeamStrokeAsOneStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        key(Key.Five)
        assertEquals(Stage.RIG, state.stage.value)
        assertEquals(BuildTool.PAINT_WEIGHT, state.tool.value, "Rig opens on Paint weight")
        for (tool in listOf(BuildTool.PAINT_WEIGHT, BuildTool.ERASE_WEIGHT, BuildTool.SMOOTH_WEIGHT)) onNodeWithTag(ShellTags.tool(tool)).assertExists()
        onNodeWithTag(ShellTags.tool(BuildTool.DRAW_OUTLINE)).assertDoesNotExist()
        key(Key.X)
        assertEquals(BuildTool.ERASE_WEIGHT, state.tool.value)
        key(Key.D)
        assertEquals(BuildTool.PAINT_WEIGHT, state.tool.value)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.rigNow() != null }
        waitForIdle()
        onNodeWithTag(ShellTags.STATUS_STAGE).onChildren().onFirst().assertTextContains("Rig · Paint weight", substring = true)
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("37 bones · 13 parts · 0 seams painted", substring = true)

        val steps = session.history.steps.value.done.size
        val pane = onNodeWithTag(sheetPaneTag(SheetPane.FULL))
        for ((x, y) in TRIES) {
            pane.performMouseInput {
                val from = Offset(width * x, height * y)
                moveTo(from)
                press()
                for (i in 1..DRAG_STEPS) moveTo(from + Offset(DRAG_PIXELS * i / DRAG_STEPS, 0f))
                release()
            }
            waitForIdle()
            if (session.history.steps.value.done.size > steps) break
        }
        assertEquals(steps + 1, session.history.steps.value.done.size, "one drag is one step")
        val laid = assertIs<SetSeamStroke>(session.history.steps.value.done.last())
        assertNotNull(laid.stroke)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value }
        waitForIdle()
        val status = onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertTrue(PAIR_SEAMS_PAINTED.containsMatchIn(status), "one stroke paints one seam, or both of a pair's: $status")

        onNodeWithText("Bones", ignoreCase = true, useUnmergedTree = true).assertExists()
        key(Key.Tab)
        onNodeWithText("Bones", ignoreCase = true, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(BuildTool.PAINT_WEIGHT, state.tool.value, "the inspector closing arms nothing")
        assertEquals(Stage.RIG, state.stage.value)
        state.undo()
        assertEquals(steps, session.history.steps.value.done.size)
    }
}
