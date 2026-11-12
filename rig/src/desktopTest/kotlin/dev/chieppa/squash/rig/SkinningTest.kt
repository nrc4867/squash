package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkinningTest {
    private val rig get() = fennec.rig

    @Test
    fun theRestPoseLeavesEveryVertexWhereItRests() {
        val posed = rig.posed(Pose())
        for (part in rig.parts) {
            val moved = checkNotNull(posed.parts[part.id])
            for (i in moved.indices) assertEquals(part.rest[i], moved[i], 1e-5f, "${part.id}")
        }
    }

    @Test
    fun oneAngleOnBothSidesPosesThemAsMirrorImages() {
        val legs = listOf("back-leg-l", "back-leg-r").map(::PartId)
        val pose = Pose(legs.flatMap { listOf(BonePose(it, "hip", 25f), BonePose(it, "knee", 50f), BonePose(it, "ankle", -20f, stretch = 1.2f)) })
        val posed = rig.posed(pose)
        for ((left, right) in listOf("back-leg-l" to "back-leg-r", "back-paw-l" to "back-paw-r").map { (l, r) -> PartId(l) to PartId(r) }) {
            val l = checkNotNull(posed.parts[left])
            val r = checkNotNull(posed.parts[right])
            // A strip skins partly by the torso it shares with its twin, which a pose builds again, so only the parts' own patches count.
            for (v in 0 until ownVertices(checkNotNull(fennec.assembled.part(left)))) {
                assertEquals(l[v * 3], r[v * 3], 1e-4f)
                assertEquals(l[v * 3 + 1], r[v * 3 + 1], 1e-4f)
                assertEquals(l[v * 3 + 2], -r[v * 3 + 2], 1e-4f)
            }
        }
    }

    @Test
    fun aTurnIsHeldToItsJointsLimits() {
        val leg = PartId("back-leg-l")
        val past = rig.posed(Pose(listOf(BonePose(leg, "knee", 200f)))).parts.getValue(leg)
        val atLimit = rig.posed(Pose(listOf(BonePose(leg, "knee", 140f)))).parts.getValue(leg)
        assertContentEquals(atLimit, past)
    }

    @Test
    fun aStretchCarriesWhatSitsPastTheJoint() {
        val leg = PartId("back-leg-l")
        val transforms = boneTransforms(rig, Pose(listOf(BonePose(leg, "ankle", stretch = 1.5f))))
        val knee = checkNotNull(rig.bone(BoneName(leg, "knee")))
        val ankle = checkNotNull(rig.indexOf(BoneName(leg, "ankle")))
        val wrist = checkNotNull(rig.indexOf(BoneName(PartId("back-paw-l"), "wrist")))
        val reach = transforms[ankle](rig.bones[ankle].pivot) - knee.pivot
        assertEquals(1.5f * knee.length, reach.length, 1e-4f)
        assertTrue((transforms[wrist](rig.bones[wrist].pivot) - rig.bones[wrist].pivot).length > 0.4f * knee.length)
    }

    @Test
    fun aTwistSpreadsAlongItsBoneSoTheJointKeepsItsSection() {
        val leg = PartId("back-leg-l")
        val knee = checkNotNull(rig.indexOf(BoneName(leg, "knee")))
        val bone = rig.bones[knee]
        val pose = Pose(listOf(BonePose(leg, "knee", turn = 90f, mode = JointMode.FREE)))
        val frames = boneFrames(rig, pose)
        val part = checkNotNull(rig.part(leg))
        val posed = rig.posed(pose).parts.getValue(leg)
        val along = bone.direction.normalized()
        val pivot = frames.transforms[knee](bone.pivot)
        val posedAlong = (frames.turns[knee] * along).normalized()
        fun across(p: Vec3, from: Vec3, way: Vec3) = (p - from - way * ((p - from) dot way)).length
        var checked = 0
        for (v in 0 until part.vertexCount) {
            val rest = Vec3(part.rest[v * 3], part.rest[v * 3 + 1], part.rest[v * 3 + 2])
            if (part.weights.weightOf(v, knee) !in 0.2f..0.8f) continue
            val moved = Vec3(posed[v * 3], posed[v * 3 + 1], posed[v * 3 + 2])
            assertEquals(across(rest, bone.pivot, along), across(moved, pivot, posedAlong), 0.03f * bone.length, "vertex $v keeps its distance from the leg's line")
            checked++
        }
        assertTrue(checked > 0, "some vertices blend across the knee")
        val tip = bone.pivot + along * (bone.length * 1.01f)
        assertEquals(0f, (checkNotNull(frames.twists[knee]).backOf(tip)?.let { 1f } ?: 0f), "past its span a point takes the whole twist")
    }

    @Test
    fun aTwistRollsABentBoneWhereItPointsAndSpreadsAlongItWhereATurnSwingsTheBend() {
        val leg = PartId("back-leg-l")
        val knee = checkNotNull(rig.indexOf(BoneName(leg, "knee")))
        val along = rig.bones[knee].direction.normalized()
        fun pointing(set: BonePose) = (boneFrames(rig, Pose(listOf(set))).turns[knee] * along).normalized()
        val bent = pointing(BonePose(leg, "knee", 40f, mode = JointMode.FREE))
        val twisted = pointing(BonePose(leg, "knee", 40f, twist = 70f, mode = JointMode.FREE))
        val turned = pointing(BonePose(leg, "knee", 40f, turn = 70f, mode = JointMode.FREE))
        assertEquals(0f, (twisted - bent).length, 1e-4f, "a twist leaves the bent bone pointing where its bend put it")
        assertTrue((turned - bent).length > 0.1f, "a turn about the rest length swings the bend")
        val twist = checkNotNull(boneFrames(rig, Pose(listOf(BonePose(leg, "knee", 40f, twist = 70f, mode = JointMode.FREE)))).twists[knee])
        assertEquals(70f * DEGREES_TO_RADIANS, twist.radians, 1e-3f, "the whole twist spreads along the bone")
    }
}
