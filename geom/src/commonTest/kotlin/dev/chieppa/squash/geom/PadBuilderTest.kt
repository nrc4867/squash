package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PadBuilderTest {
    private val run = SliceBuilder.tube(
        TubeSpec(
            name = "body",
            axis = AxisSpline(listOf(Vec3.ZERO, Vec3.X)),
            uStart = 0f,
            uEnd = 1f,
            rings = 24,
            segments = 16,
            section = SectionProfile.CIRCLE.spline(16),
            radius = 0.3f,
            startClosure = EndClosure.ROUND,
            endClosure = EndClosure.ROUND,
        ),
    )

    @Test
    fun theSurfaceNormalPointsOutward() {
        val under = run.surfaceAt(0.5f, 0.5f)
        assertEquals(-0.3f, under.position.y, 2e-2f)
        assertEquals(-1f, under.normal.y, 2e-2f)
        val top = run.surfaceAt(0.5f, 0f)
        assertEquals(1f, top.normal.y, 2e-2f)
    }

    @Test
    fun aPadIsClosedFacesOutAndRisesFromTheSurface() {
        val pad = PadBuilder.pad("pad:palm", run, 0.5f, 0.5f, 0.2f, 7, 12)
        val mesh = MeshBuilder.build(pad) { 0 }
        assertEquals(emptyList(), MeshInvariants.check(mesh, openRings = emptySet()))
        assertTrue(MeshInvariants.signedVolume(mesh) > 0f)
        assertNull(pad.slices)
        val lowest = (0 until mesh.vertexCount).minOf { pad.point(it).y }
        assertTrue(lowest < run.surfaceAt(0.5f, 0.5f).position.y, "the pad stands proud of the underside")
    }

    @Test
    fun aPadsVerticesTakeTheBoneAtItsSite() {
        val pad = PadBuilder.pad("pad:palm", run, 0.6f, 0.5f, 0.2f, 7, 12)
        val mesh = MeshBuilder.build(pad) { if (it < 0.5f) 0 else 1 }
        assertTrue(mesh.bones.all { it == 1 })
    }
}
