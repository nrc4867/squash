package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.deletePickedSocket
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.move.MoveMode
import dev.chieppa.squash.screens.pickedOutward
import dev.chieppa.squash.screens.setOutward
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The Move section's Place part and Add socket, its Outward angle, Pair and Delete socket. */
@OptIn(ExperimentalTestApi::class)
class MoveSectionTest {
    private val state = AppState(shipped).also {
        it.open(fennecDocument())
        it.tool.value = BuildTool.MOVE
        it.selectedPart.value = TORSO
    }
    private val session get() = checkNotNull(state.session.value)
    private val picked = SocketRef(TORSO, HandSockets.name(1))
    private fun torsoParams() = session.document.value.character.let { it.resolvedShape(checkNotNull(it.part(TORSO))).params }

    @Test
    fun theSegmentChoosesAddSocketWithItsOutwardPairAndDelete() = runComposeUiTest {
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithText("Place part").assertExists()
        onNodeWithTag(ShellTags.SOCKET_PAIR).assertDoesNotExist()
        onNodeWithText("Add socket").performClick()
        waitForIdle()
        assertEquals(MoveMode.ADD_SOCKET, state.move.mode.value)
        onNodeWithTag(ShellTags.SOCKET_OUTWARD).assertExists()
        onNodeWithTag(ShellTags.SOCKET_DELETE).assertIsNotEnabled()
        onNodeWithTag(ShellTags.SOCKET_PAIR).performClick()
        waitForIdle()
        assertFalse(state.move.pair.value, "Pair sets the next socket's flag")
        state.setOutward(25f)
        assertEquals(25f, state.pickedOutward(), "with none picked the angle is the next socket's")
    }

    @Test
    fun aPickedEmptySocketEnablesDeleteAndTheAngleEditsIt() = runComposeUiTest {
        state.move.mode.value = MoveMode.ADD_SOCKET
        session.send(HandSockets.add(TORSO, 1, 0.7f, 0.2f, 10f, paired = true))
        state.move.picked.value = picked
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.SOCKET_DELETE).assertIsEnabled()
        assertEquals(10f, state.pickedOutward())
        state.setOutward(30f)
        assertEquals(30f, torsoParams()[HandSockets.outward(1)], "the field edits the picked socket")
        val steps = session.history.steps.value.done.size
        onNodeWithTag(ShellTags.SOCKET_DELETE).performClick()
        waitForIdle()
        assertEquals(steps + 1, session.history.steps.value.done.size, "one step")
        assertEquals(emptyList(), HandSockets.ids(torsoParams()))
        assertNull(state.move.picked.value)
        session.history.undo()
        assertEquals(listOf(1), HandSockets.ids(torsoParams()))
    }

    @Test
    fun aSocketHoldingAPartCannotBeDeleted() = runComposeUiTest {
        state.move.mode.value = MoveMode.ADD_SOCKET
        session.send(HandSockets.add(TORSO, 1, 0.7f, 0.2f, 0f, paired = true))
        val plan = shipped.plans.first()
        val socket = openSockets(session.document.value, plan, shipped).first { it.ref == picked }
        session.send(AddParts(placeOnSocket(socket, assertNotNull(shipped.template(TemplateId("wing-feathered"))), session.document.value)))
        state.move.picked.value = picked
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.SOCKET_DELETE).assertIsNotEnabled()
        state.deletePickedSocket()
        assertEquals(listOf(1), HandSockets.ids(torsoParams()), "a held socket stays")
    }
}
