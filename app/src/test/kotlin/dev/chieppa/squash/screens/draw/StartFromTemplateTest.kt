package dev.chieppa.squash.screens.draw

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private object NoChooser : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

@OptIn(ExperimentalTestApi::class)
class StartFromTemplateTest {
    private val work = TestWork()

    private fun ComposeUiTest.workspace(state: AppState) {
        setStretchContent { SquashApp(state, NoChooser) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
    }

    private fun AppState.outline(view: View) = session.value?.document?.value?.character?.let { character ->
        selectedPart.value?.let(character::resolved)?.outlines?.get(view)
    }

    @Test
    fun anUndrawnViewsRowSetsItsOutlineAsOneUndoStep() = runComposeUiTest {
        val state = AppState(shipped, work)
        workspace(state)
        val history = assertNotNull(state.session.value).history
        onNodeWithTag(ShellTags.startTemplate(View.SIDE)).performScrollTo().assertIsEnabled()
        val steps = history.steps.value.done.size
        onNodeWithTag(ShellTags.startTemplate(View.SIDE)).performClick()
        waitForIdle()
        assertNotNull(state.outline(View.SIDE))
        assertEquals(steps + 1, history.steps.value.done.size)
        onNodeWithTag(ShellTags.startTemplate(View.SIDE)).assertDoesNotExist()
        onNodeWithTag(ShellTags.startTemplate(View.TOP)).assertIsEnabled()
        assertNull(state.outline(View.TOP))
        onNodeWithTag(ShellTags.section("template")).assertDoesNotExist()
        state.undo()
        waitForIdle()
        assertNull(state.outline(View.SIDE))
        onNodeWithTag(ShellTags.startTemplate(View.SIDE)).assertExists()
    }

    @Test
    fun inSingleItWaitsForASnappedView() {
        val state = AppState(shipped, work)
        runComposeUiTest {
            workspace(state)
            state.layout.value = SheetLayout.SINGLE
            state.curves.snapped = null
            assertNull(state.templateStartView())
            state.curves.snapped = View.SIDE
            state.activeView.value = View.SIDE
            assertEquals(View.SIDE, state.templateStartView())
        }
    }

    @Test
    fun thePressWithAStrokeUnderWayDoesNothing() {
        val state = AppState(shipped, work)
        runComposeUiTest {
            workspace(state)
            state.curves.drawing = View.SIDE
            assertNull(state.templateStartView())
            state.startFromTemplate()
            assertNull(state.outline(View.SIDE))
            state.curves.drawing = null
        }
    }
}
