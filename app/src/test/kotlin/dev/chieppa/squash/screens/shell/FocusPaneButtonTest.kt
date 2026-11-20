package dev.chieppa.squash.screens.shell

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.edit.CurveSelection
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.paneFocusTag
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Tag

private object NoChosenFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The button in each small pane's corner that makes it the large one. */
@OptIn(ExperimentalTestApi::class)
class FocusPaneButtonTest {
    private val work = TestWork()

    private fun ComposeUiTest.workspace(state: AppState) {
        setStretchContent { SquashApp(state, NoChosenFiles) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
    }

    @Tag("integration")
    @Test
    fun inFocusEachSmallPanesButtonMakesItTheLargeOne() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped, work)
        workspace(state)
        state.layout.value = SheetLayout.FOCUS
        waitForIdle()
        onNodeWithTag(paneFocusTag(BodyView.SIDE)).assertDoesNotExist()
        onNodeWithTag(paneFocusTag(BodyView.TOP)).performClick()
        waitForIdle()
        assertEquals(SheetPane.TOP, state.focused.value)
        onNodeWithTag(paneFocusTag(BodyView.TOP)).assertDoesNotExist()
        onNodeWithTag(paneFocusTag(null)).performClick()
        waitForIdle()
        assertEquals(SheetPane.FULL, state.focused.value, "Full can be the large one too")
        onNodeWithTag(paneFocusTag(BodyView.SIDE)).performClick()
        waitForIdle()
        assertEquals(SheetPane.SIDE, state.focused.value)
    }

    @Tag("integration")
    @Test
    fun fromFullFirstASheetsButtonTurnsToFocusWithItLarge() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped, work)
        workspace(state)
        state.layout.value = SheetLayout.FULL_FIRST
        waitForIdle()
        onNodeWithTag(paneFocusTag(null)).assertDoesNotExist()
        onNodeWithTag(paneFocusTag(BodyView.FRONT)).performClick()
        waitForIdle()
        assertEquals(SheetLayout.FOCUS, state.layout.value)
        assertEquals(SheetPane.FRONT, state.focused.value)
    }

    @Test
    fun theSheetLayoutHasNoSmallPanesAndNoButtons() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped, work)
        workspace(state)
        waitForIdle()
        for (body in BodyView.entries.map<BodyView, BodyView?> { it } + null) onNodeWithTag(paneFocusTag(body)).assertDoesNotExist()
    }

    /** The button's own pixels: opaque over most of its box, so it shows over whatever the pane draws, with an icon on it. */
    private fun ComposeUiTest.assertShown(body: BodyView, state: String) {
        val image = onNodeWithTag(paneFocusTag(body)).assertIsDisplayed().captureToImage()
        val map = image.toPixelMap()
        val pixels = (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map[x, y] } }
        val opaque = pixels.count { it.alpha > 0.99f }
        assertTrue(opaque > pixels.size * 3 / 4, "$body $state: $opaque of ${pixels.size} pixels opaque")
        assertTrue(pixels.distinct().size > 2, "$body $state: an icon on it")
        val folder = File("build/focus-button").apply { mkdirs() }
        ImageIO.write(image.toAwtImage(), "png", File(folder, "${body.name.lowercase()}-$state.png"))
    }

    @Test
    fun theButtonShowsOnAnOpaquePillWhetherThePaneIsActiveHoveredOrHoldsTheSelection() = runDesktopComposeUiTest(1600, 900) {
        // The large pane keeps its button composed but folded, so all four panes carry one.
        val label = "Make this view large"
        val state = AppState(shipped, work)
        workspace(state)
        state.layout.value = SheetLayout.FOCUS
        state.focused.value = SheetPane.SIDE
        waitForIdle()
        assertShown(BodyView.TOP, "resting")
        state.activeView.value = View.TOP
        waitForIdle()
        assertShown(BodyView.TOP, "active")
        onNodeWithTag(sheetPaneTag(SheetPane.TOP)).performMouseInput { moveTo(center) }
        waitForIdle()
        assertShown(BodyView.TOP, "hovered")
        val part = assertNotNull(state.selectedPart.value, "a new character starts with a part selected")
        state.curves.selection = CurveSelection(part, View.TOP, 0)
        waitForIdle()
        assertShown(BodyView.TOP, "selected")
        assertShown(BodyView.FRONT, "beside")
        state.focused.value = SheetPane.TOP
        waitForIdle()
        assertShown(BodyView.SIDE, "small")
        onNodeWithTag(paneFocusTag(BodyView.TOP)).assertDoesNotExist()
        onAllNodesWithContentDescription(label).assertCountEquals(4)
    }
}
