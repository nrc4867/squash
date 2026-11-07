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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ResetPose
import dev.chieppa.squash.document.SetBoneMode
import dev.chieppa.squash.document.SetKeepVolume
import dev.chieppa.squash.document.SetPlanted
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.person.savedPerson
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 60_000L
private val PAW = PartId("front-paw-l")
private val LEG = PartId("front-leg-l")

@OptIn(ExperimentalTestApi::class)
class PoseInspectorTest {
    private fun opened(tool: BuildTool = BuildTool.DRAG): AppState = AppState(shipped).also {
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
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.rigNow() != null && session.graph.settled.value }
        waitForIdle()
    }

    private fun lastCommand(state: AppState) = checkNotNull(state.session.value).history.steps.value.done.last()

    private fun steps(state: AppState) = checkNotNull(state.session.value).history.steps.value.done.size

    @Test
    fun anUprightCharactersFeetAreItsPlantedRowsAndItsHandsAreNot() = runComposeUiTest {
        val state = AppState(shipped).also {
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
