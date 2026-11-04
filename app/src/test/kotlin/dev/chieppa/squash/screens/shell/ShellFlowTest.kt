package dev.chieppa.squash.screens.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.catalog.twin
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.document.RecentFiles
import dev.chieppa.stretch.test.setStretchContent
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private object NoFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

private const val SETTLE_MILLIS = 10_000L

@OptIn(ExperimentalTestApi::class)
class ShellFlowTest {
    private fun ComposeUiTest.show(state: AppState) = setStretchContent { SquashApp(state, NoFiles) }

    private fun ComposeUiTest.createCharacter(state: AppState) {
        show(state)
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        assertEquals(Screen.WORKSPACE, state.screen.value)
    }

    @Test
    fun firstLaunchGoesFromNewCharacterToTheWorkspace() = runComposeUiTest {
        val state = AppState(shipped)
        show(state)
        onNodeWithTag(ShellTags.NEW_DIALOG).assertExists()
        onNodeWithTag(ShellTags.newPlan("canine-quadruped")).assertExists()
        onNodeWithTag(ShellTags.STATUS_START).assertExists()
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()

        onNodeWithTag(ShellTags.WORKSPACE).assertExists()
        onNodeWithTag(ShellTags.NEW_DIALOG).assertDoesNotExist()
        onNodeWithTag(ShellTags.tool(BuildTool.DRAW_OUTLINE)).assertExists()
        onNodeWithTag(ShellTags.partRow("torso")).assertExists()
        onNodeWithText("Fennec · draft").assertExists()
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("0 drawn · 3 from template")
        val session = assertNotNull(state.session.value)
        assertEquals("Fennec", session.document.value.character.name)
        assertEquals(session.document.value.character.parts.single().id, state.selectedPart.value)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { onAllNodesWithText("Settled").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun anEmptyNameFallsBackToUntitled() = runComposeUiTest {
        val state = AppState(shipped)
        show(state)
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        assertEquals("Untitled", state.session.value?.document?.value?.character?.name)
    }

    @Test
    fun aSocketRowLeadsToThePickerAndDrawingSelectsThePart() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()

        onNodeWithTag(ShellTags.PICK).assertExists()
        onNodeWithTag(ShellTags.PICK_SOCKET).assertExists()
        assertEquals(TemplateId("leg"), state.pickedType.value)
        onNodeWithTag(ShellTags.pickTile("leg")).assertExists()
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("0 of 8 parts")

        onNodeWithTag(ShellTags.PICK_DRAW).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.WORKSPACE).assertExists()
        val document = assertNotNull(state.session.value).document.value
        val legs = document.character.parts.filter { it.socket?.socket == "front-leg" }
        assertEquals(2, legs.size)
        assertEquals(legs.first().id, state.selectedPart.value)
        onNodeWithTag(ShellTags.partRow("paw")).assertExists()
    }

    @Test
    fun aDotOnTheRestPosePicksItsSocketAndTheTreeStillDoes() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        val session = assertNotNull(state.session.value)
        waitUntil(timeoutMillis = 20_000) { session.assembly.output.value != null && onAllNodesWithTag(ShellTags.socketDot("tail")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("front-leg", state.pickedSocket.value?.name)

        onNodeWithTag(ShellTags.socketDot("tail")).performClick()
        waitForIdle()
        assertEquals("tail", state.pickedSocket.value?.name)
        assertEquals(TemplateId("tail"), state.pickedType.value)
        onNodeWithTag(ShellTags.PICK).assertExists()

        onNodeWithTag(ShellTags.partRow("back-leg")).performClick()
        waitForIdle()
        assertEquals("back-leg", state.pickedSocket.value?.name)
    }

    @Test
    fun aTileWithNoOpenSocketOffersNothingToDraw() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithContentDescription("Part types").performClick()
        onNodeWithTag(ShellTags.PICK).assertExists()
        onNodeWithTag(ShellTags.PICK_DRAW).assertExists()
        onNodeWithTag(ShellTags.pickTile("torso")).performClick()
        onNodeWithTag(ShellTags.PICK_DRAW).assertDoesNotExist()
        onNodeWithContentDescription("Part types").performClick()
        onNodeWithTag(ShellTags.WORKSPACE).assertExists()
    }

    @Test
    fun theTypesKeyKeepsWorkingAfterTheTileHoldingFocusIsGone() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        fun pressTypes(tag: String) {
            onNodeWithTag(tag).performKeyInput { pressKey(Key.T) }
            waitForIdle()
        }
        pressTypes(ShellTags.WORKSPACE)
        assertEquals(Screen.PICK_PART, state.screen.value)
        onNodeWithTag(ShellTags.pickTile("torso")).performClick()
        pressTypes(ShellTags.PICK)
        assertEquals(Screen.WORKSPACE, state.screen.value)
        pressTypes(ShellTags.WORKSPACE)
        assertEquals(Screen.PICK_PART, state.screen.value)
    }

    @Test
    fun undoInTheTopBarTakesBackAPlacedPart() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithTag(ShellTags.partRow("tail")).performClick()
        onNodeWithTag(ShellTags.PICK_DRAW).performClick()
        val session = assertNotNull(state.session.value)
        waitForIdle()
        assertEquals(2, session.document.value.character.parts.size)
        onNodeWithContentDescription("Undo").performClick()
        waitForIdle()
        assertEquals(1, session.document.value.character.parts.size)
        onNodeWithContentDescription("Redo").performClick()
        waitForIdle()
        assertEquals(2, session.document.value.character.parts.size)
    }

    @Test
    fun theSliderIsOneUndoStepAndStaysInItsLimits() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithContentDescription("Part").performClick()
        state.tool.value = BuildTool.NONE
        waitForIdle()
        val session = assertNotNull(state.session.value)
        val root = session.document.value.character.parts.single().id
        val steps = session.history.steps.value.done.size
        onNodeWithTag(ShellTags.parameter("size")).performSemanticsAction(SemanticsActions.SetProgress) { it(99f) }
        waitForIdle()
        assertEquals(2.5f, session.document.value.character.resolved(root)?.params?.get("size"))
        assertEquals(steps + 1, session.history.steps.value.done.size)
        session.history.undo()
        assertEquals(null, session.document.value.character.resolved(root)?.params?.get("size"))
    }

    @Test
    fun theDrawerSaysWhichToesAPawTakes() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        onNodeWithTag(ShellTags.pickTile("paw")).performClick()
        waitForIdle()
        onNodeWithText("0, 3\u20135").assertExists()
    }

    @Test
    fun aSliderOverValuesSkipsWhatTheyLeaveOut() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        val session = assertNotNull(state.session.value)
        val plan = shipped.plans.first()
        fun place(socketName: String) = openSockets(session.document.value, plan, shipped).first { it.name == socketName }.let { socket ->
            placeOnSocket(socket, assertNotNull(shipped.template(socket.takes.first())), session.document.value).also { session.send(AddParts(it)) }
        }
        place("front-leg")
        val paw = place("paw").first()
        state.selectedPart.value = paw.id
        onNodeWithContentDescription("Part").performClick()
        state.tool.value = BuildTool.NONE
        waitForIdle()
        val tagged = onNodeWithTag(ShellTags.parameter("toes"))
        fun set(to: Float): Float? {
            tagged.performSemanticsAction(SemanticsActions.SetProgress) { it(to) }
            waitForIdle()
            return session.document.value.character.resolved(paw.id)?.params?.get("toes")
        }
        assertEquals(3f, set(2f))
        assertEquals(0f, set(1f))
        assertEquals(4f, set(3.8f))
        assertEquals(5f, set(5f))
    }

    @Test
    fun theMenusRunActions() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithText("View").performClick()
        onNodeWithText("History").performClick()
        onNodeWithText("Add Torso").assertExists()
    }

    @Test
    fun homeListsARecentFileAndContinues() = runComposeUiTest {
        val file = Files.createTempFile("marsh-hare", ".json")
        val format = ScratchFormat()
        val new = newCharacter(caninePlan, shipped, "Marsh hare")
        FileStorage.write(file.absolutePathString(), format.encode(new.commands.fold(new.document) { d, c -> c.apply(d).document }))
        val state = AppState(shipped, recent = RecentFiles(listOf(file.absolutePathString())))
        show(state)
        onNodeWithTag(ShellTags.HOME).assertExists()
        onNodeWithTag(ShellTags.recent(file.nameWithoutExtension)).assertExists()
        waitUntil { onAllNodesWithText("Marsh hare").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(ShellTags.HOME_CONTINUE).performClick()
        waitUntil { state.session.value != null && state.screen.value == Screen.WORKSPACE }
        assertEquals("Marsh hare", state.session.value?.document?.value?.character?.name)
    }

    @Test
    fun clearingTheRecentListShowsTheEmptyHome() = runComposeUiTest {
        val state = AppState(shipped, recent = RecentFiles(listOf("gone.json")))
        show(state)
        onNodeWithText("Clear recent files").performClick()
        onNodeWithText("No projects").assertExists()
        onNodeWithText("New character").performClick()
        assertTrue(state.screen.value == Screen.NEW_CHARACTER)
    }

    @Test
    fun aFileThatWillNotOpenLeavesTheRecentList() = runComposeUiTest {
        val state = AppState(shipped, recent = RecentFiles(listOf("gone.json")))
        show(state)
        onNodeWithTag(ShellTags.HOME_CONTINUE).performClick()
        waitUntil { state.recent.entries.isEmpty() }
        assertEquals(Screen.HOME, state.screen.value)
    }

    @Test
    fun everyScreenHasTheFourMenusAndCantUndoWithNothingToTakeBack() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        for (title in listOf("File", "Edit", "View", "Help")) onNodeWithText(title).assertExists()
        val undo = onNodeWithContentDescription("Undo")
        undo.assertExists()
        onNodeWithContentDescription("Commands").assertExists()
        onNodeWithText("Shot").assertIsNotEnabled()
        onNodeWithText("Edit").performClick()
        onNodeWithText("Erase").assertExists()
        onNodeWithText("Mirror").assertIsNotEnabled()
    }

    @Test
    fun theStatusBarShowsHomesOwnFactsAfterTheWorkspace() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("0 drawn · 3 from template")
        state.closeCharacter()
        waitForIdle()
        onNodeWithTag(ShellTags.HOME).assertExists()
        onNodeWithTag(ShellTags.STATUS_END).assertDoesNotExist()
        assertEquals(0, onAllNodesWithText("0 drawn · 3 from template").fetchSemanticsNodes().size)
        onNodeWithText("Ready").assertExists()
        onNodeWithContentDescription("Settings").assertExists()
        onNodeWithContentDescription("Undo").assertDoesNotExist()
    }

    @Test
    fun theRailHoldsWhatTheBoardsDrawAndWhatIsNotBuiltDoesNotOpen() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        for (name in listOf("Parts", "Part types", "History", "Help", "Part", "Color")) onNodeWithContentDescription(name).assertIsEnabled()
        for (name in listOf("References", "Rig", "Slots", "Lines", "Marks")) onNodeWithContentDescription(name).assertIsNotEnabled()
    }

    @Test
    fun mirrorDecidesWhereAnEditLandsAndAnOverrideShowsItself() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithTag(ShellTags.tool(BuildTool.ERASE)).assertExists()
        onNodeWithTag(ShellTags.MIRROR).assertIsNotEnabled()
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        onNodeWithTag(ShellTags.PICK_DRAW).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.MIRROR).assertIsEnabled()
        onAllNodesWithTag(ShellTags.sideRow("front-leg.left")).assertCountEquals(0)

        onNodeWithTag(ShellTags.MIRROR).performClick()
        waitForIdle()
        assertEquals(false, state.mirror.value)
        onNodeWithTag(ShellTags.sideRow("front-leg.right")).performClick()
        waitForIdle()
        val session = assertNotNull(state.session.value)
        val right = assertNotNull(session.document.value.character.part(assertNotNull(state.selectedPart.value)))
        assertEquals(Side.RIGHT, right.side)

        val curve = OutlineCurve(List(4) { CurvePoint(it * 0.1f, 0.2f) })
        session.send(SetOutline(right.id, View.SIDE, curve, state.thisSide))
        val left = assertNotNull(session.document.value.twin(right))
        assertEquals(null, session.document.value.character.resolved(left).outlines[View.SIDE], "the other side keeps what it had")
        onNodeWithTag(ShellTags.MIRROR).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.sideRow("front-leg.right")).assertExists()
        onNodeWithText("differs").assertExists()
    }

    @Test
    fun addPartPlacesTheChosenTypeOnTheSocketItIsPressedOn() = runComposeUiTest {
        val state = AppState(shipped)
        createCharacter(state)
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        waitForIdle()
        val before = assertNotNull(state.session.value).document.value.character.parts.size
        val socket = assertNotNull(state.pickedSocket.value)
        state.pickTool.value = PickTool.ADD_PART
        state.placeOn(socket)
        waitForIdle()
        assertEquals(Screen.WORKSPACE, state.screen.value)
        assertTrue(assertNotNull(state.session.value).document.value.character.parts.size > before)
    }
}
