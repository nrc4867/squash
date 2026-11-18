package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.isFieldRun
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/**
 * The posed chain while a drag is open on a posed character (see [StackFixture]): the dress posed without the search for skin pushed
 * through it and the lines outlines only; and the release equal to a fresh build.
 */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class DragPosedTest {
    private val leg = StackFixture.leg
    private val bent = Pose(listOf(BonePose(leg.id, "knee", 40f)))

    @Test
    fun aDragPosesTheCoarseSurfaceWithItsDressAsAtRestAndOutlinesOnly() = runTest {
        val (session, pose) = posedSession()
        val gesture = session.history.begin()
        gesture.update(SetParameter(leg.id, "size", EDITED_SIZE))
        runCurrent()
        check(session.graph.settled.value)

        val coarse = assertNotNull(pose.surface.skin.output.value)
        assertTrue(coarse.coarse)
        val dress = assertNotNull(coarse.dress)

        val posed = assertNotNull(pose.surface.posed.output.value)
        assertTrue(posed.coarse)
        val worn = assertNotNull(posed.posed?.dress)
        assertSame(dress.shown, worn.shown, "the skin shown is the rest's, nothing searched for that the pose pushed through")

        val lines = assertNotNull(session.posedLines.output.value)
        val rest = assertNotNull(session.bodyLines.output.value)
        val moved = lines.parts.getValue(leg.id)
        assertTrue(moved.crease.isEmpty() && moved.markWeights.isEmpty(), "a moved part is outlined only")
        assertFalse(moved.grids.any { grid -> isFieldRun(moved.part, grid.patch) }, "the one surface outlines its own runs")
        assertSame(rest.parts.getValue(StackFixture.head.id), lines.parts.getValue(StackFixture.head.id), "a part the pose leaves keeps its lines")

        gesture.commit()
        runCurrent()
        check(session.graph.settled.value)
        val released = assertNotNull(pose.surface.skin.output.value)
        assertFalse(released.coarse)
        assertFalse(assertNotNull(pose.surface.posed.output.value).coarse)
        assertEqualsFresh(session, pose)
    }

    /** A settled session of the fixture in the [bent] pose, its skin shown. */
    private fun TestScope.posedSession(): Pair<CharacterSession, SessionPose> {
        val session = stackSession()
        val pose = SessionPose(session)
        session.send(SetPose(bent))
        runCurrent()
        check(session.graph.settled.value)
        return session to pose
    }

    /** The released skin, posed surface and lines of [session] equal those of a session opened afresh on its document. */
    private fun TestScope.assertEqualsFresh(session: CharacterSession, pose: SessionPose) {
        val fresh = CharacterSession(session.document.value, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val freshPose = SessionPose(fresh)
        runCurrent()
        check(fresh.graph.settled.value)
        val skin = assertNotNull(pose.surface.skin.output.value?.skin)
        val freshSkin = assertNotNull(freshPose.surface.skin.output.value?.skin)
        assertContentEquals(freshSkin.bones, skin.bones)
        assertContentEquals(freshSkin.weights, skin.weights)
        val posed = assertNotNull(pose.surface.posed.output.value?.posed)
        val freshPosed = assertNotNull(freshPose.surface.posed.output.value?.posed)
        assertContentEquals(freshPosed.surface.render.vertices, posed.surface.render.vertices)
        assertContentEquals(freshPosed.surface.render.indices, posed.surface.render.indices)
        assertContentEquals(freshPosed.dress?.shown, posed.dress?.shown)
        assertSameLines(assertNotNull(fresh.posedLines.output.value), assertNotNull(session.posedLines.output.value))
    }

    private fun assertSameLines(expected: BodyLines, actual: BodyLines) {
        assertEquals(expected.parts.keys, actual.parts.keys)
        for ((id, lines) in expected.parts) {
            val other = actual.parts.getValue(id)
            assertEquals(lines.grids.map { it.patch.name }, other.grids.map { it.patch.name }, "$id's grids")
            assertEquals(lines.crease.map { it.points.map { p -> p.position } }, other.crease.map { it.points.map { p -> p.position } }, "$id's crease")
            assertEquals(lines.crease.map { it.weights }, other.crease.map { it.weights }, "$id's crease weights")
            assertEquals(lines.markWeights, other.markWeights, "$id's mark weights")
        }
    }
}
