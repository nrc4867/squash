package dev.chieppa.squash.screens.surface.rig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ResetSeam
import dev.chieppa.squash.document.SeamBrush
import dev.chieppa.squash.document.SeamSide
import dev.chieppa.squash.document.SeamStroke
import dev.chieppa.squash.document.SeamStrokeId
import dev.chieppa.squash.document.SeamToTwin
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetSeamStroke
import dev.chieppa.squash.document.seamStrokes
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val LEG = PartId("front-leg-l")
private val TWIN = PartId("front-leg-r")

@OptIn(ExperimentalTestApi::class)
class RigInspectorTest {
    private val work = TestWork()

    private fun opened(): AppState = AppState(shipped, work).also {
        it.open(savedFennec())
        it.tool.value = BuildTool.PAINT_WEIGHT
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showInspector(state: AppState) {
        val session = checkNotNull(state.session.value)
        setContent {
            StretchTheme(StretchThemes.Ink) {
                Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) { RigInspector(state, session) }
            }
        }
        settle(work) { state.rigNow() != null && session.graph.settled.value }
    }

    private fun lastCommand(state: AppState) = checkNotNull(state.session.value).history.steps.value.done.last()

    @Test
    fun theInspectorHoldsTheToolsOptionsThenBonesThenSeams() = runComposeUiTest {
        val state = opened()
        showInspector(state)
        onNodeWithText("Paint weight", ignoreCase = true, useUnmergedTree = true).assertExists()
        onNodeWithTag(RigInspectorTags.SIZE).assertExists()
        onNodeWithTag(RigInspectorTags.STRENGTH).assertExists()
        onNodeWithText("This side", ignoreCase = true, useUnmergedTree = true).assertExists()
        onNodeWithText("Bones", ignoreCase = true, useUnmergedTree = true).assertExists()
        onNodeWithText("Seams", ignoreCase = true, useUnmergedTree = true).assertExists()
        onNodeWithTag(RigBonesTags.part(PartId("torso"))).assertExists()
        onNodeWithTag(RigSeamsTags.seam(LEG)).assertExists()
        onNodeWithTag(RigSeamsTags.paired(LEG), useUnmergedTree = true).assertExists()
        onNodeWithTag(RigSeamsTags.paired(PartId("tail")), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aJointsRowPicksAndWigglesItsBoneAndTheRightSideStartsClosed() = runComposeUiTest {
        val state = opened()
        showInspector(state)
        onNodeWithTag(RigBonesTags.joint(TWIN, "knee")).assertDoesNotExist()
        onNodeWithTag(RigBonesTags.joint(LEG, "knee")).performScrollTo().performClick()
        onNodeWithTag(RigBonesTags.joint(LEG, "knee")).assertIsSelected()
        assertEquals(BoneName(LEG, "knee"), state.rig.picked)
        assertEquals(BoneName(LEG, "knee"), state.rig.wiggle?.bone)
        onNodeWithTag(RigInspectorTags.BONE).assertExists()
        onNodeWithTag(RigBonesTags.part(TWIN)).performScrollTo().performClick()
        onNodeWithTag(RigBonesTags.joint(TWIN, "knee")).assertExists()
    }

    @Test
    fun aReachStepSendsOneCommandForThePair() = runComposeUiTest {
        val state = opened()
        showInspector(state)
        val session = checkNotNull(state.session.value)
        val before = session.history.steps.value.done.size
        onNodeWithContentDescription("More reach for Front leg L · knee").performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 1, session.history.steps.value.done.size)
        val sent = assertIs<SetParameter>(lastCommand(state))
        assertEquals(RigParams.reach("knee"), sent.name)
        assertEquals(1.1f, checkNotNull(sent.value), 1e-4f)
        assertEquals(false, sent.thisSide)
        assertEquals(1.1f, checkNotNull(session.document.value.character.resolved(TWIN)).params.getValue(RigParams.reach("knee")), 1e-4f)
    }

    @Test
    fun theSeamsButtonsActOnThePickedSeam() = runComposeUiTest {
        val state = opened()
        val session = checkNotNull(state.session.value)
        val anchor = dev.chieppa.squash.document.Anchor(LEG, u = 0.05f, v = 0.25f)
        val stroke = SeamStroke(SeamStrokeId("s"), listOf(anchor), SeamBrush.PAINT, SeamSide.PARENT, 0.08f, 1f)
        session.send(SetSeamStroke(LEG, stroke.id, stroke, thisSide = true))
        showInspector(state)
        onNodeWithTag(RigSeamsTags.strokes(LEG), useUnmergedTree = true).assertExists()
        onNodeWithTag(RigSeamsTags.strokes(TWIN), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(RigSeamsTags.seam(LEG)).performScrollTo().performClick()
        onNodeWithTag(RigSeamsTags.TO_TWIN).performScrollTo().performClick()
        waitForIdle()
        assertIs<SeamToTwin>(lastCommand(state))
        assertEquals(1, session.document.value.character.seamStrokes(TWIN).size)
        onNodeWithTag(RigSeamsTags.RESET).performScrollTo().performClick()
        waitForIdle()
        assertIs<ResetSeam>(lastCommand(state))
        assertTrue(session.document.value.character.seamStrokes(LEG).isEmpty())
        val steps = session.history.steps.value.done.size
        onNodeWithTag(RigSeamsTags.TIDY).performScrollTo().performClick()
        waitForIdle()
        assertEquals(steps, session.history.steps.value.done.size, "an unpainted seam has nothing to tidy")
    }
}
