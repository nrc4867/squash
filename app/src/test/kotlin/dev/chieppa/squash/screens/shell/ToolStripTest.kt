package dev.chieppa.squash.screens.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.draw.WorkspaceTools
import dev.chieppa.squash.screens.sheet.SheetStrip
import dev.chieppa.squash.screens.surface.lineToolSection
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals

private const val NARROW_WIDTH = 420
private const val NARROW_HEIGHT = 400

/** The strip at a width too narrow for every tool: every tool stays one press away, and none needs a scroll. */
@OptIn(ExperimentalTestApi::class)
class ToolStripTest {
    private val work = TestWork()

    @Test
    fun aCollapsedSectionOpensItsToolsAndArmingOneClosesItAndLightsIt() = runDesktopComposeUiTest(NARROW_WIDTH, NARROW_HEIGHT) {
        val state = AppState(TemplateFolder.shipped(), work)
        state.tool.value = BuildTool.NONE
        setStretchContent { SheetStrip(tools = { WorkspaceTools(state, lineToolSection(state)) }) }
        waitForIdle()
        onNodeWithTag(ShellTags.tool(BuildTool.MOVE)).assertDoesNotExist()
        onNodeWithTag(ShellTags.section("outline")).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.tool(BuildTool.MOVE)).performClick()
        waitForIdle()
        assertEquals(BuildTool.MOVE, state.tool.value)
        onNodeWithTag(ShellTags.section("outline")).assertIsSelected()
    }
}
