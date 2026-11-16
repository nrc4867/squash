package dev.chieppa.squash.rig

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.Paint
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetBranchSurface
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A garment's own regions and brush strokes are painted on it, over its color, where it is worn. */
@OptIn(ExperimentalCoroutinesApi::class)
class GarmentPaintTest {
    private val stripe = SlotName("stripe")
    private val dab = SlotName("dab")

    private fun torso(u: Float, v: Float) = Anchor(StackFixture.torso.id, BODY_PATCH, u, v)

    @Test
    fun aBranchsRegionAndStrokeArePaintedOnTheGarmentAndGoneWithThem() = runTest {
        val session = stackSession()
        val sweater = GarmentId("sweater")
        val plain = assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.single()
        assertNull(plain.paint, "a garment with no shapes of its own holds no paint")
        val band = Region(RegionId("band"), listOf(torso(0.3f, -0.2f), torso(0.4f, -0.2f), torso(0.4f, 0.2f), torso(0.3f, 0.2f)), stripe)
        val stroke = BrushStroke(BrushStrokeId("dab"), listOf(torso(0.5f, 0f), torso(0.55f, 0.05f)), dab, radius = 0.05f)
        session.send(SetBranchSurface(sweater, StackFixture.torso.id, SurfaceWork(regions = listOf(band), paint = Paint(listOf(stroke)))))
        runCurrent()
        check(session.graph.settled.value)
        val garment = assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.single()
        val layers = assertNotNull(garment.paint).layers
        assertEquals(listOf(stripe, dab), layers.map { it.slot })
        assertTrue(layers.all { it.mesh.vertexCount > 0 }, "each shape lands on the garment")
        val body = assertNotNull(session.sessionSurface.paint.output.value?.paint).layers
        assertTrue(body.none { it.slot == stripe || it.slot == dab }, "the body under it is not painted")
        session.history.undo()
        runCurrent()
        assertNull(assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.single().paint)
    }
}
