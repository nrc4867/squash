package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A field lofts to rings on its surface that close in poles at its ends, slices that pass through the grid, and outward normals. */
class FieldLoftTest {
    private val axes = PartAxes.of(Vec3.X, Vec3.Y)

    @Test
    fun aSphereLoftsOntoItsSurfaceWithPolesAtItsEnds() {
        val field = BlendedField(listOf(Blended(Ellipsoid(Vec3(0.5f, 0f, 0f), Vec3(0.2f, 0.2f, 0.2f)))))
        val patch = FieldLoft.loft("body", field, axes, rings = 12, segments = 16, uOf = { it })
        assertTrue(patch.isPole(0) && patch.isPole(patch.nu - 1))
        for (i in 0 until patch.nu * patch.nv) assertEquals(0.2f, (patch.point(i) - Vec3(0.5f, 0f, 0f)).length, 0.004f)
        assertEquals(0.3f, patch.ringUs.first(), 0.003f)
        assertEquals(0.7f, patch.ringUs.last(), 0.003f)
        val normals = requireNotNull(patch.proxyNormals)
        for (i in 0 until patch.nu * patch.nv) {
            val outward = (patch.point(i) - Vec3(0.5f, 0f, 0f)).normalized()
            assertTrue(Vec3(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]) dot outward > 0.98f)
        }
    }

    @Test
    fun aSlicesSamplesAreTheGridItWasFittedThrough() {
        val field = BlendedField(listOf(Blended(Ellipsoid(Vec3(0f, 0f, 0f), Vec3(0.3f, 0.1f, 0.2f))), Blended(Capsule(Vec3(-0.2f, 0f, 0f), Vec3(-0.5f, 0f, 0f), 0.06f), 0.05f)))
        val patch = FieldLoft.loft("body", field, axes, rings = 16, segments = 12, uOf = { it })
        val slices = requireNotNull(patch.slices)
        val ring = 7
        val slice = slices[ring]
        for (j in 0 until patch.nv) assertEquals(0f, (slice.pointAt(j.toFloat() / patch.nv) - patch.point(patch.index(ring, j))).length, 1e-4f)
        for (i in 0 until patch.nu * patch.nv) assertTrue(abs(field.value(patch.point(i))) < 0.002f)
    }

    @Test
    fun theUnionFillsWhereTwoSolidsMeetAndNowhereElse() {
        val a = Ellipsoid(Vec3.ZERO, Vec3(0.2f, 0.2f, 0.2f))
        val b = Capsule(Vec3(0.15f, 0f, 0f), Vec3(0.6f, 0f, 0f), 0.05f)
        val field = BlendedField(listOf(Blended(a), Blended(b, 0.06f)))
        val crease = Vec3(0.2f, 0.07f, 0f)
        assertTrue(field.value(crease) < minOf(a.distance(crease), b.distance(crease)))
        val far = Vec3(-0.25f, 0f, 0f)
        assertEquals(a.distance(far), field.value(far), 1e-6f)
    }

    @Test
    fun throughPassesThroughItsPoints() {
        val points = listOf(Vec2(0f, 1f), Vec2(0.8f, 0.6f), Vec2(1f, -0.2f), Vec2(0.1f, -1f), Vec2(-0.9f, -0.4f), Vec2(-0.7f, 0.7f))
        val spline = FieldLoft.through(points)
        points.forEachIndexed { j, p -> assertEquals(0f, (spline.pointAt(j.toFloat() / points.size) - p).let { kotlin.math.hypot(it.x, it.y) }, 1e-4f) }
    }
}
