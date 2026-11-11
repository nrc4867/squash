package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.FaceMap
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The upright round head: a face drawn as a profile in Side, a ridge for a nose, a swelling for a muzzle, and the flat face map. */
class FaceHeadTest {
    private val head: PartTemplate = shipped.parts.single { it.id.value == "head-round" && it.version == 2 }

    /** A head 0.5 tall from its chin, its back at -0.22 and its face at 0.15 forward, with [front] standing out of the face between those heights. */
    private fun side(vararg front: Pair<Float, Float>): OutlineCurve =
        outline(0f to 0.1f, *front, 0.5f to 0.0f, 0.45f to -0.2f, 0.25f to -0.22f, 0.05f to -0.15f)

    private val nose = side(0.2f to 0.15f, 0.26f to 0.21f, 0.3f to 0.15f, 0.4f to 0.14f)
    private val muzzle = side(0.08f to 0.15f, 0.12f to 0.26f, 0.38f to 0.26f, 0.42f to 0.15f)
    private val width = outline(0f to 0f, 0.05f to 0.15f, 0.45f to 0.17f, 0.5f to 0f, 0.45f to -0.17f, 0.05f to -0.15f)

    private fun body(side: OutlineCurve): Patch =
        assertNotNull(PartBuilder.build(head, outlines = mapOf(View.SIDE to side, View.TOP to width)).firstOrNull { it.name == BODY_PATCH })

    /** How far forward the ring nearest [height] reaches at [across] of its half width out from the middle. */
    private fun forwardAt(patch: Patch, height: Float, across: Float): Float {
        val ring = (0 until patch.nu).minBy { r -> abs(patch.ring(r).map { it.y }.average().toFloat() - height) }
        val points = patch.ring(ring)
        val half = points.maxOf { abs(it.z) }
        return points.filter { it.x > 0f }.minBy { abs(abs(it.z) - across * half) }.x
    }

    @Test
    fun itFacesForwardWithItsAxisUp() {
        assertEquals(Vec3.X, PartBuilder.facing(head))
        assertEquals(Vec3.Y, PartBuilder.axes(head).along)
    }

    @Test
    fun aNoseInTheProfileStandsOutAsANarrowRidge() {
        val patch = body(nose)
        assertEquals(0.21f, forwardAt(patch, 0.26f, 0f), 0.02f)
        assertTrue(forwardAt(patch, 0.26f, 0.5f) < 0.16f, "the cheek beside the nose stays with the face: ${forwardAt(patch, 0.26f, 0.5f)}")
    }

    @Test
    fun aMuzzleInTheProfileSwellsTheWholeFront() {
        val patch = body(muzzle)
        assertEquals(0.26f, forwardAt(patch, 0.25f, 0f), 0.02f)
        assertTrue(forwardAt(patch, 0.25f, 0.5f) > 0.2f, "a snout as tall as the face carries its sides forward: ${forwardAt(patch, 0.25f, 0.5f)}")
    }

    @Test
    fun theFaceMapLandsWhereTheFrontViewShowsIt() {
        val data = PartData(mapOf(View.SIDE to nose, View.TOP to width))
        val face = assertNotNull(FaceMap.of(PartId("head"), head, data))
        val patch = body(nose)
        for ((x, y) in listOf(0f to 0.26f, 0.06f to 0.3f, 0.1f to 0.15f, -0.08f to 0.35f)) {
            val anchor = assertNotNull(face.at(x, y))
            val point = patch.positionAt(anchor.u, anchor.v)
            assertEquals(y, point.y, 0.005f, "($x, $y) up")
            assertEquals(x, -point.z, 0.005f, "($x, $y) across")
            assertTrue(point.x > 0f, "($x, $y) lands on the face, not the back of the head")
        }
        val left = assertNotNull(face.at(0.1f, 0.3f))
        val right = assertNotNull(face.at(0.1f, 0.3f, left = false))
        assertEquals(left.u, right.u, 1e-3f)
        assertEquals(1f, left.v + right.v, 1e-3f)
        assertTrue(left.v > 0.5f, "her left lies round toward v = 0.75")
    }
}
