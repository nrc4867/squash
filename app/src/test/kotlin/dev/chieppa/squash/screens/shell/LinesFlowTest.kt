package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.SetLineRules
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.BACK_WEIGHT
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.lines.PickedWeight
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.surface.MarksDrawerTags
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 30_000L
private const val WIDTH = 1440
private const val HEIGHT = 900

private object NoLineFlowFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Lines and Marks drawers and their tools in the app whole, over the saved fennec. */
@OptIn(ExperimentalTestApi::class)
class LinesFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), scope)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoLineFlowFiles) }
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.session.value?.graph?.settled?.value == true && state.session.value?.bodyLines?.output?.value != null }
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.press(key: Key) = onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }

    @Test
    fun aLineToolHoldsTheStripAndItsKeys() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        onNodeWithTag(ShellTags.tool(BuildTool.DRAW_OUTLINE)).assertExists()
        press(Key.W)
        assertEquals(BuildTool.SPINE_WIDTH, state.tool.value, "with no line tool armed W is Spine and width")
        onNodeWithContentDescription("Lines").performClick()
        waitForIdle()
        assertEquals(BuildTool.LINE_WIDTH, state.tool.value)
        onNodeWithTag(ShellTags.tool(BuildTool.SURFACE_STROKE)).assertExists()
        onNodeWithTag(ShellTags.tool(BuildTool.DRAW_OUTLINE)).assertDoesNotExist()
        press(Key.D)
        assertEquals(BuildTool.SURFACE_STROKE, state.tool.value, "with it D is Surface stroke")
        press(Key.W)
        assertEquals(BuildTool.LINE_WIDTH, state.tool.value, "and W is Line width")
        onNodeWithText("Silhouette").assertIsDisplayed()
        onNodeWithText("Taper").assertIsDisplayed()
        onNodeWithText("body surface").assertIsDisplayed()
        onNodeWithContentDescription("Lines").performClick()
        waitForIdle()
        assertEquals(BuildTool.DRAW_OUTLINE, state.tool.value, "closing it leaves the surface tools")
    }

    @Test
    fun theMarksDrawerListsRulesWeightsStrokesAndFur() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        onNodeWithContentDescription("Marks").performClick()
        waitForIdle()
        onNodeWithTag(MarksDrawerTags.SILHOUETTE).assertExists()
        onNodeWithTag(MarksDrawerTags.strokes(HEAD)).assertExists()
        onNodeWithTag(MarksDrawerTags.fur(TORSO)).assertExists()
        onNodeWithText("+60%").assertExists()
        onNodeWithText("Saddle weight").performClick()
        assertEquals(PickedWeight(TORSO, BACK_WEIGHT), state.lines.picked)
        session.send(SetLineRules(LineRules(silhouette = 5f)))
        waitForIdle()
        onNodeWithText("5.0 px").assertExists()
        onNodeWithTag(MarksDrawerTags.SILHOUETTE).performClick()
        waitForIdle()
        onNodeWithText("Taper").assertExists()
    }

    @Test
    fun aDragWithLineWidthStoresOneWeightThroughOneUndoStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        onNodeWithContentDescription("Lines").performClick()
        waitForIdle()
        val before = session.document.value
        val full = onNodeWithTag(sheetPaneTag(SheetPane.FULL))
        val size = full.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f + DRAG_BELOW)
        full.performMouseInput {
            moveTo(middle)
            press()
            repeat(DRAG_STEPS) { moveTo(middle + Offset((it + 1) * DRAG_STEP, 0f)) }
            release()
        }
        waitForIdle()
        val character = session.document.value.character
        val added = character.parts.flatMap { character.resolved(it).surface.lineWeights }.filter { it.id != BACK_WEIGHT }
        assertTrue(added.isNotEmpty(), "the drag laid a weight")
        assertEquals(setOf(added.first().id), added.map { it.id }.toSet())
        assertEquals(state.lines.amount, added.first().amount)
        assertEquals(state.lines.picked?.id, added.first().id)
        state.undo()
        assertEquals(before, session.document.value, "one undo takes it back")
    }

    private companion object {
        const val DRAG_BELOW = 30f
        const val DRAG_STEPS = 10
        const val DRAG_STEP = 8f
    }
}
