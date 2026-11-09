package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.LooseEndId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetBoneAngle
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.document.looseEndPartId
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A turned root bone carries the loose end's tip at least this far, in body units. */
private const val SWUNG = 0.05f

/** A scarf's loose end hangs on a chain of its own bones from the scarf, posed by hand, outside the body at rest. */
@OptIn(ExperimentalCoroutinesApi::class)
class LooseEndRigTest {
    private val torso = PartId("torso")
    private val scarf = GarmentId("scarf")
    private val endId = LooseEndId("tail")
    private val end = looseEndPartId(scarf, endId)

    private fun TestScope.scarfed(): Pair<CharacterSession, SessionPose> {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val pose = SessionPose(session)
        val slot = GarmentSlot(GarmentSlotId("neck"), "Neck")
        val garment = Garment(
            scarf, "Scarf", slot.id,
            branches = listOf(GarmentBranch(torso, 0.93f, 1.02f)),
            looseEnds = listOf(LooseEnd(endId, torso, v = 0.5f)),
        )
        val outfit = OutfitId("winter")
        session.send(SetWardrobe(Wardrobe(listOf(garment), listOf(slot), listOf(Outfit(outfit, "Winter", mapOf(slot.id to scarf))), outfit)))
        runCurrent()
        check(session.graph.settled.value)
        return session to pose
    }

    @Test
    fun theEndHangsOnItsOwnChainFromTheTorso() = runTest {
        val (_, pose) = scarfed()
        val rig = assertNotNull(pose.rig.rig.output.value)
        val chain = rig.bones.filter { it.name.part == end }
        assertEquals((1..LooseEnd.DEFAULT_LOOSE_BONES).map(::looseJoint), chain.map { it.name.joint })
        assertEquals(torso, chain.first().parent?.part, "the chain hangs from a torso bone")
        assertTrue(assertNotNull(rig.part(end)).weights.of(0).isNotEmpty())
    }

    @Test
    fun turningTheRootSwingsTheEndAndUndoesItsPose() = runTest {
        val (session, pose) = scarfed()
        val rig = assertNotNull(pose.rig.rig.output.value)
        val strip = assertNotNull(rig.part(end))
        val tip = strip.vertexCount - 1
        session.send(SetBoneAngle(end, looseJoint(1), 60f))
        runCurrent()
        val swung = assertNotNull(pose.posed.output.value).parts.getValue(end)
        assertTrue((at(swung, tip) - at(strip.rest, tip)).length > SWUNG, "the tip swings with the root bone")
        session.send(SetPose(Pose()))
        runCurrent()
        assertTrue(session.graph.settled.value)
    }

    @Test
    fun atRestTheEndStaysOutsideTheBody() = runTest {
        val (session, _) = scarfed()
        val character = assertNotNull(session.assembly.output.value)
        val strip = assertNotNull(character.part(end))
        val body = character.parts.filter { it.id != null && it.covering == null && it.hanging == null }.flatMap { part ->
            part.surface.patches.filter { it.slices != null }.map { ShellInside(it, { p -> part.origin + part.surface.shown(p) }, part.surface::shown) }
        }
        val mesh = strip.surface.mesh
        val inside = (0 until mesh.vertexCount).count { v -> body.any { strip.origin + mesh.position(v) in it } }
        assertEquals(0, inside, "strip vertices inside the body")
    }

    private fun at(values: FloatArray, vertex: Int) = Vec3(values[vertex * 3], values[vertex * 3 + 1], values[vertex * 3 + 2])
}
