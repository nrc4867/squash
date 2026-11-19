package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val RINGS = 6
private const val AROUND = 24

/** A run refined with more rings reads as it did, and grown ring by ring stands out by what each ring grew, its u and v kept. */
class SweptRegrowthTest {
    /** An upright tube narrowing from radius 0.1 at its foot to 0.06 at its top, u 0 to 1. */
    private val tube: SweptSolid = run {
        val middles = List(RINGS) { Vec3(0f, it / (RINGS - 1f), 0f) }
        val points = List(RINGS) { i ->
            val r = 0.1f - 0.04f * i / (RINGS - 1f)
            List(AROUND) { k ->
                val a = 2 * PI * k / AROUND
                middles[i] + Vec3((r * cos(a)).toFloat(), 0f, (r * sin(a)).toFloat())
            }
        }
        val vs = FloatArray(AROUND) { it.toFloat() / AROUND }
        requireNotNull(SweptSolid.ofRings(points, vs, List(RINGS) { Vec3.Y }, List(RINGS) { Vec3.X }, FloatArray(RINGS) { it / (RINGS - 1f) }))
    }

    @Test
    fun aRefinedRunReadsAsItDidWithItsPlaces() {
        val fine = tube.refined(1f / 64)
        assertTrue(fine.rings().count > tube.rings().count * 4)
        assertSame(tube, tube.refined(1f))
        val out = FloatArray(PLACE_ARRAY)
        val again = FloatArray(PLACE_ARRAY)
        for (p in listOf(Vec3(0.12f, 0.3f, 0f), Vec3(0f, 0.55f, -0.05f), Vec3(0.02f, 0.9f, 0.02f), Vec3(0.3f, 0.5f, 0.1f))) {
            tube.place(p, out)
            fine.place(p, again)
            assertEquals(out[PLACE_FIELD], again[PLACE_FIELD], 2e-3f, "field at $p")
            assertEquals(out[PLACE_U], again[PLACE_U], 1e-2f, "u at $p")
            assertEquals(out[PLACE_V], again[PLACE_V], 1e-2f, "v at $p")
        }
    }

    @Test
    fun aRunGrownRingByRingStandsOutByWhatEachRingGrew() {
        val fine = tube.refined(1f / 32)
        val rings = fine.rings()
        val grown = FloatArray(rings.radii.size) { i -> rings.radii[i] + 0.04f * rings.us[i / RunRings.ANGLES] }
        val wider = fine.withRadii(grown)
        val out = FloatArray(PLACE_ARRAY)
        for (u in listOf(0.2f, 0.5f, 0.8f)) {
            val p = Vec3(0f, u, 0f)
            val radius = 0.1f - 0.04f * u
            assertEquals(radius, -fine.distance(p), 2e-3f)
            assertEquals(radius + 0.04f * u, -wider.distance(p), 2e-3f, "grown at u $u")
            wider.place(Vec3(radius + 0.04f * u, u, 0f), out)
            assertEquals(0f, out[PLACE_FIELD], 2e-3f)
            assertEquals(u, out[PLACE_U], 1e-2f)
        }
    }
}
