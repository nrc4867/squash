package dev.chieppa.squash.character

import dev.chieppa.squash.character.surface.RunScales
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.boxCells
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.geom.RenderMesh
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** How wide the belt is, in the body's cells. */
private const val BELT_CELLS = 1f

/** Where along the torso the belt starts, in u. */
private const val BELT_U = 0.45f

/**
 * A belt about one of the body's cells wide is meshed finer than the body, so it is worn whole: as wide as the chart's rows its box
 * paints, at least two, and all the way round; cloth the same garment lays over another part meshes at the body's cell.
 */
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
        assertTrue(worn.surface.field.cellOf(memberOf(worn, torsoPart.id)) < body.mesh.cell, "the belt meshes finer than the body")
        val rows = boxCells(belt.branches.single()).count { it } / CHART_COLUMNS
        val drawn = rows.toFloat() / CHART_ROWS * scales.along(BELT_U) * scales.around(BELT_U + du / 2f)
        val area = areaOf(worn.drawn)
        assertTrue(area > drawn * 0.7f && area < drawn * 1.6f, "the belt covers $area, drawn as $drawn")

        val legged = belt.copy(branches = belt.branches + GarmentBranch(leftLeg.id, 0f, 1f))
        session.send(SetWardrobe(Wardrobe(listOf(legged), listOf(slot), listOf(Outfit(outfit, "Belted", mapOf(slot.id to legged.id))), outfit)))
        session.settle()
        val both = assertNotNull(session.sessionSurface.dress.output.value?.dress?.garments?.singleOrNull(), "the belt and the leg's cloth are worn")
        assertTrue(both.surface.field.cellOf(memberOf(both, torsoPart.id)) < body.mesh.cell, "the belt still meshes finer than the body")
        assertEquals(body.mesh.cell, both.surface.field.cellOf(memberOf(both, leftLeg.id)), "the leg's cloth meshes at the body's cell")
        assertEquals(body.mesh.cell, both.surface.field.cell, "the garment meshes from the cell of its coarsest cloth, the leg's")
        assertEquals(both.surface.field.cellOf(memberOf(both, torsoPart.id)), both.surface.mesh.cell, "its mesh reads as meshed at the belt's cell")
    }

    /** The member of [worn]'s field over [part]'s first run. */
    private fun memberOf(worn: SurfaceGarment, part: PartId): Int = worn.surface.field.runs.indexOfFirst { it.partId == part && it.first }

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
