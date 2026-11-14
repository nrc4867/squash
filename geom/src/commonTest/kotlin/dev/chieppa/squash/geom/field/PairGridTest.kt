package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

private const val RINGS = 13
private const val AROUND = 20
private const val SAMPLES = 4000

/** A swept solid read through its pair grid gives the least of its pairs' fields, inside, near and far from it. */
class PairGridTest {
    /** A run bending through a quarter turn, swelling and narrowing, with an oval section. */
    private val solid: SweptSolid = run {
        val middles = List(RINGS) { i ->
            val a = PI / 2 * i / (RINGS - 1)
            Vec3((cos(a) * 0.6).toFloat(), (sin(a) * 0.6).toFloat(), 0f)
        }
        val tangents = List(RINGS) { i ->
            val a = PI / 2 * i / (RINGS - 1)
            Vec3((-sin(a)).toFloat(), cos(a).toFloat(), 0f)
        }
        val points = List(RINGS) { i ->
            val r = 0.05f + 0.08f * sin(PI * i / (RINGS - 1)).toFloat()
            val side = tangents[i] cross Vec3.Z
            List(AROUND) { k ->
                val a = 2 * PI * k / AROUND
                middles[i] + Vec3.Z * (r * cos(a)).toFloat() + side * (r * 0.7f * sin(a)).toFloat()
            }
        }
        val vs = FloatArray(AROUND) { it.toFloat() / AROUND }
        requireNotNull(SweptSolid.ofRings(points, vs, tangents, List(RINGS) { Vec3.Z }, FloatArray(RINGS) { it / (RINGS - 1f) }))
    }

    @Test
    fun eachReadIsTheLeastOfEveryPair() {
        val random = Random(7)
        val low = solid.low - Vec3(0.4f, 0.4f, 0.4f)
        val size = solid.high - solid.low + Vec3(0.8f, 0.8f, 0.8f)
        repeat(SAMPLES) {
            val p = Vec3(low.x + random.nextFloat() * size.x, low.y + random.nextFloat() * size.y, low.z + random.nextFloat() * size.z)
            val least = (0 until solid.pairCount).minOf { solid.pairAt(p.x, p.y, p.z, it, null) }
            assertEquals(least, solid.distance(p), 1e-6f, "at $p")
        }
    }
}
