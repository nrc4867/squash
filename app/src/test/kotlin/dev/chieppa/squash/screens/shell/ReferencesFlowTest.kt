package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.references.ReferencesTags
import dev.chieppa.squash.screens.references.TraceTags
import dev.chieppa.squash.screens.references.decodeImage
import dev.chieppa.squash.screens.references.newImageCommand
import dev.chieppa.squash.screens.references.sketchPng
import dev.chieppa.squash.screens.settle
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

private const val WIDTH = 1440
private const val HEIGHT = 900

private object NoReferenceFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The References, Trace and Place image drawers and the Focus layout in the app whole, over the saved fennec. */
@OptIn(ExperimentalTestApi::class)
class ReferencesFlowTest {
    private val work = TestWork()

    @AfterTest
    fun stop() = work.close()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoReferenceFiles) }
        settle(work, state)
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
        onNode(hasText("Side") and hasAnyAncestor(hasTestTag(ReferencesTags.TRACE_IN))).performClick()
        waitForIdle()
        val trace = assertNotNull(session.document.value.character.traces[View.SIDE])
        assertEquals(image.id, trace.image)
        assertTrue(trace.locked, "a new trace stays put")

        onNodeWithTag(TraceTags.row(View.SIDE)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, state.references.handles, "a locked trace shows no handles")
        onNodeWithTag(TraceTags.SCALE).assertDoesNotExist()
        val armed = state.tool.value
        onNodeWithTag(TraceTags.LOCK).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, session.document.value.character.traces[View.SIDE]?.locked)
        assertEquals(true, state.references.handles, "unlocked, its handles show")
        assertEquals(armed, state.tool.value, "and no tool is armed for it")
        onNodeWithTag(TraceTags.SCALE).performScrollTo().assertExists()

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

        onNodeWithTag(TraceTags.LOCK).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, state.references.handles, "locking it again puts the handles away")
        onNodeWithContentDescription("References").performClick()
        waitForIdle()
        onNodeWithTag(TraceTags.LOCK).assertDoesNotExist()
        assertEquals(false, state.references.handles)
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
        onNode(hasText("Front") and hasAnyAncestor(hasTestTag(ReferencesTags.TRACE_IN))).performClick()
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
