package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val FRONT_PAW_L = PartId("front-paw-l")
private val FRONT_LEG_L = PartId("front-leg-l")

/** How near a held paw must stay, and a raised paw must land, in body units: a hundredth of the fennec's leg. */
private const val NEAR = 0.006f

class PoseSolverTest {
    private val solver = PoseSolver(fennec.rig)
    private val chains = solver.chains
    private val allPlanted = Pose(planted = chains.limbs.map { it.id }.toSet())

    private fun ends(pose: Pose) = chains.limbs.associate { it.id to solver.endOf(pose, it) }

    @Test
    fun theFennecHasFourLimbsATailAndAHead() {
        assertEquals(setOf("front-paw-l", "front-paw-r", "back-paw-l", "back-paw-r"), chains.limbs.map { it.id.value }.toSet())
        assertEquals(listOf("tail"), chains.tails.map { it.part.value })
        val look = assertNotNull(chains.look)
        assertEquals(BoneName(PartId("head"), "neck"), fennec.rig.bones[look.head].name)
        assertEquals(listOf(BoneName(PartId("torso"), "chest")), look.neck.map { fennec.rig.bones[it].name })
    }

    @Test
    fun aRaisedPawLandsAndThePlantedPawsStay() {
        val start = allPlanted.withPlanted(FRONT_PAW_L, false)
        val limb = assertNotNull(chains.limb(FRONT_PAW_L))
        val target = solver.endOf(start, limb) + Vec3(0.1f, 0.15f, 0f)
        val solved = solver.reach(start, limb, target)
        val landed = (solver.endOf(solved.pose, limb) - target).length
        println("raised paw lands ${"%.5f".format(landed)} from its target")
        assertTrue(landed < NEAR, "landed $landed away")
        val before = ends(start)
        val after = ends(solved.pose)
        chains.limbs.filter { it.id != FRONT_PAW_L }.forEach { assertEquals(0f, (after.getValue(it.id) - before.getValue(it.id)).length, 1e-6f) }
        assertTrue(solved.pose.of(FRONT_LEG_L, "knee")!!.degrees > 0f, "the knee bends its own way")
    }

    @Test
    fun theBodyPushedSidewaysPastReachSlidesThePawsAndFirmStopsIt() {
        val by = Vec3(0f, 0f, 0.25f)
        val loose = solver.moveBody(allPlanted, by)
        println("loose: moved ${loose.report.moved}, slid ${loose.report.slid.mapValues { "%.4f".format(it.value) }}")
        assertEquals(by, loose.report.moved)
        assertTrue(loose.report.slid.isNotEmpty() && loose.report.slid.values.all { it > NEAR }, "the paws slide")
        val firm = solver.moveBody(allPlanted, by, SolveOptions(firm = true))
        println("firm: moved ${"%.4f".format(firm.report.moved.z)} of ${by.z}")
        assertTrue(firm.report.moved.z < by.z, "a firm body stops short")
        assertTrue(firm.report.slid.isEmpty())
        val before = ends(allPlanted)
        ends(firm.pose).forEach { (id, at) -> assertTrue((at - before.getValue(id)).length < NEAR, "$id stays") }
    }

    @Test
    fun theBodyDroppingKeepsItsPlantedPaws() {
        val solved = solver.moveBody(allPlanted, Vec3(0f, -0.08f, 0f))
        val before = ends(allPlanted)
        assertTrue(solved.report.slid.isEmpty(), "slid ${solved.report.slid}")
        ends(solved.pose).forEach { (id, at) -> assertTrue((at - before.getValue(id)).length < NEAR, "$id stays") }
        chains.limbs.forEach { assertTrue(solved.pose.of(it.leg, "knee")!!.degrees > 0f) }
    }

    @Test
    fun stretchLengthensTheLimbToABoundedFactor() {
        val limb = assertNotNull(chains.limb(FRONT_PAW_L))
        val target = solver.endOf(Pose(), limb) + Vec3(0.2f, -0.12f, 0f)
        val plain = solver.reach(Pose(), limb, target)
        val stretched = solver.reach(Pose(), limb, target, SolveOptions(stretch = true))
        val factor = stretched.pose.of(FRONT_LEG_L, "ankle")?.stretch ?: 1f
        println("stretch ${"%.3f".format(factor)}, missed ${"%.4f".format(plain.report.missed)} plain, ${"%.4f".format(stretched.report.missed)} stretched")
        assertEquals(null, plain.pose.of(FRONT_LEG_L, "ankle")?.stretch?.takeIf { it != 1f })
        assertTrue(factor > 1f && factor <= MAX_DRAG_STRETCH, "factor $factor")
        assertTrue(stretched.report.missed < plain.report.missed)
        assertEquals(factor, stretched.report.stretched[BoneName(FRONT_LEG_L, "ankle")])
        val far = solver.reach(Pose(), limb, solver.endOf(Pose(), limb) + Vec3(1.5f, 0f, 0f), SolveOptions(stretch = true))
        assertEquals(MAX_DRAG_STRETCH, far.pose.of(FRONT_LEG_L, "ankle")?.stretch)
    }

    @Test
    fun lookingTurnsTheNeckAShareAndTheHeadTheRest() {
        val look = assertNotNull(chains.look)
        val head = fennec.rig.bones[look.head]
        val pivot = solver.endOf(Pose(), chains.limbs.first()).let { PoseWorkProbe.joint(Pose(), look.head) }
        val target = pivot + head.direction * 0.6f + Vec3(0f, 0.05f, 0.3f)
        val solved = solver.look(allPlanted, target)
        val neck = solver.turnedBetween(allPlanted, solved.pose, look.neck.single())
        val total = solver.turnedBetween(allPlanted, solved.pose, look.head)
        println("look: neck ${"%.1f".format(neck)}°, head ${"%.1f".format(total)}° in all, missed ${"%.2f".format(solved.report.missed)}°")
        assertTrue(solved.report.missed < 1f, "faces the target")
        assertTrue(neck > 0f && neck < total * 0.5f, "the neck takes a share")
        assertEquals(target.x, solved.pose.lookAt!!.x)
        val before = ends(allPlanted)
        ends(solved.pose).forEach { (id, at) -> assertTrue((at - before.getValue(id)).length < NEAR, "$id stays") }
    }

    @Test
    fun limitsHoldUnlessTheBoneIsFree() {
        val limb = assertNotNull(chains.limb(FRONT_PAW_L))
        val target = solver.endOf(Pose(), limb) + Vec3(0.4f, 0.5f, 0f)
        val solved = solver.reach(Pose(), limb, target)
        for (bone in fennec.rig.bones) {
            val set = solved.pose.of(bone.name.part, bone.name.joint) ?: continue
            val limits = bone.limits ?: continue
            assertTrue(set.degrees in limits.min..limits.max, "${bone.name} tips ${set.degrees}")
            assertTrue(kotlin.math.abs(set.lean) <= limits.turnReach() && kotlin.math.abs(set.turn) <= limits.turnReach(), "${bone.name} turns ${set.turn}, leans ${set.lean}")
        }
        val freed = Pose(listOf(BonePose(FRONT_LEG_L, "hip", mode = JointMode.FREE), BonePose(FRONT_LEG_L, "knee", mode = JointMode.FREE)))
        val free = solver.reach(freed, limb, target)
        println("limited misses ${"%.4f".format(solved.report.missed)}, free ${"%.4f".format(free.report.missed)}")
        assertTrue(free.report.missed < solved.report.missed)
    }

    @Test
    fun aLockedBoneStaysOutOfTheSolve() {
        val limb = assertNotNull(chains.limb(FRONT_PAW_L))
        val locked = Pose(listOf(BonePose(FRONT_LEG_L, "knee", 20f, mode = JointMode.LOCKED)))
        val solved = solver.reach(locked, limb, solver.endOf(locked, limb) + Vec3(0.05f, 0.1f, 0f))
        assertEquals(locked.of(FRONT_LEG_L, "knee"), solved.pose.of(FRONT_LEG_L, "knee"))
    }

    @Test
    fun theTwinPosesAsTheMirrorImage() {
        val right = PartId("front-leg-r")
        val pose = Pose(listOf(BonePose(FRONT_LEG_L, "hip", 20f, turn = 10f, lean = 15f), BonePose(right, "hip", 20f, turn = 10f, lean = 15f), BonePose(FRONT_LEG_L, "knee", 30f), BonePose(right, "knee", 30f)))
        val left = assertNotNull(chains.limb(FRONT_PAW_L))
        val twin = assertNotNull(chains.limb(PartId("front-paw-r")))
        val a = solver.endOf(pose, left)
        val b = solver.endOf(pose, twin)
        assertEquals(a.x, b.x, 1e-5f)
        assertEquals(a.y, b.y, 1e-5f)
        assertEquals(a.z, -b.z, 1e-5f)
    }

    @Test
    fun aTailCurlsInEqualShares() {
        val tail = chains.tails.single()
        val tip = PoseWorkProbe.joint(Pose(), tail.bones.last())
        val solved = solver.curl(Pose(), tail, tip + Vec3(0.1f, 0.25f, 0f))
        val shares = tail.bones.map { solved.pose.of(fennec.rig.bones[it].name.part, fennec.rig.bones[it].name.joint)?.degrees ?: 0f }
        assertTrue(shares.first() != 0f)
        shares.forEach { assertEquals(shares.first(), it, 1e-4f) }
    }
}

/** A joint of the fennec under a pose. */
private object PoseWorkProbe {
    fun joint(pose: Pose, bone: Int): Vec3 = boneTransforms(fennec.rig, pose)[bone](fennec.rig.bones[bone].pivot)
}
