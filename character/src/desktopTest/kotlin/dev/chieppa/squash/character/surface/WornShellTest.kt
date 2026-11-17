package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.shipped
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A garment worn on the surface reads a shell of its own where a branch is drawn or runs past its part, open or whole round. */
class WornShellTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")

    /** A torso alone on [plan], resting as the plan's root rests: level on the canine plan, upright on the biped. */
    private fun body(plan: String, template: String): AssembledCharacter {
        val bodyPlan = shipped.plans.single { it.id.value == plan }
        val part = AssemblyPart(torso, library.latest(TemplateId(template))!!.ref, null, null, null)
        return assembleNow(listOf(part), { PartData() }, bodyPlan, library)
    }

    private val level by lazy { body("canine-quadruped", "torso") }
    private val upright by lazy { body("biped", "torso-upright") }

    private fun worn(body: AssembledCharacter, branch: GarmentBranch): WornCover {
        val garment = Garment(GarmentId("cape"), "Cape", GarmentSlotId("outer"), branches = listOf(branch))
        return WornCover.of(GarmentCover(garment, mapOf(branch.part to branch)), body, emptyList(), ShellCache())
    }

    /** A rectangle from [from] to [to] along x and [half] either side of y = 0, as joined straight cubics. */
    private fun box(from: Float, to: Float, half: Float): OutlineCurve {
        val corners = listOf(CurvePoint(from, -half), CurvePoint(to, -half), CurvePoint(to, half), CurvePoint(from, half), CurvePoint(from, -half))
        return OutlineCurve(listOf(corners.first()) + corners.zipWithNext { a, b ->
            listOf(CurvePoint(a.x + (b.x - a.x) / 3f, a.y + (b.y - a.y) / 3f), CurvePoint(a.x + 2f * (b.x - a.x) / 3f, a.y + 2f * (b.y - a.y) / 3f), b)
        }.flatten())
    }

    @Test
    fun anOpenBranchDrawnStandingOffReadsAShellTheWholeWayRound() {
        val cape = worn(level, GarmentBranch(torso, 0f, 1f, 0.03f, mapOf(View.SIDE to box(-1f, 3f, 2f)), fromV = -0.4f, toV = 0.4f))
        val shell = assertNotNull(cape.shells[torso]).surface.patches.single()
        assertTrue(shell.closedV, "the shell closes round, so it is a solid coverage cuts open")
        assertEquals(assertNotNull(level.part(torso)).surface.patches.first { it.slices != null }.nv, shell.nv)
        assertTrue(cape.hanging, "an open branch with a shell hangs over what comes out of its part")
        assertEquals(setOf(torso), cape.hangsOver)
    }

    @Test
    fun anOpenBranchRunningPastItsPartsEndReadsAShellAndOnePlainReadsNone() {
        val trailing = worn(level, GarmentBranch(torso, -0.3f, 1f, 0.03f, fromV = -0.4f, toV = 0.4f))
        assertNotNull(trailing.shells[torso])
        val plain = worn(level, GarmentBranch(torso, 0.1f, 0.9f, 0.03f, fromV = -0.4f, toV = 0.4f))
        assertTrue(plain.shells.isEmpty())
        assertFalse(plain.hanging)
    }

    @Test
    fun aBranchRunsPastAnEndAndHangsFreeOnlyWhereThatEndPointsDown() {
        val skirt = worn(upright, GarmentBranch(torso, -0.3f, 0.5f, 0.03f))
        assertEquals(setOf(torso), skirt.hangsFree, "below an upright torso the cloth hangs")
        val trailing = worn(level, GarmentBranch(torso, -0.3f, 1f, 0.03f))
        assertTrue(trailing.hangsFree.isEmpty(), "past the rump of a level torso it runs on, not hanging")
        assertFalse(trailing.hanging)
    }

    @Test
    fun aFrontOutlineIsTheShellsSection() {
        val square = box(-1f, 1f, 1f)
        val boxed = worn(level, GarmentBranch(torso, 0.2f, 0.8f, 0.03f, mapOf(View.FRONT to square)))
        val plain = worn(level, GarmentBranch(torso, 0.2f, 0.8f, 0.03f, mapOf(View.SIDE to box(-1f, 3f, 0.001f))))
        val ring = assertNotNull(boxed.shells[torso]).surface.patches.single().slices!![16]
        val eased = assertNotNull(plain.shells[torso]).surface.patches.single().slices!![16]
        fun reach(slice: dev.chieppa.squash.geom.Slice) = (0 until 64).maxOf { slice.section.pointAt(it / 64f).let { p -> abs(p.x) + abs(p.y) } }
        assertTrue(reach(ring) > 1.8f, "a square section reaches its corners, not ${reach(ring)}")
        assertTrue(reach(eased) < 1.7f, "the body's section does not, not ${reach(eased)}")
        assertEquals(eased.height, ring.height, 1e-3f, "a section alone leaves the eased size")
    }

    @Test
    fun pastALevelEndADrawingSetsTheSizeAndClosesWhereItEnds() {
        val drawn = box(-0.15f, 1f, 0.05f)
        val trailing = worn(level, GarmentBranch(torso, -0.4f, 1f, 0.03f, mapOf(View.SIDE to drawn, View.TOP to drawn)))
        val rings = assertNotNull(trailing.shells[torso]).surface.patches.single().slices!!
        val first = rings.first()
        assertEquals(0f, first.height, "past the drawing's end the shell closes")
        val inside = rings.first { it.u < 0f && it.height > 0f }
        assertEquals(0.1f, inside.height, 1e-3f, "past the rump the drawing's size holds, under the eased body's")
    }

    @Test
    fun pastAnEndThatPointsDownTheEaseHolds() {
        val drawn = box(-0.15f, 1f, 0.05f)
        val skirt = worn(upright, GarmentBranch(torso, -0.4f, 0.5f, 0.03f, mapOf(View.SIDE to drawn, View.TOP to drawn)))
        val rings = assertNotNull(skirt.shells[torso]).surface.patches.single().slices!!
        assertTrue(rings.all { it.height > 0.1f }, "below an upright torso no ring closes or shrinks under the ease")
    }
}
