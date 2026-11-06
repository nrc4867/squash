package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performScrollTo
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
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.BACK_WEIGHT
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.lines.PickedMark
import dev.chieppa.squash.screens.lines.PickedWeight
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.surface.MarksDrawerTags
import dev.chieppa.squash.screens.surface.LinesDrawerTags
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
import dev.chieppa.squash.screens.shell.Stage
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText

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
        assertEquals(BuildTool.SPINE_WIDTH, state.tool.value, "in Shape W is Spine and width")
        press(Key.Three)
        waitForIdle()
        assertEquals(Stage.LINES, state.stage.value)
        assertEquals(BuildTool.LINE_WIDTH, state.tool.value)
        onNodeWithTag(ShellTags.tool(BuildTool.SURFACE_STROKE)).assertExists()
        onNodeWithTag(ShellTags.tool(BuildTool.DRAW_OUTLINE)).assertDoesNotExist()
        press(Key.D)
        assertEquals(BuildTool.SURFACE_STROKE, state.tool.value, "in Lines D is Stroke")
        onNodeWithTag(LinesDrawerTags.STROKE_COLOR).assertExists()
        onNodeWithText("Floating").performClick()
        waitForIdle()
        assertTrue(state.lines.floating, "Stroke draws floating from its option")
        press(Key.C)
        assertEquals(BuildTool.COMB, state.tool.value, "C is Comb")
        press(Key.E)
        assertEquals(BuildTool.LINE_EDIT, state.tool.value, "E is Edit curves")
        press(Key.L)
        assertEquals(BuildTool.LINE_EDIT, state.tool.value, "L arms nothing")
        press(Key.W)
        assertEquals(BuildTool.LINE_WIDTH, state.tool.value, "and W is Line width")
        onNodeWithText("Silhouette").assertIsDisplayed()
        onNodeWithText("Taper").assertIsDisplayed()
        onNodeWithContentDescription("Inspector").performClick()
        waitForIdle()
        assertEquals(BuildTool.LINE_WIDTH, state.tool.value, "closing the inspector changes no tool")
        press(Key.One)
        waitForIdle()
        assertEquals(BuildTool.SPINE_WIDTH, state.tool.value, "Shape comes back holding the tool it was left with")
    }

    @Test
    fun theLinesInspectorListsStrokesWeightsAndFur() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        press(Key.Three)
        waitForIdle()
        val stroke = assertNotNull(session.document.value.character.resolved(HEAD)).surface.marks.first { it.kind == MarkKind.LINE && it.id !in assertNotNull(session.document.value.character.resolved(HEAD)).surface.groups.flatMap { g -> g.roles.flatMap { r -> r.marks } } }.id
        onNodeWithTag(MarksDrawerTags.stroke(stroke)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(PickedMark(HEAD, stroke), state.lines.pickedMark, "a row picks its stroke")
        onNodeWithTag(MarksDrawerTags.STROKE_MIRROR).performScrollTo().performClick()
        waitForIdle()
        fun mark() = assertNotNull(session.document.value.character.resolved(HEAD)).surface.marks.firstOrNull { it.id == stroke }
        assertEquals(true, mark()?.mirrored, "the head lies in the middle, so it can mirror")
        onNodeWithTag(MarksDrawerTags.STROKE_DELETE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(null, mark())
        session.history.undo()
        session.history.undo()
        waitForIdle()
        onNodeWithTag(MarksDrawerTags.fur(TORSO)).performScrollTo().assertExists()
        onAllNodesWithText("+60%").onFirst().assertExists()
        onNodeWithText("Saddle weight").performClick()
        assertEquals(PickedWeight(TORSO, BACK_WEIGHT), state.lines.picked)
        onNodeWithTag(MarksDrawerTags.WEIGHT_AMOUNT).assertExists()
        onNodeWithTag(MarksDrawerTags.fur(TORSO)).performClick()
        waitForIdle()
        onNodeWithText("Taper").assertExists()
    }

    @Test
    fun aDragWithLineWidthStoresOneWeightThroughOneUndoStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        press(Key.Three)
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
