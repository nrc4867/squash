package dev.chieppa.squash.character

import dev.chieppa.squash.character.surface.RunScales
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.RenderMesh
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How wide the stripe is, in the surface's cells. */
private const val STRIPE_CELLS = 0.4f

/** Where along the leg the stripe runs, in u. */
private const val STRIPE_U = 0.45f

/** A band painted round a leg narrower than one of the surface's cells is laid whole: as wide as drawn and all the way round. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThinStripeTest {
    private val stripe = SlotName("stripe")

    @Test
    fun aStripeNarrowerThanACellIsPaintedAsDrawn() = runTest {
        val session = surfaceSession(torsoPart, leftLeg).also { it.settle() }
        val before = assertNotNull(session.characterSurface.output.value?.surface)
        val run = before.field.runs.first { it.partId == leftLeg.id && it.patch.name == BODY_PATCH }
        val scales = RunScales(run.patch)
        val cell = before.mesh.cell
        val width = cell * STRIPE_CELLS
        val du = width / scales.along(STRIPE_U)
        val border = List(12) { on(leftLeg, STRIPE_U, it / 12f) } + List(12) { on(leftLeg, STRIPE_U + du, 1f - it / 12f) }
        session.send(SetPaletteSlot(stripe, SlotColor(0.1f, 0.1f, 0.1f)))
        session.send(SetRegion(leftLeg.id, RegionId("stripe"), Region(RegionId("stripe"), border, stripe)))
        session.settle()
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        val layer = assertNotNull(session.surfacePaint.output.value?.paint?.layers?.singleOrNull { it.slot == stripe }, "the stripe is painted")
        val laid = layer.cut.mesh(surface.render)
        var area = 0.0
        val p = laid.vertices
        val w = RenderMesh.FLOATS_PER_VERTEX
        for (tri in 0 until laid.indices.size / 3) {
            val (a, b, c) = Triple(laid.indices[tri * 3] * w, laid.indices[tri * 3 + 1] * w, laid.indices[tri * 3 + 2] * w)
            val ux = p[b] - p[a]
            val uy = p[b + 1] - p[a + 1]
            val uz = p[b + 2] - p[a + 2]
            val vx = p[c] - p[a]
            val vy = p[c + 1] - p[a + 1]
            val vz = p[c + 2] - p[a + 2]
            val cx = uy * vz - uz * vy
            val cy = uz * vx - ux * vz
            val cz = ux * vy - uy * vx
            area += sqrt((cx * cx + cy * cy + cz * cz).toDouble()) / 2.0
        }
        val drawn = width * scales.around(STRIPE_U + du / 2f)
        assertTrue(area > drawn * 0.7f && area < drawn * 1.3f, "the stripe covers $area, drawn as $drawn")
    }
}
