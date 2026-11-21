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
import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockSection
import dev.chieppa.squash.document.LockTrim
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.ShellFade
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Cuts on lock hair over a lone round head: tips cut to a plane, braids, buns and the short-hair shell with its fade. */
class HairCutsTest {
    private val headId = PartId("head")

    private val head: AssembledCharacter by lazy {
        val part = PartInstance(headId, TemplateRef(TemplateId("head-round"), 2), role = Role.HEAD)
        val character = Character(CharacterId("toy"), "Toy", BodyPlanRef(BodyPlanId("blank"), 1), parts = listOf(part))
        val plan = shipped.plans.firstOrNull { it.id.value == "blank" }
        assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, plan, TemplateLibrary(shipped.parts, shipped.plans))
    }

    private val scalp: Scalp by lazy { Scalp.of(assertNotNull(head.part(headId)), null) }

    private fun group(id: String, kind: LockGroupKind, count: Int = 6) = LockGroup(LockGroupId(id), kind, headId, count = count, length = 0.2f, width = 0.04f, thickness = 0.015f, fall = 0.9f)

    @Test
    fun aGuideIsCutWhereItFirstCrossesItsTrim() {
        val down = List(5) { Vec3(0f, -0.1f * it, 0f) }
        val cut = assertNotNull(trimmed(down, TrimPlane(Vec3(0f, -0.25f, 0f), Vec3(0f, 1f, 0f), blunt = true)))
        assertEquals(-0.25f, cut.guide.last().y, 1e-3f)
        assertEquals(0.625f, cut.share, 1e-2f)
        assertEquals(down.size, cut.guide.size)
        assertNull(trimmed(down, TrimPlane(Vec3(0f, -1f, 0f), Vec3(0f, 1f, 0f), blunt = true)), "a lock that never reaches its trim keeps its length")
    }

    @Test
    fun aTrimmedGroupEndsItsLocksLevelAndBlunt() {
        val whole = group("b", LockGroupKind.BACK)
        val level = -0.2f
        val trimmed = whole.copy(trim = LockTrim(level))
        val plane = TrimPlane.of(LockTrim(level), scalp)
        var cut = 0
        for ((lock, wholeLock) in locksOf(trimmed, scalp).zip(locksOf(whole, scalp))) {
            if (trimmed(guideOf(wholeLock, scalp), plane) == null) continue
            cut++
            assertEquals(plane.point.y, buildLock(lock, scalp).guide.last().y, 2e-3f, "lock ${lock.index} ends on the cut")
        }
        assertTrue(cut > 0, "the trim cuts the back's locks")
    }

    @Test
    fun aBraidIsThreeStrandsATieAndATailOnOneChain() {
        val braid = group("braid", LockGroupKind.BACK, 1).copy(section = LockSection.BRAID, tie = 1f, length = 0.4f, width = 0.06f, thickness = 0.05f, band = SlotName("ribbon"))
        val lock = buildLock(locksOf(braid, scalp).single(), scalp)
        assertEquals(5, lock.patches.size)
        assertEquals(lock.patches.map { it.name }, lock.chain.patches)
        assertNotNull(lock.tie)
        val part = assertNotNull(LockBuilds(null).withLocks(head, listOf(braid)).part(lockGroupPartId(braid.id)))
        val placing = assertNotNull(part.locks)
        assertEquals(listOf(SlotName("ribbon")), placing.layers.map { it.slot }, "the tie takes the band's slot")
    }

    @Test
    fun aCoiledTieWindsItsLocksRoundTheTieAsLongAsTheyAre() {
        val bun = group("bun", LockGroupKind.CROWN, 4).copy(tie = 1f, coil = 1.5f, rise = 80f, length = 0.3f)
        for (lock in locksOf(bun, scalp)) {
            val guide = guideOf(lock, scalp)
            val length = guide.zipWithNext { a, b -> (b - a).length }.sum()
            assertTrue(length in 0.8f * lock.length..1.3f * lock.length, "lock ${lock.index} runs ${length} for ${lock.length}")
            val reach = guide.maxOf { (it - lock.root).length }
            assertTrue(reach < 0.5f * lock.length, "lock ${lock.index} stays coiled near its tie: $reach")
        }
    }

    @Test
    fun aShellShowsItsToneAboveItsHairlineAndFadesUpTheSides() {
        val shell = HairShell(headId, fade = ShellFade.HIGH)
        assertEquals(0f, shellToneAt(shell, 0f, shell.hairline.front - 10f))
        assertEquals(1f, shellToneAt(shell, 0f, shell.hairline.front + 10f))
        val side = hairlineAt(shell.hairline, 90f)
        val rising = (0..8).map { shellToneAt(shell, 90f, side + it * 5f) }
        assertTrue(rising.zipWithNext().all { (a, b) -> b >= a }, "the fade only rises up the side: $rising")
        assertTrue(rising.zipWithNext().all { (a, b) -> b - a < 0.35f }, "with no hard band: $rising")
        assertEquals(1f, shellToneAt(shell, 90f, side + 45f), 1e-4f)
    }

    @Test
    fun aShellIsBuiltOverItsHeadAndKeptWhileNothingChanges() {
        val shell = ShellSpec(HairShell(headId), SlotName("skin"))
        val builds = LockBuilds(null)
        val first = assertNotNull(builds.withLocks(head, emptyList(), cut = listOf(shell)).part(shellPartId(headId)))
        val placing = assertNotNull(first.locks)
        assertEquals(listOf(SlotName("skin")), placing.layers.map { it.slot })
        val again = assertNotNull(builds.withLocks(head, emptyList(), cut = listOf(shell)).part(shellPartId(headId)))
        assertSame(first, again)
        val points = first.shownPoints()
        val reach = headReach(scalp)
        assertTrue(points.all { (it - scalp.middle).length > 0.5f * reach }, "the shell lies on the scalp, not inside the head")
    }
}
