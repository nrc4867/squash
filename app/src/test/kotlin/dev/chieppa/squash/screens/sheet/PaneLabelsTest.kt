package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


private object LabelsNoFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** A pane's label says which way it looks at the body, whichever view it holds. See HLD: data-model, How a pane looks at a part. */
@OptIn(ExperimentalTestApi::class)
class PaneLabelsTest {
    private val work = TestWork()

    private fun ComposeUiTest.workspaceOn(state: AppState, row: String) {
        setStretchContent { SquashApp(state, LabelsNoFiles) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.partRow(row)).performClick()
        onNodeWithTag(ShellTags.ADD_PART_ADD).performClick()
        waitForIdle()
        val session = assertNotNull(state.session.value)
        settle(work) { session.graph.settled.value }
        waitForIdle()
    }

    private fun ComposeUiTest.assertLabelIn(pane: SheetPane, label: String) {
        val area = onNodeWithTag(sheetPaneTag(pane)).fetchSemanticsNode()
        val at = onNodeWithText(label).fetchSemanticsNode().positionOnScreen
        val from = area.positionOnScreen
        assertTrue(
            at.x >= from.x && at.y >= from.y && at.x < from.x + area.size.width && at.y < from.y + area.size.height,
            "$label at $at, outside $pane from $from, ${area.size}",
        )
    }

    @Test
    fun aLegsPanesReadTheirDirectionsAndHoldItsViewsSwapped() = runComposeUiTest {
        val state = AppState(shipped, work)
        workspaceOn(state, "front-leg")
        assertLabelIn(SheetPane.TOP, "Top · part axes")
        assertLabelIn(SheetPane.SIDE, "Side · part axes")
        assertLabelIn(SheetPane.FRONT, "Front · part axes")
        assertLabelIn(SheetPane.FULL, "Full · free orbit")

        onNodeWithTag(sheetPaneTag(SheetPane.TOP)).performMouseInput { click(center) }
        assertEquals(View.FRONT, state.activeView.value, "looking down a leg shows its Front outline")
        onNodeWithTag(sheetPaneTag(SheetPane.FRONT)).performMouseInput { click(center) }
        assertEquals(View.TOP, state.activeView.value, "looking at a leg from the front shows its Top outline")
    }

    @Test
    fun aPartAlongTheBodyReadsTheSameDirections() = runComposeUiTest {
        val state = AppState(shipped, work)
        workspaceOn(state, "tail")
        assertLabelIn(SheetPane.TOP, "Top · part axes")
        assertLabelIn(SheetPane.SIDE, "Side · part axes")
        assertLabelIn(SheetPane.FRONT, "Front · part axes")
        onNodeWithTag(sheetPaneTag(SheetPane.TOP)).performMouseInput { click(center) }
        assertEquals(View.TOP, state.activeView.value)
    }
}
