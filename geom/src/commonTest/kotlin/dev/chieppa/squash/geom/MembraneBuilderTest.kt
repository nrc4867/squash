package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val RAIL_RINGS = 12
private const val RAIL_SEGMENTS = 10
private const val WEB_RINGS = 6
private const val WEB_SEGMENTS = 12
private const val RADIUS = 0.1f
private val SHAPE = MembraneShape(thickness = 0.35f, sag = 0.4f)

class MembraneBuilderTest {
    /** A digit along x from [root], its rings' u from 0.5 to 1. */
    private fun digit(name: String, root: Vec3): Patch {
        val us = FloatArray(RAIL_RINGS) { 0.5f + 0.5f * it / (RAIL_RINGS - 1) }
        val size = FloatArray(RAIL_RINGS) { RADIUS * 2f }
        return SliceBuilder.sweep(
            SweepSpec(name, us, us.map { root + Vec3.X * (it - 0.5f) }, size, size.copyOf(), SectionProfile.CIRCLE.spline(RAIL_SEGMENTS), Vec3.Y, EndClosure.ROUND, EndClosure.ROUND),
        )
    }

    private fun web(gap: Float): Patch {
        val first = digit("toes:0", Vec3(0f, 0f, -gap / 2f))
        val second = digit("toes:1", Vec3(0f, 0f, gap / 2f))
        return MembraneBuilder.membrane("web:0", MembraneRail(first, 0.5f, 0.65f), MembraneRail(second, 0.5f, 0.65f), WEB_RINGS, WEB_SEGMENTS, SHAPE)
    }

    @Test
    fun aWebIsClosedFacesOutAndKeepsItsGrid() {
        for (gap in listOf(RADIUS * 2f, RADIUS * 3f, RADIUS * 6f)) {
            val web = web(gap)
            assertEquals(WEB_RINGS, web.nu)
            assertEquals(WEB_SEGMENTS, web.nv)
            val mesh = MeshBuilder.build(web) { 0 }
            assertEquals(emptyList(), MeshInvariants.check(mesh, openRings = emptySet()), "at a gap of $gap")
            assertTrue(MeshInvariants.signedVolume(mesh) > 0f, "the web faces out at a gap of $gap")
        }
    }

    @Test
    fun itsEndsSitInsideTheDigitsAndItsMiddleIsThin() {
        val gap = RADIUS * 4f
        val web = web(gap)
        assertTrue(web.isPole(0) && web.isPole(web.nu - 1), "each end closes to a point")
        for ((ring, side) in listOf(0 to -gap / 2f, 1 to -gap / 2f, web.nu - 2 to gap / 2f, web.nu - 1 to gap / 2f)) {
            for (column in 0 until web.nv) {
                val p = web.point(web.index(ring, column))
                val off = Vec3(0f, p.y, p.z - side).length
                assertTrue(off < RADIUS, "ring $ring column $column lies ${off / RADIUS} radii from its digit's middle")
            }
        }
        val middle = web.nu / 2
        val under = web.point(web.index(middle, WEB_SEGMENTS / 4))
        val over = web.point(web.index(middle, WEB_SEGMENTS - 1 - WEB_SEGMENTS / 4))
        assertTrue((under - over).length < RADIUS, "the web is thinner than a digit half way between them")
    }

    @Test
    fun everyVertexRidesBothDigitsAndItsEdgeDips() {
        val web = web(RADIUS * 4f)
        val spanned = requireNotNull(web.spanned)
        assertEquals("toes:0", spanned.first)
        assertEquals("toes:1", spanned.second)
        for (i in 0 until web.nu * web.nv) {
            assertTrue(spanned.shares[i] in 0f..1f)
            assertTrue(spanned.firstUs[i] in 0.5f..0.65f && spanned.secondUs[i] in 0.5f..0.65f)
        }
        assertEquals(0f, spanned.shares[web.index(0, 0)])
        assertEquals(1f, spanned.shares[web.index(web.nu - 1, 0)])
        val lip = WEB_SEGMENTS / 2 - 1
        val nearDigit = web.point(web.index(1, lip)).x
        val middle = web.point(web.index(web.nu / 2, lip)).x
        assertTrue(middle < nearDigit, "the free edge dips between the digits: $middle against $nearDigit")
        assertEquals(0.575f, web.boneUOf(web.index(0, 0)), 1e-5f)
    }

    @Test
    fun aSpannedPatchOpenedInVKeepsWhereItsVerticesRide() {
        val web = web(RADIUS * 4f)
        val opened = web.openedInV()
        val spanned = requireNotNull(opened.spanned)
        assertEquals(opened.nu * opened.nv, spanned.vertexCount)
        assertEquals(web.spanned?.shares?.get(web.index(2, 0)), spanned.shares[opened.index(2, opened.nv - 1)])
    }
}
