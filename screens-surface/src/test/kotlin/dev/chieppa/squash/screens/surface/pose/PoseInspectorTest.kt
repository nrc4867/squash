package dev.chieppa.squash.screens.surface.pose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ResetPose
import dev.chieppa.squash.document.SetBoneMode
import dev.chieppa.squash.document.SetKeepVolume
import dev.chieppa.squash.document.SetPlanted
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.curlOf
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.person.savedPerson
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.squash.screens.toucan.savedToucan
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val PAW = PartId("front-paw-l")
private val LEG = PartId("front-leg-l")
private val HAND_L = PartId("hand-l")
private val HAND_R = PartId("hand-r")

@OptIn(ExperimentalTestApi::class)
class PoseInspectorTest {
    private val work = TestWork()

    private fun opened(tool: BuildTool = BuildTool.DRAG): AppState = AppState(shipped, work).also {
        it.open(savedFennec())
        it.tool.value = tool
    }

    private fun ComposeUiTest.showInspector(state: AppState) {
        val session = checkNotNull(state.session.value)
        setContent {
            StretchTheme(StretchThemes.Ink) {
                Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) { PoseInspector(state, session) }
            }
        }
        settle(work) { state.rigNow() != null && session.graph.settled.value }
        waitForIdle()
    }

    private fun lastCommand(state: AppState) = checkNotNull(state.session.value).history.steps.value.done.last()

    private fun steps(state: AppState) = checkNotNull(state.session.value).history.steps.value.done.size

    @Test
    fun anUprightCharactersFeetAreItsPlantedRowsAndItsHandsAreNot() = runComposeUiTest {
        val state = AppState(shipped, work).also {
            it.open(savedPerson())
            it.tool.value = BuildTool.DRAG
        }
        showInspector(state)
        onNodeWithText("Left foot", useUnmergedTree = true).assertExists()
        onNodeWithText("Right foot", useUnmergedTree = true).assertExists()
        onNodeWithTag(PoseInspectorTags.pin(PartId("foot-l"))).assertExists()
        onNodeWithTag(PoseInspectorTags.pin(PartId("hand-l"))).assertDoesNotExist()
        onNodeWithTag(PoseInspectorTags.pin(PartId("hand-r"))).assertDoesNotExist()
        onNodeWithText("Right hand", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aPickedHandCurlsItsFingersInOneStepAndTheTwinsWithMirror() = runComposeUiTest {
        val state = AppState(shipped, work).also {
            it.open(savedPerson())
            it.tool.value = BuildTool.DRAG
            it.pose.limb = HAND_L
        }
        showInspector(state)
        val before = steps(state)
        onNodeWithTag(PoseInspectorTags.CURL).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        waitForIdle()
        assertEquals(before + 1, steps(state), "the curl is one step")
        val rig = checkNotNull(state.rigNow())
        val pose = checkNotNull(state.session.value).document.value.character.pose
        assertEquals(1f, checkNotNull(rig.curlOf(pose, HAND_L)), 1e-4f)
        assertEquals(1f, checkNotNull(rig.curlOf(pose, HAND_R)), 1e-4f, "Mirror curls the twin")
    }

    @Test
    fun aPickedFingerJointNamesItsDigitInTheTurnSection() = runComposeUiTest {
        val state = AppState(shipped, work).also {
            it.open(savedPerson())
            it.tool.value = BuildTool.TURN
            it.pose.pick(BoneName(HAND_L, "fingers:1.2"), false)
        }
        showInspector(state)
        onNodeWithText("Index 2", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("-10° to 90°", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aBirdsFeetAreItsPlantedRowsAndItsWingsAreNot() = runComposeUiTest {
        val state = AppState(shipped, work).also {
            it.open(savedToucan())
            it.tool.value = BuildTool.DRAG
        }
        showInspector(state)
        onNodeWithText("Left foot", useUnmergedTree = true).assertExists()
        onNodeWithText("Right foot", useUnmergedTree = true).assertExists()
        onNodeWithTag(PoseInspectorTags.pin(PartId("foot-l"))).assertExists()
        onNodeWithTag(PoseInspectorTags.pin(PartId("foot-r"))).assertExists()
        onNodeWithTag(PoseInspectorTags.pin(PartId("wing-l"))).assertDoesNotExist()
        onNodeWithTag(PoseInspectorTags.pin(PartId("wing-r"))).assertDoesNotExist()
    }

    @Test
    fun dragsOptionsAreAppStateAndWriteNothing() = runComposeUiTest {
        val state = opened()
        showInspector(state)
        onNodeWithText("Drag", ignoreCase = true, useUnmergedTree = true).assertExists()
        val before = steps(state)
        onNodeWithTag(PoseInspectorTags.FIRM).performClick()
        onNodeWithTag(PoseInspectorTags.STRETCH).performClick()
        waitForIdle()
        assertTrue(state.pose.firm && state.pose.stretch)
        assertEquals(before, steps(state), "Firm and Stretch stay out of the document")
        onNodeWithTag(PoseInspectorTags.LIMB_PLANTED).assertDoesNotExist()
        onNodeWithText("Planted", ignoreCase = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun aPickedLimbShowsItsSectionThenItsKneesTurn() = runComposeUiTest {
        val state = opened()
        state.pose.limb = PAW
        showInspector(state)
        onAllNodesWithText("Front paw L", ignoreCase = true, useUnmergedTree = true).onFirst().assertExists()
        for (tag in listOf(PoseInspectorTags.LIMB_PLANTED, PoseInspectorTags.RIDES, PoseInspectorTags.BEND, PoseInspectorTags.LENGTH, PoseInspectorTags.KEEP_VOLUME)) onNodeWithTag(tag).assertExists()
        onNodeWithText("Front leg L · knee", useUnmergedTree = true).assertExists()
        onNodeWithText("0° to 140°", useUnmergedTree = true).assertExists()
        for (tag in listOf(PoseInspectorTags.TIP, PoseInspectorTags.TURN, PoseInspectorTags.LEAN)) onNodeWithTag(tag).assertExists()

        onNodeWithTag(PoseInspectorTags.LIMB_PLANTED).performClick()
        waitForIdle()
        assertEquals(SetPlanted(PAW, true), lastCommand(state))
        onNodeWithTag(PoseInspectorTags.KEEP_VOLUME).performClick()
        waitForIdle()
        assertEquals(SetKeepVolume(LEG, "ankle", false, bothSides = true), lastCommand(state))
        onNodeWithText("Locked", useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
        assertEquals(SetBoneMode(LEG, "knee", JointMode.LOCKED, bothSides = true), lastCommand(state))
        onNodeWithTag(PoseInspectorTags.RESET_POSE).performScrollTo().performClick()
        waitForIdle()
        assertIs<ResetPose>(lastCommand(state))
    }

    @Test
    fun eachPawHasAPinRow() = runComposeUiTest {
        val state = opened(BuildTool.PIN)
        showInspector(state)
        val back = PartId("back-paw-r")
        onNodeWithTag(PoseInspectorTags.pin(back)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(SetPlanted(back, true), lastCommand(state))
        for (paw in listOf("front-paw-l", "front-paw-r", "back-paw-l")) onNodeWithTag(PoseInspectorTags.pin(PartId(paw))).assertExists()
    }
}
