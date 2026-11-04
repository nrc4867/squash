package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartColor
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.SlottedCut
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.Shading
import dev.chieppa.stretch.gpu.Mat4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceDrawsTest {
    private val mesh = RenderMesh(FloatArray(0), IntArray(0))
    private val fallback = PartMaterial(Color.Gray, ink = Color.Black)
    private val fur = SlotName("fur")
    private val spot = SlotName("spot")
    private val palette = Palette(listOf(PaletteSlot(fur, SlotColor(1f, 0.5f, 0f))))
    private fun layer(slot: SlotName) = SlottedCut(slot, PatchCut("body", RenderMesh(FloatArray(0), IntArray(0)), FloatArray(0), FloatArray(0)))

    private fun draw(color: PartColor?, from: Palette = palette, shading: ObjectShading = ObjectShading(), side: BodySide? = null, toCamera: Vec3 = Vec3.Z) =
        coloredPartDraw(mesh, color, from, fallback, shading, side, toCamera, Mat4.Identity, 0f)

    @Test
    fun theBaseShowsItsSlotAndAPaletteChangeOnlyChangesTheColor() {
        assertEquals(Color(1f, 0.5f, 0f), draw(PartColor(fur, emptyList())).material.base)
        val recolored = Palette(listOf(PaletteSlot(fur, SlotColor(0f, 0f, 1f))))
        assertEquals(Color(0f, 0f, 1f), draw(PartColor(fur, emptyList()), recolored).material.base)
    }

    @Test
    fun anEmptyPaletteOrMissingSlotFallsBackToTheLooksOwnColorAndDrawsNoLayer() {
        assertEquals(Color.Gray, draw(null).material.base)
        assertEquals(Color.Gray, draw(PartColor(spot, emptyList())).material.base)
        assertEquals(Color.Gray, draw(PartColor(fur, listOf(layer(fur))), Palette()).material.base)
        assertTrue(draw(PartColor(fur, listOf(layer(fur))), Palette()).layers.isEmpty())
        assertEquals(1, draw(PartColor(null, listOf(layer(fur), layer(spot)))).layers.size, "only the slot it holds")
    }

    @Test
    fun aFarSideAndTwoToneReachBothTheBaseAndItsLayers() {
        val near = draw(PartColor(fur, listOf(layer(fur))), shading = ObjectShading(Shading.TWO_TONE), side = BodySide.POSITIVE_Z)
        val far = draw(PartColor(fur, listOf(layer(fur))), shading = ObjectShading(Shading.TWO_TONE), side = BodySide.NEGATIVE_Z)
        assertEquals(Shading.TWO_TONE, near.material.shading)
        assertTrue(far.material.base.red < near.material.base.red)
        assertTrue(far.layers.single().tones.lit.red < near.layers.single().tones.lit.red)
        assertTrue(near.layers.single().tones.shadow.red < near.layers.single().tones.lit.red)
    }

    @Test
    fun aMarkWithNoStopsTapersAndAMarkWithStopsKeepsThem() {
        val plain = markProfile(MarkWidth())
        assertEquals(0f, plain.at(0f))
        assertEquals(1f, plain.at(0.5f))
        val own = markProfile(MarkWidth(listOf(WidthStop(0f, 0.5f), WidthStop(1f, 2f))))
        assertEquals(0.5f, own.at(0f))
        assertEquals(2f, own.at(1f))
    }
}
