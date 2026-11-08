package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.center
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MouthShape
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.EARS_BACK
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.screens.surface.ExpressionTags
import dev.chieppa.squash.screens.surface.FACE_FRONT
import dev.chieppa.squash.screens.surface.FACE_WRITES_TAG
import dev.chieppa.squash.screens.surface.FaceTags
import dev.chieppa.squash.screens.surface.HeadAngleTags
import dev.chieppa.squash.screens.surface.LinesDrawerTags
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH = 1440
private const val HEIGHT = 900

private object NoFaceFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Face stage in the app whole, over the saved fennec: its tools, its inspector and the band of expressions and angle versions. */
@OptIn(ExperimentalTestApi::class)
class FaceFlowTest {
    private val work = TestWork()

    @AfterTest
    fun stop() = work.close()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoFaceFiles) }
        settle(work) { state.session.value?.graph?.settled?.value == true && state.session.value?.bodyLines?.output?.value != null }
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.pickEye(state: AppState) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Four) }
        waitForIdle()
        assertEquals(Stage.FACE, state.stage.value)
        assertEquals(BuildTool.DRAW_MARK, state.tool.value, "the Face stage opens on its tools")
        assertEquals(MarkGroupId("eye-l"), state.face.group?.id, "and on the head's first eye")
        assertEquals(MarkRole.LID, state.face.role)
    }

    private fun ComposeUiTest.stage(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun pickingAnEyeBringsItsToolsControlsAndStrips() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        pickEye(state)
        assertEquals(MarkGroupId("eye-l"), state.face.group?.id)
        assertEquals(HEAD, state.face.group?.part)
        onNodeWithTag(ShellTags.tool(BuildTool.GAZE_TARGET)).assertExists()
        onNodeWithTag(ShellTags.section("mirror")).assertDoesNotExist()
        onNodeWithTag(FaceTags.OPEN).performScrollTo().assertExists()
        onNodeWithTag(FaceTags.DILATION).performScrollTo().assertExists()
        onNodeWithTag(ExpressionTags.NEUTRAL).assertExists()
        onNodeWithTag(ExpressionTags.tile(EARS_BACK)).assertExists()
        onNodeWithTag(ExpressionTags.version(FACE_FRONT)).assertExists()

        onNodeWithTag(FaceTags.role(MarkRole.IRIS)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(MarkRole.IRIS, state.face.role)
        onNodeWithTag(FaceTags.role(MarkRole.LID), useUnmergedTree = true).performScrollTo().assertExists()

        onNodeWithTag(FaceTags.OPEN).performScrollTo().performMouseInput { click(Offset(1f, centerY)) }
        waitForIdle()
        fun open(id: String) = assertNotNull(session.document.value.character.resolved(HEAD)).surface.groups.first { it.id.value == id }.controls.open
        assertTrue(open("eye-l") < 0.5f, "Open closes the eye, at ${open("eye-l")}")
        assertTrue(open("eye-r") < 0.5f, "with Both, both")

        onNode(hasText("Right") and hasAnyAncestor(hasTestTag(FaceTags.WHICH_EYE))).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, state.face.bothEyes)
        assertEquals(MarkGroupId("eye-r"), state.face.group?.id)
        onNodeWithTag(FaceTags.OPEN).performScrollTo().performMouseInput { click(Offset(width - 1f, centerY)) }
        waitForIdle()
        assertTrue(open("eye-r") > 0.5f, "Right opens the right eye")
        assertTrue(open("eye-l") < 0.5f, "and leaves the left")

        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.G) }
        waitForIdle()
        assertEquals(BuildTool.GAZE_TARGET, state.tool.value)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.D) }
        waitForIdle()
        assertEquals(BuildTool.DRAW_MARK, state.tool.value, "D draws a mark here, not an outline")
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.L) }
        waitForIdle()
        assertEquals(BuildTool.DRAW_MARK, state.tool.value, "whiskers are Lines work, so L arms nothing here")
        onNodeWithTag(ShellTags.tool(BuildTool.GAZE_TARGET)).assertExists()
    }

    @Test
    fun anExpressionsTileShowsItOnTheCharacterAndMovesTheEars() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        pickEye(state)
        val before = assertNotNull(session.assembly.output.value?.part(EAR_L)?.pointOn(0.9f, 0f))
        onNodeWithTag(ExpressionTags.tile(EARS_BACK)).performScrollTo().performClick()
        settle(work) { session.graph.settled.value && session.assembly.output.value?.part(EAR_L)?.pointOn(0.9f, 0f) != before }
        assertEquals(EARS_BACK, session.document.value.character.shownExpression)
        assertNotEquals(before, session.assembly.output.value?.part(EAR_L)?.pointOn(0.9f, 0f))
        onNodeWithTag(ExpressionTags.NEUTRAL).performScrollTo().performClick()
        waitForIdle()
        assertEquals(null, session.document.value.character.shownExpression)

        onNode(hasTestTag(ExpressionTags.NEW), true).performClick()
        settle(work) { session.document.value.character.shownExpression != null }
        val made = session.document.value.character.shownExpression
        settle(work) { onAllNodes(hasTestTag(ExpressionTags.DELETE), true).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasTestTag(ExpressionTags.DELETE), true).performClick()
        waitForIdle()
        assertTrue(session.document.value.character.expressions.none { it.id == made })
    }

    @Test
    fun theMouthShowsNoShapeUntilOneIsPicked() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        pickEye(state)
        onNode(hasText("Mouth") and hasAnyAncestor(hasTestTag(FaceTags.FEATURE))).performScrollTo().performClick()
        waitForIdle()
        val mouth = assertNotNull(state.face.group)
        assertEquals(MarkRole.LIP_LINE, state.face.role)
        fun shape() = assertNotNull(session.document.value.character.resolved(HEAD)).surface.groups.first { it.id == mouth.id }.controls.mouthShape
        assertEquals(null, shape())
        onNode(hasText("Smile") and hasAnyAncestor(hasTestTag(FaceTags.SHAPE))).assertIsNotSelected()
        onNode(hasText("Smile") and hasAnyAncestor(hasTestTag(FaceTags.SHAPE))).performClick()
        waitForIdle()
        assertEquals(MouthShape.SMILE, shape())
        onNode(hasText("Smile") and hasAnyAncestor(hasTestTag(FaceTags.SHAPE))).assertIsSelected()
    }

    @Test
    fun aLockedVersionAndTheTestGazeLastOnlyWhileFaceIsUp() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        pickEye(state)
        onNodeWithTag(ExpressionTags.LOCK).assertIsNotEnabled()
        onNodeWithTag(ExpressionTags.version(FACE_FRONT)).performClick()
        waitForIdle()
        assertEquals(FACE_FRONT, state.face.version)
        onNodeWithTag(ExpressionTags.LOCK).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(FACE_FRONT, state.face.lockedVersion)
        val chips = onAllNodes(hasTestTag(FACE_WRITES_TAG), true).fetchSemanticsNodes().size
        assertTrue(chips > 1, "every pane names the version a stroke writes")
        assertEquals(chips, onAllNodes(hasText("Writes Front version"), true).fetchSemanticsNodes().size)

        stage(Key.G)
        assertEquals(BuildTool.GAZE_TARGET, state.tool.value)
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput { click(center + Offset(width / 4f, 0f)) }
        waitForIdle()
        assertNotNull(state.face.gazeTarget, "the Gaze tool moves the handle")

        stage(Key.One)
        assertEquals(Stage.SHAPE, state.stage.value)
        assertEquals(null, state.face.version, "the picked version goes with the band")
        assertEquals(false, state.face.locked)
        assertEquals(null, state.face.gazeTarget, "and the test gaze")
    }

    @Test
    fun theHeadAngleShowsOverSingleWhileAFaceIsPicked() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        state.layout.value = SheetLayout.SINGLE
        waitForIdle()
        onNodeWithTag(HeadAngleTags.SLIDER).assertDoesNotExist()
        pickEye(state)
        onNodeWithTag(FACE_WRITES_TAG, useUnmergedTree = true).assertExists()
        onNodeWithTag(HeadAngleTags.SLIDER).performMouseInput { click(center) }
        waitForIdle()
        onNodeWithTag(HeadAngleTags.VERSION).assertExists()
        onNodeWithText("¾ version").assertExists()
    }
}
