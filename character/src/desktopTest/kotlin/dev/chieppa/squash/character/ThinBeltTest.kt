package dev.chieppa.squash.character

import dev.chieppa.squash.character.surface.RunScales
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.geom.RenderMesh
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** How wide the belt is, in the body's cells. */
private const val BELT_CELLS = 1f

/** Where along the torso the belt starts, in u. */
private const val BELT_U = 0.45f

/** A belt about one of the body's cells wide is meshed finer than the body, so it is worn whole: as wide as drawn and all the way round. */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class ThinBeltTest {
    @Test
    fun aBeltAboutACellWideIsWornAsDrawn() = runTest {
        val session = surfaceSession(torsoPart, leftLeg).also { it.settle() }
        val body = assertNotNull(session.characterSurface.output.value?.surface)
        val run = body.field.runs.first { it.partId == torsoPart.id && it.patch.name == BODY_PATCH }
        val scales = RunScales(run.patch)
        val width = body.mesh.cell * BELT_CELLS
        val du = width / scales.along(BELT_U)
        val slot = GarmentSlot(GarmentSlotId("waist"), "Waist")
        val belt = Garment(GarmentId("belt"), "Belt", slot.id, branches = listOf(GarmentBranch(torsoPart.id, BELT_U, BELT_U + du)))
        val outfit = OutfitId("belted")
        session.send(SetWardrobe(Wardrobe(listOf(belt), listOf(slot), listOf(Outfit(outfit, "Belted", mapOf(slot.id to belt.id))), outfit)))
        session.settle()
        val worn = assertNotNull(session.sessionSurface.dress.output.value?.dress?.garments?.singleOrNull(), "the belt is worn")
        assertTrue(worn.surface.mesh.cell < body.mesh.cell, "the belt meshes finer than the body")
        val drawn = width * scales.around(BELT_U + du / 2f)
        val area = areaOf(worn.drawn)
        assertTrue(area > drawn * 0.7f && area < drawn * 1.6f, "the belt covers $area, drawn as $drawn")
    }

    private fun areaOf(mesh: RenderMesh): Double {
        val p = mesh.vertices
        val w = RenderMesh.FLOATS_PER_VERTEX
        var area = 0.0
        for (tri in 0 until mesh.indices.size / 3) {
            val a = mesh.indices[tri * 3] * w
            val b = mesh.indices[tri * 3 + 1] * w
            val c = mesh.indices[tri * 3 + 2] * w
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
        return area
    }
}
