package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Poses sent one after another, as a drag sends one every frame. */
private const val STEPS = 3

/**
 * What one `SetPose` costs the fennec's graph, as a drag sends one every frame: a paw lifted by its leg, and the spine, tail and
 * neck bent as the body curve bends them. A step reposes the character and rebuilds nothing under the pose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoseStepWorkTest {
    private val leg = PartId("back-leg-l")
    private val torso = PartId("torso")
    private val tail = PartId("tail")
    private val head = PartId("head")

    private fun legPose(step: Int) = Pose(listOf(BonePose(leg, "hip", -20f - step), BonePose(leg, "knee", 40f + step)))

    private fun bodyPose(step: Int) = Pose(
        listOf(
            BonePose(torso, "waist", 6f + step * 0.5f),
            BonePose(torso, "chest", 6f + step * 0.5f),
            BonePose(tail, "root", 15f + step),
            BonePose(head, "neck", -10f - step),
        ),
    )

    @Test
    fun aPawDragStepReposesAndRebuildsNothingUnderThePose() = runTest { assertOnlyThePoseReruns(::legPose) }

    @Test
    fun aBodyCurveStepReposesAndRebuildsNothingUnderThePose() = runTest { assertOnlyThePoseReruns(::bodyPose) }

    private fun TestScope.assertOnlyThePoseReruns(pose: (Int) -> Pose) {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val posing = SessionPose(session)
        runCurrent()
        assertTrue(session.graph.settled.value)
        val assembled = session.assembly.output.value
        val rig = posing.rig.rig.output.value
        var posed = posing.posed.output.value
        for (step in 0 until STEPS) {
            session.send(SetPose(pose(step)))
            runCurrent()
            assertTrue(session.graph.settled.value, "the graph settles after step $step")
            assertSame(assembled, session.assembly.output.value, "step $step builds no part")
            assertSame(rig, posing.rig.rig.output.value, "step $step keeps the rig")
            assertNotSame(posed, posing.posed.output.value, "step $step reposes the character")
            posed = posing.posed.output.value
        }
    }
}
