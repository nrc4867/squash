package dev.chieppa.squash.geom.blend

import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImplicitBlendTest {
    private val palm = tube("palm", Vec3(0f, -1f, 0f), Vec3(0f, 0f, 0f), 0.5f)
    private val finger = tube("finger", Vec3(0f, -0.1f, 0f), Vec3(0f, 1.2f, 0f), 0.2f)
    private val beside = tube("beside", Vec3(0.75f, -1f, 0f), Vec3(0.75f, 0f, 0f), 0.2f)

    @Test
    fun aFieldIsZeroOnItsSurfaceAndGrowsOutward() {
        val samples = PatchSamples.of(palm, FIELD_SAMPLES)
        val field = assertNotNull(HermiteField.fit(samples.points, samples.normals))
        val gradient = DoubleArray(3)
        assertTrue(field.at(Vec3(0f, -0.5f, 0f), gradient) < -0.2)
        assertTrue(abs(field.at(Vec3(0.5f, -0.5f, 0f), gradient)) < 0.02)
        assertTrue(gradient[0] > 0.8, "outward at the side: ${gradient.toList()}")
        assertTrue(field.at(Vec3(0.8f, -0.5f, 0f), gradient) > 0.2)
    }

    @Test
    fun aCrossingGrowsAFilletAndLeavesTheRestWhereItWas() {
        val blended = ImplicitBlend(GradientBlend(radius = 0.3f)).apply(listOf(palm, finger))
        for ((before, after) in listOf(palm, finger).zip(blended)) {
            assertEquals(before.nu, after.nu)
            assertEquals(before.nv, after.nv)
            assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(after) { 0 }, emptySet()))
        }
        val moved = finger.vertices().zip(blended[1].vertices()).map { (a, b) -> a to (b - a).length }
        val far = moved.filter { (a, _) -> a.y > 1f }
        assertTrue(far.all { it.second == 0f }, "the finger's far end stays")
        assertTrue(palm.vertices().zip(blended[0].vertices()).filter { it.first.y < -0.5f }.all { it.first == it.second }, "the palm's far end stays")
        // The finger crosses the palm's round top at y = 0.46; just above it, it swells outward into a fillet.
        val root = moved.filter { (a, _) -> a.y in 0.47f..0.6f && abs(a.x) > 0.1f }
        assertTrue(root.all { it.second > 0.005f }, "the finger's root moves: ${root.map { it.second }}")
        assertTrue(moved.all { it.second <= 0.3f / 4f + 0.01f }, "nothing moves further than the fillet reaches")
    }

    @Test
    fun surfacesFacingAcrossAGapDoNotBlend() {
        val near = tube("near", Vec3(0.8f, -1f, 0f), Vec3(0.8f, 0f, 0f), 0.2f)
        val blended = ImplicitBlend(GradientBlend(radius = 0.15f)).apply(listOf(palm, near))
        assertSame(near, blended[1])
    }

    @Test
    fun onlyTheJoinedPatchesMove() {
        val blend = ImplicitBlend(GradientBlend(radius = 0.3f), joins = { a, b -> setOf(a, b) == setOf("palm", "finger") })
        val blended = blend.apply(listOf(palm, finger, beside))
        assertSame(beside, blended[2])
    }

    @Test
    fun aStillPatchKeepsItsPlaceAndTheOtherCoversTheFillet() {
        val blended = ImplicitBlend(GradientBlend(radius = 0.3f), moves = { it == "finger" }).apply(listOf(palm, finger))
        assertSame(palm, blended[0])
        // Just inside the palm's top the finger rises toward the fillet instead of staying buried.
        val buried = finger.vertices().zip(blended[1].vertices()).filter { (a, _) -> a.y in 0.36f..0.45f && abs(a.x) > 0.1f }
        assertTrue(buried.isNotEmpty() && buried.all { (a, b) -> b.y > a.y || (b - a).length > 0.005f }, "the buried root rises")
    }
}
