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
import dev.chieppa.squash.document.Cowlick
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Parting
import dev.chieppa.squash.document.PartingKind
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.ScalpFlow
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.Whorl
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scalp's flow on a lone round head: roots keep off a parting and the locks beside it lie away from it, a whorl turns the locks round
 * it the way it turns, a cowlick lifts and turns only the locks it reaches, and an edit of one builds only the locks whose way it changed.
 */
class ScalpFlowTest {
    private val headId = PartId("head")

    private val head: AssembledCharacter by lazy {
        val part = PartInstance(headId, TemplateRef(TemplateId("head-round"), 2), role = Role.HEAD)
        val character = Character(CharacterId("toy"), "Toy", BodyPlanRef(BodyPlanId("blank"), 1), parts = listOf(part))
        val plan = shipped.plans.firstOrNull { it.id.value == "blank" }
        assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, plan, TemplateLibrary(shipped.parts, shipped.plans))
    }

    private val scalp: Scalp by lazy { Scalp.of(assertNotNull(head.part(headId)), null) }

    private fun group(id: String, kind: LockGroupKind, count: Int) = LockGroup(LockGroupId(id), kind, headId, count = count, length = 0.1f, width = 0.03f, thickness = 0.012f)

    private fun degreesBetween(a: Vec3, b: Vec3): Float = (acos((a.normalized() dot b.normalized()).coerceIn(-1f, 1f)) * 180.0 / PI).toFloat()

    @Test
    fun rootsKeepOffAPartingAndTheLocksBesideItLieAwayFromIt() {
        val field = ScalpField(ScalpFlow(headId, parting = Parting(PartingKind.SIDE, at = 0.5f, clean = 1f)))
        val line = assertNotNull(field.partLine)
        val top = group("top", LockGroupKind.TOP, 16)
        val locks = locksOf(top, scalp, field)
        assertEquals(16, locks.size)
        for (lock in locks) {
            val off = line.offset(lock.out)
            assertTrue(abs(off) >= PART_GAP - 1e-3f, "a root keeps off the line: $off degrees")
            assertTrue((lock.growth dot line.away(lock.out, off)) > 0f, "a lock beside the line lies away from it")
        }
        assertTrue(locks.any { line.offset(it.out) < 0f } && locks.any { line.offset(it.out) > 0f }, "a top group roots either side of the line")
        for (lock in locksOf(group("fringe", LockGroupKind.FRINGE, 9), scalp, field) + locksOf(group("crown", LockGroupKind.CROWN, 9), scalp, field)) {
            assertTrue(line.beyond(PartLine.alongOf(lock.out)) > 0f || abs(line.offset(lock.out)) >= PART_GAP - 1e-3f, "no group roots on the line")
        }
        assertNull(ScalpField(ScalpFlow(headId)).partLine, "no parting, no line")
    }

    @Test
    fun aWhorlTurnsTheLocksRoundItTheWayItTurns() {
        val crown = group("crown", LockGroupKind.CROWN, 12)
        fun turnRound(turn: Float): Float {
            val field = ScalpField(ScalpFlow(headId, Whorl(turn = turn)))
            val middle = Scalp.direction(180f, Whorl().up)
            return locksOf(crown, scalp, field).filter { degreesBetween(it.out, middle) > 2f }.sumOf { lock ->
                val radial = (lock.out - middle).let { it - lock.out * (it dot lock.out) }.normalized()
                ((lock.growth dot (radial cross lock.out)).toDouble())
            }.toFloat()
        }
        assertTrue(turnRound(0.8f) > 0f, "a whorl turning clockwise seen from above turns its locks that way")
        assertTrue(turnRound(-0.8f) < 0f, "and the other way")
        val bent = locksOf(crown, scalp, ScalpField(ScalpFlow(headId, Whorl(turn = 0.8f))))
        assertTrue(bent.all { it.sweep < 0f }, "locks round a whorl bend round it as they grow")
    }

    @Test
    fun aCowlickLiftsAndTurnsOnlyTheLocksItReaches() {
        val fringe = group("fringe", LockGroupKind.FRINGE, 11)
        val cowlick = Cowlick(around = 0f, up = 46f, radius = 14f, turn = 0.4f, lift = 25f)
        val plain = locksOf(fringe, scalp, ScalpField(ScalpFlow(headId)))
        val licked = locksOf(fringe, scalp, ScalpField(ScalpFlow(headId, cowlicks = listOf(cowlick))))
        val at = Scalp.direction(cowlick.around, cowlick.up)
        var inside = 0
        for ((a, b) in plain.zip(licked)) {
            if (degreesBetween(a.out, at) < cowlick.radius) {
                inside++
                assertTrue(b.rise > a.rise, "a lock over the cowlick lifts")
            } else {
                assertEquals(a, b, "a lock past the cowlick's reach is as it was")
            }
        }
        assertTrue(inside in 1 until plain.size, "the cowlick reaches some locks and not others: $inside")
    }

    @Test
    fun aCowlickEditBuildsOnlyTheLocksInsideItsRadius() {
        val groups = listOf(group("fringe", LockGroupKind.FRINGE, 11), group("back", LockGroupKind.BACK, 9), group("top", LockGroupKind.TOP, 10))
        val cowlick = Cowlick(around = 10f, up = 50f, radius = 16f, turn = 0.5f, lift = 20f)
        val builds = LockBuilds(null)
        builds.withLocks(head, groups, listOf(ScalpFlow(headId)))
        assertEquals(30, builds.lastBuilt)
        val flow = ScalpFlow(headId, cowlicks = listOf(cowlick))
        builds.withLocks(head, groups, listOf(flow))
        val at = Scalp.direction(cowlick.around, cowlick.up)
        val inside = groups.flatMap { locksOf(it, scalp, ScalpField(flow)) }.count { degreesBetween(it.out, at) < cowlick.radius }
        assertTrue(inside in 1..15, "the cowlick reaches some of the locks: $inside")
        assertEquals(inside, builds.lastBuilt, "only the locks inside the cowlick's radius are built again")
        builds.withLocks(head, groups, listOf(flow.copy(cowlicks = listOf(cowlick.copy(strength = 0.5f)))))
        assertEquals(inside, builds.lastBuilt, "a cowlick's strength edit builds only its own locks")
    }

    @Test
    fun aPartingBuildsAStripOfScalpAlongItColouredAsTheHead() {
        val groups = listOf(group("top", LockGroupKind.TOP, 10))
        val parted = LockBuilds(null).withLocks(head, groups, listOf(ScalpFlow(headId, parting = Parting(PartingKind.CENTRE))))
        val strip = assertNotNull(parted.part(partingPartId(headId)), "a parting shows a strip of scalp")
        assertTrue(assertNotNull(strip.locks).chains.isEmpty(), "the strip rides the head whole")
        assertNull(LockBuilds(null).withLocks(head, groups, listOf(ScalpFlow(headId))).part(partingPartId(headId)), "no parting, no strip")
    }
}
