package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A bent knee moves a sleeve vertex at least this far, in body units. */
private const val MOVED = 0.02f

/** How far the gap between a sleeve vertex and the leg vertex under it may change between rest and a bent knee, in body units and as a share of the gap. */
private const val HELD = 1e-4f
private const val KEPT = 0.25f

/** A garment's branch takes the weights of the body under it, so it follows the pose with no bones of its own. */
@OptIn(ExperimentalCoroutinesApi::class)
class GarmentRigTest {
    private val leg = PartId("front-leg-l")
    private val twin = PartId("front-leg-r")
    private val sleeve = GarmentId("sleeve")
    private val outfit = OutfitId("winter")

    private fun TestScope.dressedSession(): Pair<CharacterSession, SessionPose> {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val pose = SessionPose(session)
        val slot = GarmentSlot(GarmentSlotId("legs"), "Legs")
        val garment = Garment(sleeve, "Sleeve", slot.id, branches = listOf(GarmentBranch(leg, 0f, 1f)))
        session.send(SetWardrobe(Wardrobe(listOf(garment), listOf(slot), listOf(Outfit(outfit, "Winter", mapOf(slot.id to sleeve))), outfit)))
        runCurrent()
        check(session.graph.settled.value)
        return session to pose
    }

    @Test
    fun aWornBranchIsPlacedAndRiggedWithNoBonesOfItsOwn() = runTest {
        val (session, pose) = dressedSession()
        val rig = assertNotNull(pose.rig.rig.output.value)
        val branch = branchPartId(sleeve, leg)
        assertNotNull(session.assembly.output.value?.part(branch))
        assertNotNull(rig.part(branchPartId(sleeve, twin)))
        assertTrue(rig.bones.none { it.name.part == branch }, "no bone is the branch's")
        assertTrue(assertNotNull(rig.part(branch)).weights.of(0).isNotEmpty(), "the sleeve follows bones")
    }

    @Test
    fun aBentKneeCarriesTheSleeveWithTheLeg() = runTest {
        val (session, pose) = dressedSession()
        val rig = assertNotNull(pose.rig.rig.output.value)
        val branch = branchPartId(sleeve, leg)
        val sleevePart = assertNotNull(rig.part(branch))
        val legPart = assertNotNull(rig.part(leg))
        session.send(SetPose(Pose(listOf(BonePose(leg, "knee", 60f)))))
        runCurrent()
        val posed = assertNotNull(pose.posed.output.value).parts
        val sleeveNow = posed.getValue(branch)
        val legNow = posed.getValue(leg)
        val placed = assertNotNull(session.assembly.output.value?.part(branch))
        val covering = assertNotNull(placed.covering)
        val legPatch = assertNotNull(session.assembly.output.value?.part(leg)).surface.patches
        val start = legPatch.take(covering.bodyPatch).sumOf { it.nu * it.nv }
        val around = legPatch[covering.bodyPatch].nv
        var moved = 0
        for (vertex in 0 until sleevePart.vertexCount) {
            val ring = vertex / around
            val under = start + covering.bodyRings[ring] * around + vertex % around
            val gapRest = at(sleevePart.rest, vertex) - at(legPart.rest, under)
            val gapPosed = at(sleeveNow, vertex) - at(legNow, under)
            if ((at(sleeveNow, vertex) - at(sleevePart.rest, vertex)).length > MOVED) moved++
            assertEquals(sleevePart.weights.of(vertex), legPart.weights.of(under), "vertex $vertex weighs as the leg under it")
            assertEquals(gapRest.length, gapPosed.length, HELD + gapRest.length * KEPT, "vertex $vertex keeps its ease")
        }
        assertTrue(moved > 0, "the sleeve below the knee moves")
    }

    @Test
    fun theBareBodyWearsNoBranch() = runTest {
        val (session, pose) = dressedSession()
        session.send(ShowOutfit(null))
        runCurrent()
        assertNull(session.assembly.output.value?.part(branchPartId(sleeve, leg)))
        assertNull(pose.rig.rig.output.value?.part(branchPartId(sleeve, leg)))
        session.send(SetWardrobe(Wardrobe()))
        runCurrent()
        assertTrue(session.graph.settled.value)
    }

    private fun at(values: FloatArray, vertex: Int) = Vec3(values[vertex * 3], values[vertex * 3 + 1], values[vertex * 3 + 2])
}
