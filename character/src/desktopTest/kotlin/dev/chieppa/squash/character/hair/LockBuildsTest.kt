package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.shipped
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.ColliderShape
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.LockPoint
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The most vertices one lock's mesh holds. */
private const val MOST_LOCK_VERTICES = 400

/**
 * Lock groups on a lone round head: where their roots fall, how each lock is grown and built, the chain each carries for a solver, and
 * that a build keeps every lock and group an edit leaves alone.
 */
class LockBuildsTest {
    private val headId = PartId("head")

    private val head: AssembledCharacter by lazy {
        val part = PartInstance(headId, TemplateRef(TemplateId("head-round"), 2), role = Role.HEAD)
        val character = Character(CharacterId("toy"), "Toy", BodyPlanRef(BodyPlanId("blank"), 1), parts = listOf(part))
        val plan = shipped.plans.firstOrNull { it.id.value == "blank" }
        assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, plan, TemplateLibrary(shipped.parts, shipped.plans))
    }

    private val scalp: Scalp by lazy { Scalp.of(assertNotNull(head.part(headId)), null) }

    private fun group(id: String, kind: LockGroupKind, count: Int = 6) = LockGroup(LockGroupId(id), kind, headId, count = count, length = 0.1f, width = 0.04f, thickness = 0.015f)

    @Test
    fun eachKindRootsItsCountWhereItsSeedSays() {
        for (kind in LockGroupKind.entries) {
            val roots = rootsOf(group("g", kind, 9))
            assertEquals(9, roots.size, "$kind")
            assertEquals(roots, rootsOf(group("g", kind, 9)), "$kind roots the same by the same seed")
            assertNotEquals(roots, rootsOf(group("g", kind, 9).copy(seed = 2)), "$kind roots elsewhere by another seed")
        }
        val front = rootsOf(group("f", LockGroupKind.FRINGE))
        assertTrue(front.all { it.azimuth in -60f..60f }, "a fringe roots over the front: $front")
        val tied = rootsOf(group("t", LockGroupKind.BACK).copy(tie = 1f))
        assertTrue(tied.all { it.azimuth == tied.first().azimuth && it.elevation == tied.first().elevation }, "a tie gathers every root to one point")
    }

    @Test
    fun aLockRootsUnderTheScalpAndKeepsOutOfItPastItsRoot() {
        val locks = locksOf(group("b", LockGroupKind.BACK, 8), scalp)
        assertEquals(8, locks.size)
        for (lock in locks) {
            val built = buildLock(lock, scalp)
            val root = built.guide.first()
            assertTrue((root - scalp.middle).length < scalp.reachToward(root), "the root sinks under the scalp")
            val tip = built.guide.last()
            assertTrue((tip - scalp.middle).length >= scalp.reachToward(tip) + lock.clearance * 0.5f, "the tip lies off the scalp")
            assertTrue(built.patch.nu * built.patch.nv <= MOST_LOCK_VERTICES, "a lock's mesh stays small: ${built.patch.nu * built.patch.nv}")
            assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(built.patch) { 0 }, emptySet()))
        }
    }

    @Test
    fun aChainHoldsThreeToSixBonesAlongItsLockFromItsRoot() {
        val short = buildLock(locksOf(group("s", LockGroupKind.CROWN, 1), scalp).single(), scalp)
        assertEquals(3, short.chain.bones)
        assertEquals(short.guide.first(), short.chain.joints.first())
        val long = buildLock(locksOf(group("l", LockGroupKind.BACK, 1).copy(length = 1.2f), scalp).single(), scalp)
        assertEquals(6, long.chain.bones)
        val own = LockPhysics(stiffness = 0.1f)
        val set = locksOf(group("o", LockGroupKind.BACK, 2).copy(overrides = listOf(LockOverride(1, bones = 4, physics = own))), scalp)
        assertEquals(4, buildLock(set[1], scalp).chain.bones)
        assertEquals(own, buildLock(set[1], scalp).chain.physics)
        assertEquals(LockPhysics(), buildLock(set[0], scalp).chain.physics)
    }

    @Test
    fun anOverrideSetsOneLockAndHidesOrGuidesIt() {
        val base = group("o", LockGroupKind.FRINGE, 5)
        val guided = base.copy(overrides = listOf(LockOverride(2, guide = listOf(LockPoint(0.05f, -0.05f, 0f), LockPoint(0.08f, -0.12f, 0f))), LockOverride(4, hidden = true)))
        val locks = locksOf(guided, scalp)
        assertEquals(listOf(0, 1, 2, 3), locks.map { it.index })
        val built = buildLock(locks[2], scalp)
        assertEquals(3, built.guide.size)
        assertEquals(locks[2].root.x + 0.08f, built.guide.last().x, 1e-5f)
    }

    @Test
    fun aBuildKeepsEveryLockAndGroupAnEditLeavesAlone() {
        val builds = LockBuilds(null)
        val fringe = group("fringe", LockGroupKind.FRINGE, 7)
        val back = group("back", LockGroupKind.BACK, 9)
        val first = builds.withLocks(head, listOf(fringe, back))
        assertEquals(16, builds.lastBuilt)
        val fringePart = assertNotNull(first.part(lockGroupPartId(fringe.id)))
        val backPart = assertNotNull(first.part(lockGroupPartId(back.id)))

        val again = builds.withLocks(head, listOf(fringe, back))
        assertEquals(0, builds.lastBuilt, "nothing changed, nothing is built")
        assertSame(fringePart, again.part(lockGroupPartId(fringe.id)))

        val edited = fringe.withOverride(3, LockOverride(3, length = 0.15f))
        val oneLock = builds.withLocks(head, listOf(edited, back))
        assertEquals(1, builds.lastBuilt, "editing one lock builds only that lock")
        assertSame(backPart, oneLock.part(lockGroupPartId(back.id)), "the other group is the same part")
        assertNotEquals(fringePart, oneLock.part(lockGroupPartId(fringe.id)))

        builds.withLocks(head, listOf(edited, back.copy(curl = 0.5f)))
        assertEquals(9, builds.lastBuilt, "editing a group builds only its locks")
    }

    @Test
    fun aGroupIsOneMeshOfALockAPatchRiddenByItsChains() {
        val built = LockBuilds(null).withLocks(head, listOf(group("crown", LockGroupKind.CROWN, 5)))
        val part = assertNotNull(built.part(lockGroupPartId(LockGroupId("crown"))))
        assertEquals(Role.HAIR, part.role)
        assertEquals(5, part.surface.patches.size)
        val placing = assertNotNull(part.locks)
        assertEquals(headId, placing.head)
        assertEquals(part.surface.patches.map { it.name }, placing.chains.map { it.patch })
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4), placing.neighbours)
        assertNull(LockBuilds(null).withLocks(head, listOf(group("x", LockGroupKind.CROWN).copy(on = PartId("nobody")))).part(lockGroupPartId(LockGroupId("x"))))
    }

    @Test
    fun aTiedGroupWithABandBuildsTheBandAsAPartOfItsOwn() {
        val tail = group("tail", LockGroupKind.BACK, 6).copy(tie = 1f, band = SlotName("ribbon"))
        val built = LockBuilds(null).withLocks(head, listOf(tail))
        val band = assertNotNull(built.part(lockBandPartId(tail.id)))
        assertTrue(assertNotNull(band.locks).chains.isEmpty())
    }

    @Test
    fun everyPresetGivesNamedGroupsSizedToTheHead() {
        for (preset in HairPreset.entries) {
            val groups = presetGroups(preset, headId, null, 0.2f)
            assertEquals(presetGroupNames(preset), groups.map { it.id.value })
            assertEquals(groups.size, groups.map { it.id }.toSet().size)
            val twice = presetGroups(preset, headId, null, 0.4f)
            assertEquals(groups.first().length * 2f, twice.first().length, 1e-5f)
        }
        assertTrue(presetGroups(HairPreset.PONYTAIL, headId, null, 0.2f, band = SlotName("ribbon")).any { it.tie > 0f && it.band == SlotName("ribbon") })
        assertTrue(presetGroups(HairPreset.CURLY, headId, null, 0.2f).all { it.curl != 0f })
    }

    @Test
    fun theHeadGetsASphereOfItsOwn() {
        val shapes = autoColliders(head)
        val sphere = shapes.single()
        assertEquals(ColliderShape.SPHERE, sphere.shape)
        assertEquals(headId, sphere.part)
        assertTrue(sphere.radius > 0.01f && sphere.radius < headReach(scalp))
    }
}
