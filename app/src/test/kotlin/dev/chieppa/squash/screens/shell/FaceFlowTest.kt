package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MouthShapeName
import dev.chieppa.squash.document.MouthShapeValues
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SetMouthShape
import dev.chieppa.squash.character.model.MouthStyle
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
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.screens.surface.ExpressionTags
import dev.chieppa.squash.screens.surface.FACE_FRONT
import dev.chieppa.squash.screens.surface.FACE_WRITES_TAG
import dev.chieppa.squash.screens.surface.FaceTags
import dev.chieppa.squash.screens.surface.PaintedEyeTags
import dev.chieppa.squash.screens.surface.EyeLidTags
import dev.chieppa.squash.document.LidPose
import dev.chieppa.squash.screens.surface.HeadAngleTags
import dev.chieppa.squash.screens.surface.TeethTags
import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.MouthTeeth
import dev.chieppa.squash.document.ToothSlotChange
import dev.chieppa.squash.document.TongueLevel
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900

/** A mouth style whose shapes are held as data only. */
private const val LONG_MUZZLE = "long-muzzle"

private object NoFaceFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Face stage in the app whole, over the saved fennec: its tools, its inspector and the band of expressions and angle versions. */
@Tag("integration")
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
    fun aHeadsPaintedEyeShowsThreeRowsAndMoreWithNoAngleVersions() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
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
        assertFalse(eye().flat, "an eye follows the head's curve until made flat")
        onNodeWithTag(PaintedEyeTags.FLAT).performScrollTo().performClick()
        waitForIdle()
        assertTrue(eye().flat, "Flat lays the eye flat across the face")
    }

    @Test
    fun anEyesLidsStandByCommandAndBlinkWithNoEdit() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        stage(Key.Four)
        fun eye() = session.document.value.character.paintedEyes.single()
        onNodeWithTag(EyeLidTags.slider("upper-close")).assertDoesNotExist()
        onNode(hasText("LIDS") and hasAnyAncestor(hasTestTag(EyeLidTags.SECTION))).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(EyeLidTags.slider("upper-close")).performScrollTo().performMouseInput { click(Offset(width - 1f, centerY)) }
        waitForIdle()
        assertTrue(eye().upperClose > 0.95f, "Upper close stands the upper lid shut, at ${eye().upperClose}")
        session.history.undo()
        waitForIdle()
        assertEquals(0f, eye().upperClose, "one drag is one step of undo")

        val before = session.document.value
        onNodeWithTag(EyeLidTags.BLINK).performScrollTo().performClick()
        waitForIdle()
        assertEquals(LidPose.BLINK, session.sessionSurface.lidPreview.value, "Blink shows the lids shut")
        assertSame(before, session.document.value, "and edits nothing")
        onNodeWithTag(EyeLidTags.BLINK).performClick()
        waitForIdle()
        assertNull(session.sessionSurface.lidPreview.value)
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
    fun aLongMuzzleShowsNoShapesAndOnLipsPickingAShapePreviewsItWithoutAnEditAndResetTakesTheStylesBack() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        pickMouth(state)
        val mouth = assertNotNull(state.face.group)
        fun group() = assertNotNull(session.document.value.character.resolved(HEAD)).surface.groups.first { it.id == mouth.id }
        onNodeWithTag(FaceTags.SHAPES).assertExists()
        session.send(SetMarkGroup(HEAD, mouth.id, group().copy(style = LONG_MUZZLE)))
        settle(work) { state.session.value?.graph?.settled?.value == true }
        waitForIdle()
        onNodeWithTag(FaceTags.SHAPES).assertDoesNotExist()
        session.send(SetMarkGroup(HEAD, mouth.id, group().copy(style = MouthStyle.LIPS)))
        settle(work) { state.session.value?.graph?.settled?.value == true }
        waitForIdle()
        val before = session.document.value
        onNodeWithTag(FaceTags.shapeControl("open")).assertDoesNotExist()
        onNodeWithTag(FaceTags.shape(MouthShapeName.O)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(MouthShapeName.O, session.mouthPreview.value)
        assertEquals(before, session.document.value, "picking a shape is no edit")
        onNodeWithTag(FaceTags.shapeControl("round")).assertExists()
        onNodeWithTag(FaceTags.SHAPE_RESET).assertIsNotEnabled()
        session.send(SetMouthShape(HEAD, mouth.id, MouthShapeName.O, MouthShapeValues(round = 0.4f)))
        waitForIdle()
        onNodeWithTag(FaceTags.SHAPE_RESET).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(emptyMap(), group().shapes, "Reset takes the style's shape back")
        onNodeWithTag(FaceTags.shape(MouthShapeName.O)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(null, session.mouthPreview.value, "picking it again lets it go")
    }

    @Test
    fun aMouthsTeethStartFromItsStylesPresetAndKeepOnlyWhatTheyChange() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        pickMouth(state)
        val mouth = assertNotNull(state.face.group)
        fun teeth() = assertNotNull(session.document.value.character.resolved(HEAD)).surface.groups.first { it.id == mouth.id }.teeth
        assertEquals(MouthTeeth(), teeth())
        onNodeWithTag(TeethTags.RESET).performScrollTo().assertIsNotEnabled()

        onNodeWithTag(TeethTags.setting("length")).performScrollTo().performMouseInput { click(Offset(width - 1f, centerY)) }
        waitForIdle()
        val longer = teeth().upper.slots.single()
        assertEquals(ToothSlotChange(row = 0, slot = 0, length = longer.length), longer, "a drag keeps only the length it set")
        assertNotNull(longer.length)

        onNodeWithTag(TeethTags.OVER).performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, teeth().upper.slots.single().over)
        onNodeWithTag(TeethTags.RESET).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(MouthTeeth(), teeth(), "Reset takes the preset's slot back")

        onNodeWithTag(TeethTags.ADD_SLOT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, teeth().upper.added.size)
        onNode(hasText("Bar") and hasAnyAncestor(hasTestTag(TeethTags.level(JawName.LOWER)))).performScrollTo().performClick()
        waitForIdle()
        assertEquals(TeethLevel.BAR, teeth().lower.level)
        onNode(hasText("Blocks") and hasAnyAncestor(hasTestTag(TeethTags.level(JawName.LOWER)))).performScrollTo().performClick()
        waitForIdle()
        assertEquals(TeethLevel.BLOCKS, teeth().lower.level, "on a jaw built open blocks is kept, the preset's blocks starting at shaped")
        onNode(hasText("Shaped") and hasAnyAncestor(hasTestTag(TeethTags.level(JawName.LOWER)))).performScrollTo().performClick()
        waitForIdle()
        assertNull(teeth().lower.level, "the level the preset starts at keeps nothing")
        onNode(hasText("None") and hasAnyAncestor(hasTestTag(TeethTags.TONGUE))).performScrollTo().performClick()
        waitForIdle()
        assertEquals(TongueLevel.NONE, session.document.value.character.resolved(HEAD)?.surface?.groups?.first { it.id == mouth.id }?.tongueLevel)
    }

    @Test
    fun aLockedVersionLastsOnlyWhileFaceIsUp() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
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

        stage(Key.One)
        assertEquals(Stage.SHAPE, state.stage.value)
        assertEquals(null, state.face.version, "the picked version goes with the band")
        assertEquals(false, state.face.locked)
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
