package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.newest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.move.MoveMode
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 60_000L
private const val WIDTH = 1440
private const val HEIGHT = 900
private const val SCAN_STEP = 6f
private const val LIFT_PIXELS = 40f
private const val DRAG_STEPS = 8

/** How many steps across Full the search for the torso presses at. */
private const val GRID = 12

private object NoSocketFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** A socket placed by hand on the fennec's torso in Full, a wing pair added on it with T and Enter, and the wing dragged in Pose. */
@OptIn(ExperimentalTestApi::class)
class AddSocketFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private fun ComposeUiTest.key(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.settle(state: AppState) {
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.session.value?.graph?.settled?.value == true }
        waitForIdle()
    }

    /**
     * Presses Full across a grid until a press places a socket on the torso, taking back any that lands on another part, and gives
     * where it pressed.
     */
    private fun ComposeUiTest.pressTorso(state: AppState): Offset? {
        val session = assertNotNull(state.session.value)
        val pane = onNodeWithTag(sheetPaneTag(SheetPane.FULL))
        val size = pane.fetchSemanticsNode().size
        for (row in 1 until GRID) for (column in 1 until GRID) {
            val at = Offset(size.width * column / GRID.toFloat(), size.height * row / GRID.toFloat())
            val before = session.history.steps.value.done.size
            pane.performMouseInput { click(at) }
            settle(state)
            if (session.history.steps.value.done.size == before) continue
            if (state.move.picked.value?.parent == TORSO) return at
            state.undo()
            settle(state)
        }
        return null
    }

    /** Moves the pointer over Full a row at a time until it lights the end handle of one of [limbs], and gives where. */
    private fun ComposeUiTest.findEnd(state: AppState, limbs: Set<PartId>): Offset? {
        val pane = onNodeWithTag(sheetPaneTag(SheetPane.FULL))
        val size = pane.fetchSemanticsNode().size
        fun lit() = (state.pose.hovered as? PoseGrip.End)?.limb in limbs
        var seen = false
        val watch = scope.launch(Dispatchers.Unconfined) { snapshotFlow { lit() }.collect { if (it) seen = true } }
        try {
            var y = size.height * 0.1f
            while (y < size.height * 0.95f) {
                seen = false
                pane.performMouseInput {
                    var x = width * 0.05f
                    while (x < width * 0.95f) {
                        moveTo(Offset(x, y))
                        x += SCAN_STEP
                    }
                }
                waitForIdle()
                if (seen) {
                    var x = size.width * 0.05f
                    while (x < size.width * 0.95f) {
                        pane.performMouseInput { moveTo(Offset(x, y)) }
                        waitForIdle()
                        if (lit()) return Offset(x, y)
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
    fun aSocketPressedOnTheTorsoTakesAWingPairThatPoses() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = AppState(TemplateFolder.shipped(), scope)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoSocketFiles) }
        settle(state)
        val session = assertNotNull(state.session.value)
        state.selectedPart.value = TORSO
        key(Key.V)
        assertEquals(BuildTool.MOVE, state.tool.value)
        onNodeWithText("Add socket").performClick()
        waitForIdle()
        assertEquals(MoveMode.ADD_SOCKET, state.move.mode.value)

        val steps = session.history.steps.value.done.size
        assertNotNull(pressTorso(state), "a press in Full lands on the torso")
        assertEquals(steps + 1, session.history.steps.value.done.size, "placing a socket is one step")
        val socket = SocketRef(TORSO, HandSockets.name(1))
        assertEquals(socket, state.move.picked.value)
        val dots = assertNotNull(session.assembly.output.value).sockets.filter { it.ref == socket }
        assertEquals(2, dots.size, "a paired socket is two on the torso")

        key(Key.T)
        assertEquals(BuildTool.ADD_PART, state.tool.value)
        assertEquals(socket, state.addingSocket.value?.ref)
        state.templates.newest.forEach { onNodeWithTag(ShellTags.addTile(it.id.value)).assertExists() }
        onNodeWithText("Torso · socket 1").assertExists()
        onNodeWithTag(ShellTags.addTile("wing-feathered")).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.ADD_PART).performKeyInput { pressKey(Key.Enter) }
        settle(state)
        assertEquals(steps + 2, session.history.steps.value.done.size, "the pair is one step")
        assertIs<AddParts>(session.history.steps.value.done.last())
        val wings = session.document.value.character.parts.filter { it.socket == socket }
        assertEquals(2, wings.size)
        assertTrue(wings.all { it.role == Role.WING })
        assertTrue(onAllNodesWithText("Wing").fetchSemanticsNodes().isNotEmpty(), "the wing shows in Parts")

        key(Key.Six)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.rigNow() != null }
        waitForIdle()
        val at = assertNotNull(findEnd(state, wings.map { it.id }.toSet()), "a wing's end handle shows in Full")
        val wing = assertNotNull((state.pose.hovered as? PoseGrip.End)?.limb)
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(at)
            press()
            for (i in 1..DRAG_STEPS) moveTo(at + Offset(0f, -LIFT_PIXELS * i / DRAG_STEPS))
            release()
        }
        waitForIdle()
        assertEquals(steps + 3, session.history.steps.value.done.size, "one drag is one step")
        assertIs<SetPose>(session.history.steps.value.done.last())
        val pose = session.document.value.character.pose
        assertTrue(listOf("shoulder", "elbow", "wrist").any { pose.of(wing, it) != null }, "the wing's bones turned")
        assertTrue(wing !in pose.planted, "a wing never plants")
    }
}
