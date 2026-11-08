package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.SetPlanted
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.surface.pose.PoseInspectorTags
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val WIDTH = 1440
private const val HEIGHT = 900

/**
 * How far apart the pointer looks for a paw's dot across Full, in pixels, under twice a handle's reach over the square root of two
 * so a dot always has a scanned point within reach; and how far a drag lifts it.
 */
private const val SCAN_STEP = 12f
private const val LIFT_PIXELS = 40f
private const val DRAG_STEPS = 8

private object NoPoseFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Pose stage in the app whole, over the saved fennec: its key and strip, a paw dragged in Full, a pin, the inspector's key, and back to Shape. */
@OptIn(ExperimentalTestApi::class)
class PoseFlowTest {
    private val work = TestWork()

    @AfterTest
    fun stop() = work.close()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoPoseFiles) }
        settle(work, state)
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.key(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    /** Moves the pointer over Full's lower half, a row at a time, until it lights a paw's dot, and gives where. */
    private fun ComposeUiTest.findPaw(state: AppState): Offset? {
        val pane = onNodeWithTag(sheetPaneTag(SheetPane.FULL))
        val seen = ArrayList<PoseGrip>()
        val watch = work.scope.launch(Dispatchers.Unconfined) { snapshotFlow { state.pose.hovered }.collect { if (it is PoseGrip.End) seen += it } }
        try {
            val size = pane.fetchSemanticsNode().size
            var y = size.height * 0.45f
            while (y < size.height * 0.95f) {
                seen.clear()
                pane.performMouseInput {
                    var x = width * 0.15f
                    while (x < width * 0.85f) {
                        moveTo(Offset(x, y))
                        x += SCAN_STEP
                    }
                }
                waitForIdle()
                if (seen.isNotEmpty()) {
                    var x = size.width * 0.15f
                    while (x < size.width * 0.85f) {
                        pane.performMouseInput { moveTo(Offset(x, y)) }
                        waitForIdle()
                        if (state.pose.hovered is PoseGrip.End) return Offset(x, y)
                        x += SCAN_STEP
                    }
                }
                y += SCAN_STEP
            }
            return null
        } finally {
            watch.cancel()
        }
    }

    @Test
    fun sixShowsPoseAndADragOnAPawIsOneStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        key(Key.Six)
        assertEquals(Stage.POSE, state.stage.value)
        assertEquals(BuildTool.DRAG, state.tool.value, "Pose opens on Drag")
        for (tool in listOf(BuildTool.DRAG, BuildTool.TURN, BuildTool.PIN, BuildTool.LOOK)) onNodeWithTag(ShellTags.tool(tool)).assertExists()
        onNodeWithTag(ShellTags.tool(BuildTool.PAINT_WEIGHT)).assertDoesNotExist()
        key(Key.E)
        assertEquals(BuildTool.TURN, state.tool.value)
        key(Key.D)
        assertEquals(BuildTool.DRAG, state.tool.value)
        settle(work) { state.rigNow() != null }
        waitForIdle()
        onNodeWithTag(ShellTags.STATUS_STAGE).onChildren().onFirst().assertTextContains("Pose · Drag", substring = true)
        onNodeWithTag(PoseInspectorTags.FIRM).assertExists()

        val steps = session.history.steps.value.done.size
        val at = assertNotNull(findPaw(state), "a paw's dot shows in Full")
        val paw = (state.pose.hovered as PoseGrip.End).limb
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(at)
            press()
            for (i in 1..DRAG_STEPS) moveTo(at + Offset(0f, -LIFT_PIXELS * i / DRAG_STEPS))
            release()
        }
        waitForIdle()
        assertEquals(steps + 1, session.history.steps.value.done.size, "one drag is one step")
        assertIs<SetPose>(session.history.steps.value.done.last())
        val leg = assertNotNull(session.document.value.character.part(paw)?.socket?.parent)
        val pose = session.document.value.character.pose
        assertTrue(pose.of(leg, "hip") != null || pose.of(leg, "knee") != null, "the paw's leg moved")
        assertEquals(paw, state.pose.limb)
        onNodeWithTag(PoseInspectorTags.LIMB_PLANTED).assertExists()

        key(Key.P)
        assertEquals(BuildTool.PIN, state.tool.value)
        val pin = assertNotNull(findPaw(state))
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput { click(pin) }
        waitForIdle()
        assertEquals(steps + 2, session.history.steps.value.done.size)
        val planted = assertIs<SetPlanted>(session.history.steps.value.done.last())
        assertTrue(planted.planted)
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("1 paws planted", substring = true)

        onNodeWithText("Planted", useUnmergedTree = true).assertExists()
        key(Key.Tab)
        onNodeWithText("Planted", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(BuildTool.PIN, state.tool.value, "the inspector closing arms nothing")
        key(Key.One)
        assertEquals(Stage.SHAPE, state.stage.value)
        assertEquals(Stage.SHAPE, state.tool.value.stage)
        assertNull(state.pose.held)
        state.undo()
        state.undo()
        assertEquals(steps, session.history.steps.value.done.size)
    }
}
