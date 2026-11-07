package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRigTest {
    @Test
    fun theRigFollowsTheAssemblyAndSettles() = runTest {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionRig(session).rig
        runCurrent()
        assertTrue(session.graph.settled.value)
        val knee = BoneName(PartId("back-leg-l"), "knee")
        val before = checkNotNull(node.output.value?.bone(knee)).pivot
        session.send(SetParameter(PartId("back-leg-l"), "move.joint.knee", 0.4f))
        assertFalse(session.graph.settled.value)
        runCurrent()
        assertTrue(session.graph.settled.value)
        val after = checkNotNull(node.output.value?.bone(knee)).pivot
        assertNotEquals(before, after, "moving the knee along the leg moves its bone")
        assertEquals(37, node.output.value?.bones?.size)
    }
}
