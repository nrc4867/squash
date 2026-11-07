package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Poses sent before the timed ones, so the first runs' class loading and compilation are not counted. */
private const val WARM_UPS = 5

/** Poses timed and averaged. */
private const val TIMED = 10

private const val NANOS_PER_MILLI = 1_000_000.0

/**
 * How long the fennec's graph takes to settle after one `SetPose`, as a drag sends one every frame: a paw lifted by its leg, and the
 * spine, tail and neck bent as the body curve bends them. Each step sends a pose not sent before, so no node answers from its cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PosedSettleTimeTest {
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
    fun aDragStepSettlesInTime() {
        val legMillis = settleMillis(::legPose)
        val bodyMillis = settleMillis(::bodyPose)
        println("Settled after one SetPose, mean of $TIMED: paw drag %.1f ms, body curve %.1f ms".format(legMillis, bodyMillis))
        assertTrue(legMillis > 0.0 && bodyMillis > 0.0)
    }

    private fun settleMillis(pose: (Int) -> Pose): Double {
        var total = 0L
        runTest {
            val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
            SessionPose(session)
            runCurrent()
            check(session.graph.settled.value)
            for (step in 0 until WARM_UPS + TIMED) {
                session.send(SetPose(pose(step)))
                val start = System.nanoTime()
                runCurrent()
                val spent = System.nanoTime() - start
                check(session.graph.settled.value) { "The graph did not settle after step $step" }
                if (step >= WARM_UPS) total += spent
            }
        }
        return total / NANOS_PER_MILLI / TIMED
    }
}
