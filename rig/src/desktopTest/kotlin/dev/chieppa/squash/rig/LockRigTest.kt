package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.geom.Vec3
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val TUFT_HEAD = PartId("head")
private val TUFT = LockGroupId("tuft")

/** A tuft of locks on the fennec's head: each lock a chain of its own bones hanging from the head's, ready for a solver, its mesh skinned to it. */
@Tag("integration")
class LockRigTest {
    private val own = LockPhysics(stiffness = 0.2f, angleLimit = 80f)

    private val rig: Rig by lazy {
        val tuft = LockGroup(
            TUFT, LockGroupKind.CROWN, TUFT_HEAD, count = 5, length = 0.12f, width = 0.03f, thickness = 0.015f, curl = 0.8f,
            overrides = listOf(LockOverride(2, bones = 5, physics = own)),
        )
        settledRig(SetLockGroup(TUFT, tuft).apply(savedFennec()).document, "fennec with a tuft")
    }

    private fun point(positions: FloatArray, vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

    @Test
    fun eachLockIsAChainRootedOnTheHeadsBone() {
        assertEquals(5, rig.chains.size)
        for (chain in rig.chains) {
            assertTrue(chain.bones.size in 3..6)
            assertEquals(TUFT_HEAD, rig.bones[chain.carrier].name.part, "a lock hangs from the head")
            assertEquals(rig.bones[chain.carrier].name, rig.bones[chain.bones.first()].parent)
            chain.bones.zipWithNext().forEach { (a, b) -> assertEquals(rig.bones[a].name, rig.bones[b].parent) }
        }
        val set = rig.chains.single { it.index == 2 }
        assertEquals(5, set.bones.size)
        assertEquals(own, set.physics)
        assertEquals(listOf(1, 3), set.neighbours.map { rig.chains[it].index }.sorted())
    }

    @Test
    fun theLockMeshFollowsItsChainAndTheHeadMovesIt() {
        val part = assertNotNull(rig.part(lockGroupPartId(TUFT)))
        val lockBones = rig.chains.flatMap { it.bones }.toSet()
        assertTrue((0 until part.vertexCount).all { v -> part.weights.of(v).any { (bone, _) -> bone in lockBones } })
        val neck = rig.bones.first { it.name.part == TUFT_HEAD }
        val posed = rig.posed(Pose(listOf(BonePose(TUFT_HEAD, neck.name.joint, degrees = 30f)))).parts.getValue(lockGroupPartId(TUFT))
        val moved = (0 until part.vertexCount).maxOf { (point(posed, it) - point(part.rest, it)).length }
        assertTrue(moved > 0.01f, "turning the head carries its locks: $moved")
    }
}
