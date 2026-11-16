package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.Coverage
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How far past the leg's end a point of a lid over it lies, in cells, the u it reads there, and how far coverage is read, in cells. */
private const val BEYOND_CELLS = 2f
private const val SHORT_OF_END = 0.9f
private const val REACH_CELLS = 4f

/** A part coming out through the end, as a ball this wide, and how far from the point past the end its surface lies, in the garment's ease. */
private const val BALL = 0.05f
private const val BALL_EASES = 2f

/** How many rings in from the leg's end its way out is read from. */
private const val AXIS_RINGS = 3

/**
 * A sweater down the whole leg, whose end a paw comes out through: the garment grown over the leg's closed end covers nothing past the
 * ring at that end, so the paw comes out through an opening rather than a lid; past the end, it covers a point only under a part
 * coming out through the end that sits over the point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenEndCoverageTest {
    @Test
    fun aGarmentReachingAnEndAPartComesOutThroughCoversNothingPastThatEndsRing() = runTest {
        val session = stackSession(legTo = 1f, withPaw = true)
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        val garment = assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.first { StackFixture.leg.id in it.cover.branches }
        assertEquals(true, garment.cover.openEnds[StackFixture.leg.id]?.high, "the paw comes out through the leg's end")
        val run = surface.field.runs.indexOfFirst { it.partId == StackFixture.leg.id && it.first }
        val patch = surface.field.runs[run].patch
        fun middle(ring: Int) = (0 until patch.nv).fold(Vec3.ZERO) { sum, c -> sum + patch.point(patch.index(ring, c)) } * (1f / patch.nv)
        val end = middle(patch.nu - 1)
        val out = (end - middle(patch.nu - 1 - AXIS_RINGS)).normalized()
        val member = surface.field.field.members[run]
        val mesh = garment.surface.mesh
        var covered = 0
        for (v in 0 until mesh.vertexCount) {
            if (garment.coverage[v] < 0f) continue
            covered++
            val past = (member.local(mesh.position(v)) - end) dot out
            assertTrue(past <= 0f, "covered vertex $v lies $past past the leg's end ring")
        }
        assertTrue(covered > 0)

        val beyond = member.origin + end + out * (surface.mesh.cell * BEYOND_CELLS)
        val lid = SurfaceMesh(
            surface.mesh.cell, floatArrayOf(beyond.x, beyond.y, beyond.z), floatArrayOf(out.x, out.y, out.z), IntArray(0), intArrayOf(run), intArrayOf(-1),
            floatArrayOf(1f), floatArrayOf(0f), floatArrayOf(0f), IntArray(HELD) { if (it == 0) run else -1 }, FloatArray(HELD) { if (it == 0) 1f else 0f },
            FloatArray(HELD) { if (it == 0) SHORT_OF_END else 0f }, FloatArray(HELD), FloatArray(HELD), 1,
        )
        val reach = surface.mesh.cell * REACH_CELLS
        assertTrue(Coverage(surface.field, garment.cover, reach).at(lid, 0) < 0f, "a point past the end's ring is open, whatever u it reads")

        val ease = checkNotNull(garment.cover.branchOf(StackFixture.leg.id)).ease
        val clear = BALL + ease * BALL_EASES
        val side = (out cross Vec3(0f, 0f, 1f)).let { if (it.length > 0f) it.normalized() else (out cross Vec3(1f, 0f, 0f)).normalized() }
        fun ball(at: Vec3) = JoinedMember(Ball(at, BALL), Vec3.ZERO, false, 0f)
        assertTrue(Coverage(surface.field, garment.cover, reach, ends = listOf(ball(beyond + out * clear))).at(lid, 0) >= 0f, "a part sitting over the end covers the point under it")
        assertTrue(Coverage(surface.field, garment.cover, reach, ends = listOf(ball(beyond + side * clear))).at(lid, 0) < 0f, "a part rising beside the point leaves it open")
    }

    /** A ball about [centre] of [radius], placed in body space. */
    private class Ball(private val centre: Vec3, private val radius: Float) : Solid {
        override fun distance(p: Vec3): Float = (p - centre).length - radius
        override val low: Vec3 = centre - Vec3(radius, radius, radius)
        override val high: Vec3 = centre + Vec3(radius, radius, radius)
    }
}
