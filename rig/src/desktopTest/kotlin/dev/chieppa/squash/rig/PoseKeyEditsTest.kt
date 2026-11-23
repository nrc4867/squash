package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.OutlineScale
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetPoseKeys
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.WidthScale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keys on the fennec's front and back knees: an edit of an ear builds neither again, and an edit of the front knee's key builds it
 * alone, the back knee's kept as it was. One session, so integration.
 */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class PoseKeyEditsTest {
    private fun fuller(by: Float) = OutlineScale(listOf(WidthScale(0f, by)), listOf(WidthScale(0f, by)))

    private fun key(leg: String, by: Float = 1.2f) = PartId(leg).let { part ->
        PoseKey(leg, listOf(BonePose(part, "knee", 70f)), listOf(PartChange(part, mapOf(View.SIDE to fuller(by), View.TOP to fuller(by)))))
    }

    @Test
    fun anEditBuildsAgainOnlyTheKeysItReaches() = runTest {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val pose = SessionPose(session)
        session.send(SetDisplayPose(Pose()))
        session.send(SetPoseKeys(listOf(key("front-leg-l"), key("back-leg-l"))))
        runCurrent()
        check(session.graph.settled.value)
        assertEquals(2, pose.surface.keysBuilt, "opening builds both keys")
        val back = checkNotNull(pose.surface.keys.output.value).shapes[1]
        assertTrue(!back.isEmpty)

        session.send(SetParameter(PartId("ear-l"), "size", 1.15f, thisSide = true))
        runCurrent()
        check(session.graph.settled.value)
        assertEquals(0, pose.surface.keysBuilt, "an ear's edit reaches no knee")

        session.send(SetPoseKeys(listOf(key("front-leg-l", 1.3f), key("back-leg-l"))))
        runCurrent()
        check(session.graph.settled.value)
        assertEquals(1, pose.surface.keysBuilt, "an edit of the front knee's key builds it alone")
        val kept = checkNotNull(pose.surface.keys.output.value).shapes[1]
        assertEquals(back.moved.size, kept.moved.size, "the back knee's key is kept whole")
        assertTrue(back.offsets.contentEquals(kept.offsets))
    }
}
