package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBoneAngle
import dev.chieppa.squash.document.UpdateFromLibrary
import dev.chieppa.squash.document.readDocument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

private val WING_L = PartId("wing-l")
private const val OLDER = 1

/** Stage 5's second exit criterion: the winged fennec's torso updated from library keeps its hand-made wing socket holding the wing, rigged and posed. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryUpdateRigTest {
    @Test
    fun theWingedFennecsTorsoUpdatedFromLibraryStillHoldsTheWingRiggedAndPosed() = runTest {
        val saved = readDocument(Path.of(System.getProperty("squash.examples"), "fennec-winged.json").readText())
        val torso = saved.character.parts.single { it.socket == null }
        val older = UpdateFromLibrary(torso.id, OLDER).apply(saved).document
        val session = CharacterSession(older, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val rig = SessionRig(session).rig
        val posed = SessionPose(session).posed
        session.send(SetBoneAngle(WING_L, "elbow", 40f, bothSides = true))
        session.send(SetBoneAngle(torso.id, "chest", 10f))
        runCurrent()
        val pose = session.document.value.character.pose

        val newest = assertNotNull(shippedLibrary.latest(torso.template.id))
        assertTrue(newest.version > OLDER)
        session.send(UpdateFromLibrary(torso.id, newest.version))
        runCurrent()
        assertTrue(session.graph.settled.value)

        assertEquals(newest.ref, session.document.value.character.part(torso.id)?.template)
        assertEquals(pose, session.document.value.character.pose, "the pose carries over by name")
        val wing = assertNotNull(checkNotNull(session.assembly.output.value).part(WING_L))
        assertEquals(HandSockets.name(1), wing.socketSpec?.name, "the hand-made socket still holds the wing")
        assertNotNull(wing.anchor)
        val rigged = checkNotNull(rig.output.value)
        assertEquals(torso.id, rigged.bone(BoneName(WING_L, "shoulder"))?.parent?.part, "the wing hangs from a torso bone")
        assertNotNull(rigged.bone(BoneName(torso.id, "chest")))
        val shown = checkNotNull(posed.output.value)
        assertNotSame(wing.surface.mesh, shown.meshes.getValue(WING_L), "the pose bends the wing")
        assertNotSame(checkNotNull(session.assembly.output.value).part(torso.id)?.surface?.mesh, shown.meshes.getValue(torso.id), "and the torso")

        session.history.undo()
        runCurrent()
        assertEquals(OLDER, session.document.value.character.part(torso.id)?.template?.version)
    }
}
