package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.OutlineScale
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SetPoseKeys
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.WidthScale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A pose key on the fennec's back leg, its widths a fifth fuller with the knee bent: the rest it leaves as it was, the key's pose moves
 * the leg by the key's shape with every vertex in its place, and a pose half way there moves it part of the way. One session, through
 * undo, so integration.
 */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class PoseKeySurfaceTest {
    private val leg = PartId("back-leg-l")
    private val fuller = OutlineScale(listOf(WidthScale(0f, 1.2f)), listOf(WidthScale(0f, 1.2f)))
    private val key = PoseKey("knee", listOf(BonePose(leg, "knee", 70f)), listOf(PartChange(leg, mapOf(View.SIDE to fuller, View.TOP to fuller))))

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

    private fun largestMove(a: FloatArray, b: FloatArray): Float {
        assertEquals(a.size, b.size, "the posed surface keeps its vertices")
        var most = 0f
        for (i in 0 until a.size / 3) {
            val dx = a[i * 3] - b[i * 3]
            val dy = a[i * 3 + 1] - b[i * 3 + 1]
            val dz = a[i * 3 + 2] - b[i * 3 + 2]
            most = maxOf(most, sqrt(dx * dx + dy * dy + dz * dz))
        }
        return most
    }

    @Test
    fun aKeyTakesItsShapeAtItsPoseAndNothingAtTheRest() = runTest {
        val (session, pose) = session()
        val restPlain = session.shown(pose, this)
        session.send(SetPose(Pose(key.pose)))
        val keyPlain = session.shown(pose, this)
        val keyIndices = checkNotNull(pose.surface.posed.output.value?.posed).surface.render.indices.copyOf()
        session.send(SetPose(Pose(listOf(BonePose(leg, "knee", 35f)))))
        val halfPlain = session.shown(pose, this)

        session.send(SetPoseKeys(listOf(key)))
        session.send(SetPose(Pose()))
        assertEquals(0f, largestMove(restPlain, session.shown(pose, this)), "the rest is as it was")
        val shapes = checkNotNull(pose.surface.keys.output.value)
        assertTrue(shapes.shapes.single().moved.isNotEmpty(), "the key moves the leg at rest")
        session.send(SetPose(Pose(key.pose)))
        val keyed = session.shown(pose, this)
        val full = largestMove(keyPlain, keyed)
        assertTrue(full > 0.002f, "the key's pose takes its shape: $full")
        assertContentEquals(keyIndices, checkNotNull(pose.surface.posed.output.value?.posed).surface.render.indices, "every vertex in its place")
        session.send(SetPose(Pose(listOf(BonePose(leg, "knee", 35f)))))
        val half = largestMove(halfPlain, session.shown(pose, this))
        assertTrue(half > 0.1f * full && half < 0.9f * full, "half way there takes part of it: $half of $full")
    }
}
