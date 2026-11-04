package dev.chieppa.squash.screens.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.draw.OutlineDrawerTags
import dev.chieppa.squash.screens.edit.LEG_SIDE
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals

private object NoChooser : FileChooser {
    override fun chooseOpen(format: DocumentFormat<*>): String? = null
    override fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

@OptIn(ExperimentalTestApi::class)
class OutlineDrawerTest {
    private fun ComposeUiTest.workspace(state: AppState) {
        setStretchContent { SquashApp(state, NoChooser) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
    }

    @Test
    fun theDrawerFollowsTheArmedToolAsTheBoardsShow() {
        assertEquals(PartDrawerKind.OUTLINE, partDrawerKind(BuildTool.DRAW_OUTLINE, hasPart = true, drawn = false))
        assertEquals(PartDrawerKind.OUTLINE_CURVES, partDrawerKind(BuildTool.DRAW_OUTLINE, hasPart = true, drawn = true))
        assertEquals(PartDrawerKind.OUTLINE_CURVES, partDrawerKind(BuildTool.EDIT_CURVES, hasPart = true, drawn = true))
        assertEquals(PartDrawerKind.PART, partDrawerKind(BuildTool.EDIT_CURVES, hasPart = true, drawn = false))
        assertEquals(PartDrawerKind.PART, partDrawerKind(BuildTool.DRAW_OUTLINE, hasPart = false, drawn = false))
    }

    @Test
    fun theHintSectionReadsAsTheBoardDrawsItAndIsSessionState() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        onNodeWithContentDescription("Part").performClick()
        waitForIdle()
        onNodeWithTag(OutlineDrawerTags.GHOST_VALUE).assertTextContains("40%")
        assertEquals(true, state.curves.templateHint)
        onNodeWithTag(OutlineDrawerTags.TEMPLATE).performClick()
        waitForIdle()
        assertEquals(false, state.curves.templateHint)
        assertEquals(false, state.session.value?.history?.steps?.value?.done?.size?.let { it > 1 }, "nothing goes on the document's history")
    }

    @Test
    fun theOutlineDrawerReadsAsTheBoardDrawsItAndSetsTheStrokesOptions() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        onNodeWithContentDescription("Part").performClick()
        waitForIdle()
        onNodeWithTag(OutlineDrawerTags.SMOOTH_VALUE).assertTextContains("45%")
        onNodeWithTag(OutlineDrawerTags.PRESSURE).assertTextContains("not used")
        onNodeWithTag(OutlineDrawerTags.WIDTH).assertTextContains("shape only")
        assertEquals(StrokeOptions(), state.curves.strokeOptions)
        onNodeWithTag(OutlineDrawerTags.CLOSE_END).performClick()
        onNodeWithTag(OutlineDrawerTags.SNAP_LINE).performClick()
        waitForIdle()
        assertEquals(StrokeOptions(snapLine = false, closeEnd = false), state.curves.strokeOptions)
        assertEquals(false, state.curves.options.stroke.closeEnd)
    }

    @Test
    fun theDrawerChangesTitleWithTheToolAndThePart() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        onNodeWithContentDescription("Part").performClick()
        onNodeWithText("Outline").assertExists()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.E) }
        waitForIdle()
        onNodeWithText("Torso template").assertExists()
        checkNotNull(state.session.value).send(SetOutline(checkNotNull(state.selectedPart.value), View.SIDE, LEG_SIDE))
        waitForIdle()
        onNodeWithText("Outline curves").assertExists()
        onNodeWithText("HINT").assertExists()
        state.tool.value = BuildTool.NONE
        waitForIdle()
        onNodeWithText("Torso template").assertExists()
        onNodeWithText("FIT").assertExists()
        onNodeWithText("BUILT FROM").assertExists()
        onNodeWithContentDescription("Part").assertExists()
    }
}
