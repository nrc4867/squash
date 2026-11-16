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
import dev.chieppa.squash.screens.surface.PaintedEyeTags
import dev.chieppa.squash.screens.surface.HeadAngleTags
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
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

    /** The saved fennec open in the app. */
    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoFaceFiles) }
        settle(work) { state.session.value?.graph?.settled?.value == true && state.session.value?.bodyLines?.output?.value != null }
        waitForIdle()
        return state
    }

    /** Opens the Face stage on the head's mouth, a drawn group with angle versions. */
    private fun ComposeUiTest.pickMouth(state: AppState) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Four) }
        waitForIdle()
        assertEquals(Stage.FACE, state.stage.value)
        onNode(hasText("Mouth") and hasAnyAncestor(hasTestTag(FaceTags.FEATURE))).performScrollTo().performClick()
        waitForIdle()
        assertEquals(MarkRole.LIP_LINE, state.face.role)
    }

    private fun ComposeUiTest.stage(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun aOneSurfaceHeadsPaintedEyeShowsThreeRowsAndMoreWithNoAngleVersions() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        stage(Key.Four)
        fun eye() = session.document.value.character.paintedEyes.single()
        val look = eye().look
        onNodeWithTag(PaintedEyeTags.ROUNDNESS).performScrollTo().assertExists()
        onNodeWithTag(PaintedEyeTags.PUPIL).performScrollTo().assertExists()
        listOf("iris", "pupil", "white").forEach { onNodeWithTag(PaintedEyeTags.fill(it)).performScrollTo().assertExists() }
        onNodeWithTag(PaintedEyeTags.FROM_FRONT).assertDoesNotExist()
        onNodeWithTag(ExpressionTags.version(FACE_FRONT)).assertDoesNotExist()
        onNodeWithTag(ExpressionTags.NEUTRAL).assertExists()
        onNodeWithTag(FACE_WRITES_TAG).assertDoesNotExist()

        onNodeWithTag(PaintedEyeTags.ROUNDNESS).performMouseInput { click(Offset(width - 1f, centerY)) }
        waitForIdle()
        assertTrue(eye().look.height / eye().look.width > look.height / look.width, "Roundness rounds the eye")
        onNode(hasText("Slit") and hasAnyAncestor(hasTestTag(PaintedEyeTags.PUPIL))).performClick()
        waitForIdle()
        assertTrue(eye().look.slit && assertNotNull(eye().front).slit, "Slit sets both looks' pupil")

        onNode(hasText("MORE") and hasAnyAncestor(hasTestTag(PaintedEyeTags.MORE))).performScrollTo().performClick()
        waitForIdle()
        listOf(PaintedEyeTags.FROM_FRONT, PaintedEyeTags.HIGHLIGHT, PaintedEyeTags.GAZE).forEach { onNodeWithTag(it).performScrollTo().assertExists() }
        listOf("brow", "lashes", "lid").forEach { onNodeWithTag(PaintedEyeTags.line(it)).performScrollTo().assertExists() }
        onNodeWithTag(PaintedEyeTags.FROM_FRONT).performMouseInput { click(Offset(1f, centerY)) }
        waitForIdle()
        assertTrue(eye().fromFront < 0.05f, "From front at its least shows one look at every angle, at ${eye().fromFront}")
        onNodeWithTag(PaintedEyeTags.line("brow")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(!look.brow, eye().look.brow, "the brow's switch")
    }

    @Test
    fun anExpressionsTileShowsItOnTheCharacterAndMovesTheEars() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        stage(Key.Four)
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
        pickMouth(state)
        val mouth = assertNotNull(state.face.group)
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
        pickMouth(state)
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
        pickMouth(state)
        onNodeWithTag(FACE_WRITES_TAG, useUnmergedTree = true).assertExists()
        onNodeWithTag(HeadAngleTags.SLIDER).performMouseInput { click(center) }
        waitForIdle()
        onNodeWithTag(HeadAngleTags.VERSION).assertExists()
        onNodeWithText("¾ version").assertExists()
    }
}
