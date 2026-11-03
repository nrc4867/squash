package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SliceBuilderTest {
    private fun spec(start: EndClosure, end: EndClosure, joints: List<Float> = emptyList()) = TubeSpec(
        name = "body",
        axis = AxisSpline(listOf(Vec3.ZERO, Vec3(0f, -1f, 0f))),
        uStart = 0f,
        uEnd = 1f,
        rings = 24,
        segments = 16,
        section = SectionProfile.CIRCLE.spline(16),
        radius = 0.28f,
        startClosure = start,
        endClosure = end,
        jointUs = joints,
    )

    private fun mesh(patch: Patch) = MeshBuilder.build(patch) { 0 }

    @Test
    fun theGridIsFixedByRingsAndSegments() {
        val patch = SliceBuilder.tube(spec(EndClosure.OPEN, EndClosure.ROUND))
        assertEquals(24, patch.nu)
        assertEquals(16, patch.nv)
        assertEquals(24 * 16 * 3, patch.points.size)
        assertEquals(24, patch.slices?.size)
        assertEquals(0f, patch.slices?.first()?.u)
        assertEquals(1f, patch.slices?.last()?.u)
    }

    @Test
    fun aClosedTubeIsWatertightAndFacesOut() {
        for (closure in listOf(EndClosure.ROUND, EndClosure.FLAT, EndClosure.POINTED)) {
            val built = mesh(SliceBuilder.tube(spec(closure, closure)))
            assertEquals(emptyList(), MeshInvariants.check(built, openRings = emptySet()), closure.name)
            assertTrue(MeshInvariants.signedVolume(built) > 0f, closure.name)
        }
    }

    @Test
    fun onlyTheOpenRingHasAHole() {
        val built = mesh(SliceBuilder.tube(spec(EndClosure.OPEN, EndClosure.ROUND)))
        assertEquals(emptyList(), MeshInvariants.check(built, openRings = setOf(0)))
        assertTrue(MeshInvariants.check(built, openRings = emptySet()).isNotEmpty())
    }

    @Test
    fun aJointLandsOnARing() {
        val patch = SliceBuilder.tube(spec(EndClosure.OPEN, EndClosure.ROUND, joints = listOf(0.55f)))
        assertTrue(patch.slices!!.any { it.u == 0.55f })
    }

    @Test
    fun taperNarrowsTowardTheEnd() {
        val patch = SliceBuilder.tube(spec(EndClosure.OPEN, EndClosure.OPEN).copy(taper = 0.5f))
        val slices = patch.slices!!
        assertEquals(0.56f, slices.first().height, 1e-4f)
        assertEquals(0.28f, slices.last().height, 1e-4f)
    }

    @Test
    fun theSectionMustHaveOneControlPointPerSegment() {
        val wrong = spec(EndClosure.OPEN, EndClosure.ROUND).copy(section = SectionProfile.CIRCLE.spline(8))
        assertFailsWith<IllegalArgumentException> { SliceBuilder.tube(wrong) }
    }

    @Test
    fun vertexTagsFollowTheGrid() {
        val built = MeshBuilder.build(SliceBuilder.tube(spec(EndClosure.OPEN, EndClosure.ROUND))) { if (it < 0.5f) 0 else 1 }
        val patch = built.patch
        assertEquals(0.25f, built.vs[patch.index(3, 4)])
        assertEquals(patch.slices!![3].u, built.us[patch.index(3, 4)])
        assertEquals(0, built.bones[patch.index(0, 0)])
        assertEquals(1, built.bones[patch.index(23, 0)])
    }
}
