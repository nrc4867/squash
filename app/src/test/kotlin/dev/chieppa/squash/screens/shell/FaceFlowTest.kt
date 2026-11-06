package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.center
import androidx.compose.ui.test.centerY
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
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.EARS_BACK
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.surface.ExpressionTags
import dev.chieppa.squash.screens.surface.FaceTags
import dev.chieppa.squash.screens.surface.FACE_FRONT
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import dev.chieppa.squash.screens.shell.Stage

private const val SETTLE_MILLIS = 30_000L
private const val WIDTH = 1440
private const val HEIGHT = 900

private object NoFaceFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Face stage in the app whole, over the saved fennec: its tools, its inspector and the band of expressions and angle versions. */
@OptIn(ExperimentalTestApi::class)
class FaceFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), scope)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoFaceFiles) }
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.session.value?.graph?.settled?.value == true && state.session.value?.bodyLines?.output?.value != null }
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.pickEye(state: AppState) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Four) }
        waitForIdle()
        assertEquals(Stage.FACE, state.stage.value)
        assertEquals(BuildTool.DRAW_MARK, state.tool.value, "the Face stage opens on its tools")
        onNodeWithTag(FaceTags.EYE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(BuildTool.DRAW_MARK, state.tool.value, "picking a face row leaves the tool as it was")
    }

    @Test
    fun pickingAnEyeBringsItsToolsControlsAndStrips() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        pickEye(state)
        assertEquals(MarkGroupId("eye-l"), state.face.group?.id)
        assertEquals(HEAD, state.selectedPart.value)
        onNodeWithTag(ShellTags.tool(BuildTool.GAZE_TARGET)).assertExists()
        onNodeWithTag(FaceTags.OPEN).performScrollTo().assertExists()
        onNodeWithTag(FaceTags.DILATION).performScrollTo().assertExists()
        onNodeWithTag(ExpressionTags.NEUTRAL).assertExists()
        onNodeWithTag(ExpressionTags.tile(EARS_BACK)).assertExists()
        onNodeWithTag(ExpressionTags.version(FACE_FRONT)).assertExists()

        onNodeWithTag(FaceTags.role(MarkRole.IRIS)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(MarkRole.IRIS, state.face.role)

        onNodeWithTag(FaceTags.OPEN).performScrollTo().performMouseInput { click(Offset(1f, centerY)) }
        waitForIdle()
        fun open(id: String) = assertNotNull(session.document.value.character.resolved(HEAD)).surface.groups.first { it.id.value == id }.controls.open
        assertTrue(open("eye-l") < 0.5f, "Open closes the eye, at ${open("eye-l")}")
        assertTrue(open("eye-r") < 0.5f, "with Mirror on, both")

        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.G) }
        waitForIdle()
        assertEquals(BuildTool.GAZE_TARGET, state.tool.value)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.D) }
        waitForIdle()
        assertEquals(BuildTool.DRAW_MARK, state.tool.value, "D draws a mark here, not an outline")
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.L) }
        waitForIdle()
        assertEquals(BuildTool.FLOATING_MARK, state.tool.value, "L draws a whisker from the face")
        onNodeWithTag(ShellTags.tool(BuildTool.GAZE_TARGET)).assertExists()
        onNodeWithTag(LinesDrawerTags.STROKE_COLOR).performScrollTo().assertExists()
    }

    @Test
    fun anExpressionsTileShowsItOnTheCharacterAndMovesTheEars() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        pickEye(state)
        val before = assertNotNull(session.assembly.output.value?.part(EAR_L)?.pointOn(0.9f, 0f))
        onNodeWithTag(ExpressionTags.tile(EARS_BACK)).performScrollTo().performClick()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && session.assembly.output.value?.part(EAR_L)?.pointOn(0.9f, 0f) != before }
        assertEquals(EARS_BACK, session.document.value.character.shownExpression)
        assertNotEquals(before, session.assembly.output.value?.part(EAR_L)?.pointOn(0.9f, 0f))
        onNodeWithTag(ExpressionTags.NEUTRAL).performScrollTo().performClick()
        waitForIdle()
        assertEquals(null, session.document.value.character.shownExpression)

        onNode(hasTestTag(ExpressionTags.NEW), true).performClick()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.document.value.character.shownExpression != null }
        val made = session.document.value.character.shownExpression
        waitUntil(timeoutMillis = SETTLE_MILLIS) { onAllNodes(hasTestTag(ExpressionTags.DELETE), true).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasTestTag(ExpressionTags.DELETE), true).performClick()
        waitForIdle()
        assertTrue(session.document.value.character.expressions.none { it.id == made })
    }

    @Test
    fun theHeadAngleShowsOverSingleWhileAFaceIsPicked() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        state.layout.value = SheetLayout.SINGLE
        waitForIdle()
        onNodeWithTag(HeadAngleTags.SLIDER).assertDoesNotExist()
        pickEye(state)
        onNodeWithTag(HeadAngleTags.SLIDER).performMouseInput { click(center) }
        waitForIdle()
        onNodeWithTag(HeadAngleTags.VERSION).assertExists()
        onNodeWithText("¾ version").assertExists()
    }
}
