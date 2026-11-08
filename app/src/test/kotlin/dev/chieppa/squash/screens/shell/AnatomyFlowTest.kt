package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.move.MoveMode
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

private const val WIDTH = 1440
private const val HEIGHT = 900

private object NoAnatomyFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** A Blank character given a joint and a landmark by Move's presses in Full, listed and deleted in Anatomy, and two legs paired. */
@OptIn(ExperimentalTestApi::class)
class AnatomyFlowTest {
    private val work = TestWork()

    private fun ComposeUiTest.settle(state: AppState) {
        settle(work, state)
        waitForIdle()
    }

    private fun ComposeUiTest.pressFullMiddle(state: AppState) {
        val pane = onNodeWithTag(sheetPaneTag(SheetPane.FULL))
        val size = pane.fetchSemanticsNode().size
        pane.performMouseInput { click(Offset(size.width / 2f, size.height / 2f)) }
        settle(state)
    }

    @Test
    fun movesPressesAddAJointAndALandmarkThatAnatomyListsAndTwoLegsPair() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val folder = TemplateFolder.shipped()
        val state = AppState(folder, work)
        state.createCharacter(folder.plans.first { it.id == BodyPlanId("blank") }, "Lizard")
        setStretchContent { SquashApp(state, NoAnatomyFiles) }
        settle(state)
        val session = assertNotNull(state.session.value)
        val body = assertNotNull(state.selectedPart.value)
        fun params(part: PartId) = session.document.value.character.let { it.resolvedShape(assertNotNull(it.part(part))).params }
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        assertEquals(BuildTool.MOVE, state.tool.value)

        onNodeWithText("Add joint").performClick()
        waitForIdle()
        assertEquals(MoveMode.ADD_JOINT, state.move.mode.value)
        val steps = session.history.steps.value.done.size
        pressFullMiddle(state)
        assertEquals(steps + 1, session.history.steps.value.done.size, "a press adds a joint as one step")
        assertIs<SetParameters>(session.history.steps.value.done.last())
        assertEquals(listOf(1), HandJoints.ids(params(body)))
        onNodeWithText("Body · add joint · 1 parts").assertExists()

        onNodeWithText("Add landmark").performClick()
        waitForIdle()
        pressFullMiddle(state)
        assertEquals(listOf(1), HandLandmarks.ids(params(body)))
        onNodeWithTag(ShellTags.ANATOMY_JOINTS).performScrollTo().assertTextContains("3")
        onNodeWithTag(ShellTags.ANATOMY_LANDMARKS).performScrollTo().assertTextContains("1")
        onNodeWithTag(ShellTags.anatomyDelete(HandJoints.name(1))).performScrollTo().performClick()
        settle(state)
        assertEquals(emptyList(), HandJoints.ids(params(body)))

        val tube = assertNotNull(folder.template(TemplateId("tube"))).ref
        val a = PartId("leg-a")
        val b = PartId("leg-b")
        session.send(HandSockets.add(body, 1, 0.3f, 0.35f, 0f, paired = false))
        session.send(HandSockets.add(body, 2, 0.3f, 0.65f, 0f, paired = false))
        session.send(AddParts(listOf(PartInstance(a, tube, SocketRef(body, HandSockets.name(1))), PartInstance(b, tube, SocketRef(body, HandSockets.name(2))))))
        state.selectedPart.value = a
        settle(state)
        onNodeWithTag(ShellTags.PAIR_WITH).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.pairOption(b.value)).performClick()
        settle(state)
        val character = session.document.value.character
        assertEquals(character.part(a)?.pair, assertNotNull(character.part(b)?.pair))
        val placed = assertNotNull(session.assembly.output.value)
        val (left, right) = listOf(a, b).map { assertNotNull(placed.part(it)).origin }
        assertEquals(left.z, -right.z, 1e-3f, "the pair mirrors across the body's middle")
    }
}
