package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SetPoseKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A pose key that only fills the crease inside the fennec's bent back knee: the leg's widths stay as they are, the key's pose moves only
 * the skin round the crease, and the rest moves nothing. One session, so integration.
 */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class KeyedBendTest {
    private val leg = PartId("back-leg-l")
    private val key = PoseKey("knee", listOf(BonePose(leg, "knee", 70f)), listOf(PartChange(leg, bend = 1.5f)))

    private fun TestScope.session(): Pair<CharacterSession, SessionPose> {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val pose = SessionPose(session)
        session.send(SetDisplayPose(Pose()))
        runCurrent()
        return session to pose
    }

    private fun CharacterSession.shown(pose: SessionPose, scope: TestScope): FloatArray {
        scope.runCurrent()
        check(graph.settled.value)
        return checkNotNull(pose.surface.posed.output.value?.posed).surface.positions.copyOf()
    }

    private fun moves(a: FloatArray, b: FloatArray): List<Float> {
        assertEquals(a.size, b.size, "the posed surface keeps its vertices")
        return (0 until a.size / 3).map { i ->
            val dx = a[i * 3] - b[i * 3]
            val dy = a[i * 3 + 1] - b[i * 3 + 1]
            val dz = a[i * 3 + 2] - b[i * 3 + 2]
            sqrt(dx * dx + dy * dy + dz * dz)
        }
    }

    @Test
    fun aBendFillsTheCreaseInsideTheBentJointAndNothingElse() = runTest {
        val (session, pose) = session()
        val rest = session.shown(pose, this)
        session.send(SetPose(Pose(key.pose)))
        val plain = session.shown(pose, this)

        session.send(SetPoseKeys(listOf(key)))
        val keyed = session.shown(pose, this)
        val moved = moves(plain, keyed)
        val shape = checkNotNull(pose.surface.keys.output.value).shapes.single()
        assertTrue(shape.moved.isNotEmpty(), "the bend makes a shape")
        assertTrue(moved.max() > 0.002f, "the key's pose fills the crease: ${moved.max()}")
        assertTrue(moved.count { it > 1e-5f } < moved.size / 20, "only the skin round the crease moves: ${moved.count { it > 1e-5f }} of ${moved.size}")
        session.send(SetPose(Pose()))
        assertEquals(0f, moves(rest, session.shown(pose, this)).max(), "the rest is as it was")
    }
}
