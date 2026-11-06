package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.references.ReferencesTags
import dev.chieppa.squash.screens.references.TraceTags
import dev.chieppa.squash.screens.references.decodeImage
import dev.chieppa.squash.screens.references.newImageCommand
import dev.chieppa.squash.screens.references.sketchPng
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val SETTLE_MILLIS = 30_000L
private const val WIDTH = 1440
private const val HEIGHT = 900

private object NoReferenceFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The References, Trace and Place image drawers and the Focus layout in the app whole, over the saved fennec. */
@OptIn(ExperimentalTestApi::class)
class ReferencesFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), scope)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoReferenceFiles) }
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.session.value?.graph?.settled?.value == true }
        waitForIdle()
        return state
    }

    private fun AppState.addSketch() {
        val session = assertNotNull(session.value)
        val png = sketchPng()
        val decoded = assertNotNull(decodeImage(png))
        session.send(newImageCommand(session.document.value, "Side sheet", png, decoded.width, decoded.height))
    }

    @Test
    fun anImageOnTheBoardTracesInSideAndIsPlacedThere() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        state.addSketch()
        val image = session.document.value.references.images.single()
        onNodeWithContentDescription("References").performClick()
        waitForIdle()
        onNodeWithText("1 image").assertExists()
        onNodeWithTag(ReferencesTags.image(image.id)).performClick()
        waitForIdle()
        assertEquals(image.id, state.references.picked)
        onNodeWithTag(ReferencesTags.TRACE_IN).assertExists()
        onNodeWithText("Side").performClick()
        waitForIdle()
        val trace = assertNotNull(session.document.value.character.traces[View.SIDE])
        assertEquals(image.id, trace.image)
        assertTrue(trace.locked, "a new trace stays put")

        onNodeWithContentDescription("Trace").performClick()
        waitForIdle()
        onNodeWithTag(TraceTags.row(View.SIDE)).performClick()
        onNodeWithTag(TraceTags.PLACE).performClick()
        waitForIdle()
        assertEquals(BuildTool.PLACE_IMAGE, state.tool.value)
        assertEquals(false, session.document.value.character.traces[View.SIDE]?.locked, "Place image frees it")
        onNodeWithTag(TraceTags.SCALE).assertExists()

        val before = assertNotNull(session.document.value.character.traces[View.SIDE])
        onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(30f, 0f))
            moveTo(center + Offset(60f, 0f))
            release()
        }
        waitForIdle()
        val after = assertNotNull(session.document.value.character.traces[View.SIDE])
        assertNotEquals(before.x, after.x, "a drag on the image moves it")
        state.undo()
        assertEquals(before, session.document.value.character.traces[View.SIDE], "one undo step")

        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.R) }
        waitForIdle()
        assertEquals(BuildTool.ROTATE_IMAGE, state.tool.value, "R turns the image while placing")
    }

    @Test
    fun deletingAnImageTakesItsTrace() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        state.addSketch()
        val image = session.document.value.references.images.single()
        onNodeWithContentDescription("References").performClick()
        waitForIdle()
        onNodeWithTag(ReferencesTags.image(image.id)).performClick()
        onNodeWithText("Front").performClick()
        waitForIdle()
        assertNotNull(session.document.value.character.traces[View.FRONT])
        onNodeWithTag(ReferencesTags.DELETE).performClick()
        waitForIdle()
        assertTrue(session.document.value.references.images.isEmpty())
        assertTrue(session.document.value.character.traces.isEmpty())
    }

    @Test
    fun focusSwapsThePaneUnderThePointerIntoTheLargePlace() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        state.layout.value = SheetLayout.FOCUS
        waitForIdle()
        onNodeWithText("Swap pane · Space").assertExists()
        onNodeWithTag(sheetPaneTag(SheetPane.FRONT)).performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertEquals(SheetPane.FRONT, state.focused.value)
    }
}
