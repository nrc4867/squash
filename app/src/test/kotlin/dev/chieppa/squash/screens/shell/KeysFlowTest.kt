package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.PaneNav
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH = 1600
private const val HEIGHT = 900

/** Where across Full the presses land, as shares of its width. */
private val PRESS_ACROSS = listOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)

private object NoKeysFlowFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The keys that mean the same in every stage, in the app whole over the saved fennec. */
@OptIn(ExperimentalTestApi::class)
class KeysFlowTest {
    private val work = TestWork()

    @AfterTest
    fun stop() = work.close()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoKeysFlowFiles) }
        settle(work, state)
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.press(key: Key) = onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }

    @Test
    fun fFitsThePaneUnderThePointerAlone() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        state.activeView.value = View.SIDE
        val zoomed = PaneNav(zoom = 2f)
        for (view in View.entries) state.paneNavigation.update(view) { zoomed }
        val top = onNodeWithTag(sheetPaneTag(SheetPane.TOP))
        top.performMouseInput { moveTo(center) }
        waitForIdle()
        val hovered = assertNotNull(state.hoveredView.value, "a locked pane names its view while the pointer is over it")
        press(Key.F)
        waitForIdle()
        assertEquals(PaneNav(), state.paneNavigation[hovered])
        for (view in View.entries - hovered) assertEquals(zoomed, state.paneNavigation[view], "$view keeps its zoom")
    }

    @Test
    fun escPutsTheToolDownAndAPressThenSelectsThePart() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        state.selectedPart.value = HEAD
        press(Key.Escape)
        waitForIdle()
        assertEquals(BuildTool.NONE, state.tool.value)
        assertEquals(Stage.SHAPE, state.stage.value, "the stage stays on screen")
        val full = onNodeWithTag(sheetPaneTag(SheetPane.FULL))
        val size = full.fetchSemanticsNode().size
        val picked = PRESS_ACROSS.mapTo(mutableSetOf()) { share ->
            state.selectedPart.value = HEAD
            waitForIdle()
            full.performMouseInput { click(Offset(size.width * share, size.height / 2f)) }
            waitForIdle()
            assertNotNull(state.selectedPart.value)
        }
        assertTrue(picked.any { it != HEAD }, "a press on another part selects it, of $picked")
        assertEquals(BuildTool.NONE, state.tool.value)
    }
}
