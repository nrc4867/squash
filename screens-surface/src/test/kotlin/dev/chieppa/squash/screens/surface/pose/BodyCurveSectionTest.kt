package dev.chieppa.squash.screens.surface.pose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.View
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.bendTo
import dev.chieppa.squash.rig.curveBones
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SQUASHED = 0.92f
private const val DIP = 0.08f

/** The Pose inspector with Body curve armed: the curve's section, the paws, and the chain's bends. */
@OptIn(ExperimentalTestApi::class)
class BodyCurveSectionTest {
    private val work = TestWork()

    private val paws = listOf("front-paw-l", "front-paw-r", "back-paw-l", "back-paw-r").map(::PartId).toSet()

    private fun ComposeUiTest.opened(): AppState {
        val state = AppState(shipped, work).also {
            it.open(savedFennec())
            it.tool.value = BuildTool.BODY_CURVE
        }
        val session = checkNotNull(state.session.value)
        setContent {
            StretchTheme(StretchThemes.Ink) {
                Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) { PoseInspector(state, session) }
            }
        }
        settle(work) { state.rigNow() != null && session.graph.settled.value }
        waitForIdle()
        return state
    }

    /** A Side stroke through the chain's joints at rest, sagging in the middle, from the tail's tip to the head's end. */
    private fun sag(rig: Rig): BodyStroke {
        val bones = checkNotNull(curveBones(rig))
        val pivots = (bones.behind.reversed() + bones.spine + bones.ahead).mapNotNull { rig.bone(it)?.pivot }
        val head = checkNotNull(rig.bone(bones.ahead.last())).let { it.pivot + it.direction * it.length }
        val tail = checkNotNull(rig.bone(bones.behind.last())).let { it.pivot + it.direction * it.length }
        val through = listOf(tail) + pivots + head
        val points = through.mapIndexed { i, p -> CurvePoint(p.x, p.y - DIP * kotlin.math.sin(Math.PI * i / through.lastIndex).toFloat()) }
        return BodyStroke(View.SIDE, listOf(points.first()) + points.zipWithNext().flatMap { (a, b) ->
            listOf(CurvePoint(a.x + (b.x - a.x) / 3f, a.y + (b.y - a.y) / 3f), CurvePoint(a.x + 2f * (b.x - a.x) / 3f, a.y + 2f * (b.y - a.y) / 3f), b)
        })
    }

    private fun steps(state: AppState) = checkNotNull(state.session.value).history.steps.value.done.size

    private fun pose(state: AppState) = checkNotNull(state.session.value).document.value.character.pose

    @Test
    fun theCurvesSectionComesFirstWithThePawsAndTheChainsBends() = runComposeUiTest {
        val state = opened()
        val session = checkNotNull(state.session.value)
        val rig = checkNotNull(state.rigNow())
        onNodeWithText("Body curve", ignoreCase = true, useUnmergedTree = true).assertExists()
        onNodeWithTag(BodyCurveTags.LENGTH).assertExists()
        onNodeWithTag(BodyCurveTags.KEEP_VOLUME).assertExists()
        onNodeWithTag(BodyCurveTags.CLEAR).assertDoesNotExist()
        onNodeWithTag(BodyCurveTags.LENGTH_CHIP).assertDoesNotExist()
        for (paw in paws) onNodeWithTag(PoseInspectorTags.pin(paw)).assertExists()
        for (row in listOf("spine", "neck", "head", "tail")) onNodeWithTag(BodyCurveTags.bend(row), useUnmergedTree = true).assertExists()
        onNodeWithTag(PoseInspectorTags.FIRM).assertDoesNotExist()
        onNodeWithTag(PoseInspectorTags.RESET_POSE).assertDoesNotExist()

        val solver = state.pose.solverFor(rig)
        session.send(SetPose(solver.bendTo(Pose(planted = paws), BodyCurve(listOf(sag(rig)), SQUASHED)).pose))
        waitForIdle()
        onNodeWithText("92%", useUnmergedTree = true).assertExists()
        onNodeWithText("squash", useUnmergedTree = true).assertExists()
        onNodeWithTag(BodyCurveTags.bend("spine"), useUnmergedTree = true).assertExists()
        onNodeWithText("0°", useUnmergedTree = true).assertDoesNotExist()

        val before = steps(state)
        onNodeWithTag(BodyCurveTags.LENGTH).performMouseInput {
            moveTo(Offset(width * 0.42f, height / 2f))
            press()
            moveTo(Offset(width * 0.6f, height / 2f))
            moveTo(Offset(width * 0.8f, height / 2f))
            release()
        }
        waitForIdle()
        assertEquals(before + 1, steps(state), "a slider drag is one step")
        val stretched = checkNotNull(pose(state).curve).length
        assertTrue(stretched > 1f, "dragged right, the curve stretches: $stretched")
        onNodeWithText("stretch", useUnmergedTree = true).assertExists()

        onNodeWithTag(BodyCurveTags.KEEP_VOLUME).performClick()
        waitForIdle()
        val bones = checkNotNull(curveBones(rig))
        assertTrue(bones.stretched.all { pose(state).of(it.part, it.joint)?.keepVolume == false }, "every stretched joint lengthens alone")

        onNodeWithTag(BodyCurveTags.CLEAR).performScrollTo().performClick()
        waitForIdle()
        assertIs<SetPose>(session.history.steps.value.done.last())
        assertNull(pose(state).curve)
        val chain = (bones.behind + bones.spine + bones.ahead).toSet()
        assertTrue(pose(state).bones.none { (it.part to it.joint) in chain.map { b -> b.part to b.joint } && !it.isRest }, "the chain rests")
        assertNotEquals(before, steps(state))
        assertEquals(paws, pose(state).planted)
    }
}
