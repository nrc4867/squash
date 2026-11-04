package dev.chieppa.squash.screens.shell

import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.edit.CurveDrawerTags
import dev.chieppa.squash.screens.edit.CurveSelection
import dev.chieppa.squash.screens.edit.LEG_SIDE
import dev.chieppa.squash.screens.edit.Refit
import dev.chieppa.squash.screens.edit.RefitReach
import dev.chieppa.squash.screens.edit.SmoothDrawerTags
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 10_000L
private const val RADIUS = 60f
private const val LOOP_POINTS = 60
private const val GRID_PIXELS = 16
private const val DRAG_PIXELS = 24f

private object NoDialogs : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

@OptIn(ExperimentalTestApi::class)
class CurveToolsFlowTest {
    private fun ComposeUiTest.workspace(state: AppState) {
        setStretchContent { SquashApp(state, NoDialogs) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        assertEquals(Screen.WORKSPACE, state.screen.value)
    }

    private fun AppState.drawSide() {
        val part = checkNotNull(selectedPart.value)
        checkNotNull(session.value).send(SetOutline(part, View.SIDE, LEG_SIDE))
    }

    @Test
    fun theDAndEKeysArmTheTools() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        assertEquals(BuildTool.DRAW_OUTLINE, state.tool.value)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.E) }
        waitForIdle()
        assertEquals(BuildTool.EDIT_CURVES, state.tool.value)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.D) }
        waitForIdle()
        assertEquals(BuildTool.DRAW_OUTLINE, state.tool.value)
    }

    @Test
    fun theSKeyArmsSmoothAndItsDrawerSetsTheBrush() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.S) }
        waitForIdle()
        assertEquals(BuildTool.SMOOTH, state.tool.value)
        onNodeWithTag(ShellTags.tool(BuildTool.SMOOTH)).assertExists()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        waitForIdle()
        assertEquals(BuildTool.SMOOTH, state.tool.value, "Ctrl S saves and leaves the tool armed")
        onNodeWithContentDescription("Part").performClick()
        waitForIdle()
        onNodeWithTag(SmoothDrawerTags.SIZE_VALUE).assertTextContains("60 px")
        onNodeWithTag(SmoothDrawerTags.STRENGTH_VALUE).assertTextContains("50%")
        onNodeWithTag(SmoothDrawerTags.SIZE).performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        waitForIdle()
        assertEquals(120f, state.curves.brushSize)
        onNodeWithTag(SmoothDrawerTags.SIZE_VALUE).assertTextContains("120 px")
    }

    @Test
    fun theRealPanesTakeAStrokeAHandleDragAndAStrayStroke() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        val part = checkNotNull(state.selectedPart.value)
        val session = checkNotNull(state.session.value)
        fun side() = session.document.value.character.resolved(part)?.outlines?.get(View.SIDE)
        fun steps() = session.history.steps.value.done.size
        val pane = onNodeWithTag(sheetPaneTag(SheetPane.SIDE))
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.mesh(part).output.value != null && runCatching { pane.assertExists() }.isSuccess }
        val size = pane.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)
        val before = steps()
        pane.performMouseInput {
            moveTo(middle + Offset(RADIUS, 0f))
            press()
            for (i in 1..LOOP_POINTS) {
                val angle = 2.0 * PI * i / LOOP_POINTS
                moveTo(middle + Offset(RADIUS * cos(angle).toFloat(), RADIUS * sin(angle).toFloat()))
            }
            release()
        }
        waitForIdle()
        val drawn = assertNotNull(side(), "a stroke in the real Side pane is an outline")
        assertEquals(before + 1, steps())

        pane.performMouseInput {
            moveTo(Offset(4f, 4f))
            press()
            moveTo(Offset(40f, 4f))
            release()
        }
        waitForIdle()
        assertEquals(before + 1, steps(), "a stroke far from the outline does nothing")
        assertEquals(drawn, side())

        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.E) }
        waitForIdle()
        val hit = gridOf(size.width, size.height).firstOrNull { at ->
            pane.performMouseInput {
                moveTo(at)
                press()
                release()
            }
            waitForIdle()
            state.curves.selection != null
        }
        assertNotNull(hit, "some point of the stored curve answers a press in the pane")
        val index = checkNotNull(state.curves.selection).index
        val grabbed = checkNotNull(side()).points[index]
        pane.performMouseInput {
            moveTo(hit)
            press()
            moveTo(hit + Offset(0f, -DRAG_PIXELS))
            release()
        }
        waitForIdle()
        assertEquals(before + 2, steps(), "the drag is one step")
        assertTrue(checkNotNull(side()).points[index].y > grabbed.y, "up the pane is up the view")
        state.undo()
        assertEquals(drawn, side())
    }

    private fun gridOf(width: Int, height: Int): List<Offset> = buildList {
        for (y in GRID_PIXELS until height step GRID_PIXELS) for (x in GRID_PIXELS until width step GRID_PIXELS) add(Offset(x.toFloat(), y.toFloat()))
    }

    @Test
    fun theDrawerShowsTheOutlineCurvesOnceAPartIsDrawn() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        state.drawSide()
        waitForIdle()
        onNodeWithContentDescription("Part").performClick()
        waitForIdle()
        onNodeWithTag(CurveDrawerTags.outline(View.SIDE)).assertTextContains("7 pts")
        onNodeWithTag(CurveDrawerTags.outline(View.FRONT)).assertTextContains("none")
        onNodeWithTag(CurveDrawerTags.SNAP).performClick()
        waitForIdle()
        assertEquals(false, state.curves.snapEnds)
    }

    @Test
    fun theSelectedPointsFieldsReadAndSetItAsACommand() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        state.drawSide()
        val part = checkNotNull(state.selectedPart.value)
        val session = checkNotNull(state.session.value)
        state.curves.selection = CurveSelection(part, View.SIDE, 3)
        waitForIdle()
        onNodeWithContentDescription("Part").performClick()
        waitForIdle()
        onNodeWithTag(CurveDrawerTags.X).assertExists()
        onAllNodesWithText("Smooth").assertCountEquals(2)
        onNodeWithText("Corner").assertExists()
        val before = session.history.steps.value.done.size
        onNodeWithText("Corner").performClick()
        waitForIdle()
        assertEquals(true, state.curves.isMarkedCorner(CurveSelection(part, View.SIDE, 3)))
        assertEquals(before, session.history.steps.value.done.size, "marking a corner changes nothing in the document")
    }

    @Test
    fun theStatusBarCountsTheSectionsARefitTouchedWhileItStands() = runComposeUiTest {
        val state = AppState(shipped)
        workspace(state)
        state.drawSide()
        val part = checkNotNull(state.selectedPart.value)
        val session = checkNotNull(state.session.value)
        val curve = LEG_SIDE.copy(points = LEG_SIDE.points.mapIndexed { i, p -> if (i == 4) CurvePoint(p.x, p.y + 0.1f) else p })
        session.send(SetOutline(part, View.SIDE, curve))
        state.curves.refit = Refit(part, View.SIDE, curve, 1, 1, RefitReach.SECTIONS)
        waitForIdle()
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("1 section touched")
        onNodeWithText("Section 2 refit · other sections unchanged").assertExists()
        onNodeWithText("Undo").performClick()
        waitForIdle()
        assertEquals(LEG_SIDE, session.document.value.character.resolved(part)?.outlines?.get(View.SIDE))
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("1 drawn · 2 from template")
    }
}
