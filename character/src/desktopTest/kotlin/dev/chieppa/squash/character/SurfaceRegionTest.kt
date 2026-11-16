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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A region drawn up to a join on a one surface paints its own part up to the curve where the join shares the surface equally, and no further. */
@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceRegionTest {
    private val sock = SlotName("sock")

    @Test
    fun aRegionAtTheRootOfALegEndsOnTheJoinsEqualShareCurve() = runTest {
        val border = List(12) { Anchor(leftLeg.id, BODY_PATCH, 0f, it / 12f) } + List(12) { Anchor(leftLeg.id, BODY_PATCH, 0.3f, 1f - it / 12f) }
        val session = surfaceSession(torsoPart, leftLeg).also {
            it.send(SetPaletteSlot(sock, SlotColor(0.9f, 0.9f, 0.9f)))
            it.send(SetRegion(leftLeg.id, RegionId("sock"), Region(RegionId("sock"), border, sock)))
            it.settle()
        }
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        val layer = assertNotNull(session.surfacePaint.output.value?.paint).layers.single { it.slot == sock }
        val section = layer.cut.values(SurfaceSection(surface, leftLeg.id).values)
        val cell = surface.mesh.cell
        assertTrue(section.all { it >= -cell * 0.05f }, "the sock lies on the leg's own section, at least ${section.min()}")
        assertTrue(section.any { it < cell * 0.05f }, "and reaches the join's curve")
    }
}
