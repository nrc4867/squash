package dev.chieppa.squash.character

import dev.chieppa.squash.character.surface.SurfaceSection
import dev.chieppa.squash.document.Anchor
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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

/**
 * A paint layer's candidate triangles are triangles of the drawn mesh it was cut on. A posed surface that draws fewer triangles, as one
 * does where parts touch, keeps the layer's own numbering when its section keeps the layer to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PosedPaintLayerTest {
    private val sock = SlotName("sock")

    @Test
    fun aSectionOfASurfaceDrawingFewerTrianglesKeepsALayerCutOnTheRestSurface() = runTest {
        val border = List(12) { Anchor(leftLeg.id, BODY_PATCH, 0f, it / 12f) } + List(12) { Anchor(leftLeg.id, BODY_PATCH, 0.3f, 1f - it / 12f) }
        val session = surfaceSession(torsoPart, leftLeg).also {
            it.send(SetPaletteSlot(sock, SlotColor(0.9f, 0.9f, 0.9f)))
            it.send(SetRegion(leftLeg.id, RegionId("sock"), Region(RegionId("sock"), border, sock)))
            it.settle()
        }
        val rest = assertNotNull(session.characterSurface.output.value?.surface)
        val layer = assertNotNull(session.surfacePaint.output.value?.paint).layers.single { it.slot == sock }
        val last = layer.candidates.max()
        val kept = rest.render.indices.copyOfRange(QUAD_TRIANGLES * 3, last / QUAD_TRIANGLES * QUAD_TRIANGLES * 3)
        val posed = rest.movedTo(RenderMesh(rest.render.vertices, kept), rest.positions, rest.lineNormals)

        val expected = assertNotNull(SurfaceSection(rest, leftLeg.id).layer(layer))
        val shown = assertNotNull(SurfaceSection(posed, leftLeg.id).layer(layer, posed.render))
        assertContentEquals(expected.cut.triangles, shown.cut.triangles)
        assertContentEquals(expected.cut.from, shown.cut.from)
    }

    private companion object {
        const val QUAD_TRIANGLES = 4
    }
}
