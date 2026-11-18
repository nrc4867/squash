package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** The one surface's stack through a drag (see [StackFixture]): coarse while it is open, the unseen skin waiting for its end, recent builds kept. */
@OptIn(ExperimentalCoroutinesApi::class)
class DragStackTest {
    private val leg = StackFixture.leg

    @Tag("integration")
    @Test
    fun aDragMeshesEverythingCoarseAndLeavesTheUnseenSkinUntilItsEnd() = runTest {
        val session = stackSession()
        val pose = SessionPose(session)
        session.sessionSurface.skinShown.value = false
        runCurrent()
        val full = assertNotNull(session.characterSurface.output.value?.surface).mesh.cell
        val gesture = session.history.begin()
        gesture.update(SetParameter(leg.id, "size", EDITED_SIZE))
        runCurrent()
        check(session.graph.settled.value)
        assertTrue(assertNotNull(session.characterSurface.output.value?.surface).mesh.cell > full)
        val dress = assertNotNull(session.sessionSurface.dress.output.value?.dress)
        assertTrue(dress.garments.isNotEmpty() && dress.garments.all { it.surface.mesh.cell > full }, "the garments mesh at the coarse cell too")
        assertNull(pose.surface.skin.output.value?.skin, "no pane draws the weights, so they wait")
        assertNull(pose.surface.posed.output.value?.posed)
        gesture.commit()
        runCurrent()
        check(session.graph.settled.value)
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        assertEquals(full, surface.mesh.cell)
        assertSame(surface, assertNotNull(pose.surface.skin.output.value).surface, "the weights follow the full surface")
        assertNotNull(pose.surface.skin.output.value?.skin)
    }

    @Test
    fun aDragTheSkinIsShownInMakesItAsItGoes() = runTest {
        val session = stackSession()
        val pose = SessionPose(session)
        runCurrent()
        val gesture = session.history.begin()
        gesture.update(SetParameter(leg.id, "size", EDITED_SIZE))
        runCurrent()
        check(session.graph.settled.value)
        val coarse = assertNotNull(session.characterSurface.output.value?.surface)
        assertSame(coarse, assertNotNull(pose.surface.skin.output.value).surface)
        gesture.cancel()
        runCurrent()
    }

    @Test
    fun aPoseDragKeepsTheFullSurface() = runTest {
        val session = stackSession()
        SessionPose(session)
        runCurrent()
        val rest = assertNotNull(session.characterSurface.output.value?.surface)
        val gesture = session.history.begin()
        gesture.update(SetPose(Pose(listOf(BonePose(leg.id, "knee", 30f)))))
        runCurrent()
        check(session.graph.settled.value)
        assertSame(rest, session.characterSurface.output.value?.surface)
        gesture.commit()
        runCurrent()
        assertSame(rest, session.characterSurface.output.value?.surface)
    }

    @Tag("integration")
    @Test
    fun undoAndRedoTakeTheBuildsTheyMadeBefore() = runTest {
        val session = stackSession()
        val pose = SessionPose(session)
        runCurrent()
        session.sessionSurface.skinShown.value = false
        runCurrent()
        val before = Built.of(session, pose)
        val gesture = session.history.begin()
        gesture.update(SetParameter(leg.id, "size", EDITED_SIZE * 0.99f))
        runCurrent()
        gesture.update(SetParameter(leg.id, "size", EDITED_SIZE))
        runCurrent()
        gesture.commit()
        runCurrent()
        val edited = Built.of(session, pose)
        assertNotSame(before.surface, edited.surface)
        session.history.undo()
        runCurrent()
        check(session.graph.settled.value)
        before.assertSame(Built.of(session, pose))
        session.history.redo()
        runCurrent()
        edited.assertSame(Built.of(session, pose))
    }

    /** What the stack shows, as objects. */
    private class Built(val surface: Any?, val raw: Any?, val paint: Any?, val dress: Any?, val skin: Any?, val posed: Any?) {
        fun assertSame(other: Built) {
            assertSame(surface, other.surface)
            assertSame(raw, other.raw, "raw paint")
            assertSame(dress, other.dress, "dress")
            assertSame(paint, other.paint)
            assertSame(dress, other.dress)
            assertSame(skin, other.skin)
            assertSame(posed, other.posed)
        }

        companion object {
            fun of(session: CharacterSession, pose: SessionPose) = Built(
                assertNotNull(session.characterSurface.output.value?.surface),
                session.surfacePaint.output.value?.paint,
                session.sessionSurface.shownPaint.output.value?.paint,
                session.sessionSurface.dress.output.value?.dress,
                pose.surface.skin.output.value?.skin,
                pose.surface.posed.output.value?.posed,
            )
        }
    }
}
