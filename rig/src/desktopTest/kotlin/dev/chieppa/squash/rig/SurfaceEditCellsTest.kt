package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.GarmentBuilder
import dev.chieppa.squash.character.surface.GarmentCover
import dev.chieppa.squash.character.surface.HiddenSkin
import dev.chieppa.squash.character.surface.PaintMemo
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.rig.StackFixture.assertSameGarment
import dev.chieppa.squash.rig.StackFixture.assertSameLayer
import dev.chieppa.squash.rig.StackFixture.assertSameSurface
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/**
 * Cells the stack is built at, as shares of the root's least half-extent: the body's own, those either side of it, a coarse one and an
 * odd one, so that which grid points fall inside the field changes from one to the next.
 */
private val CELL_SHARES = listOf(0.0487f, 0.05f, 0.0731f, 0.1f)

/**
 * A one surface with everything on it (see [StackFixture]), built by kept builders at several cells: an edit built again where it
 * reaches gives the same surface, paint, garments and hidden skin as builders that have built nothing before.
 */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceEditCellsTest {
    @Test
    fun anEditBuiltWhereItReachesMatchesAFreshBuildAtEveryCell() = runTest {
        val session = stackSession()
        val rest = assertNotNull(session.placed.output.value)
        session.send(SetParameter(StackFixture.leg.id, "size", EDITED_SIZE))
        runCurrent()
        val edited = assertNotNull(session.placed.output.value)
        val character = session.document.value.character
        val shapes = character.parts.mapNotNull { part -> session.heldShapes(part.id).output.value?.let { part.id to it } }.toMap()
        val covers = surfaceCovers(character)
        for (share in CELL_SHARES) {
            val kept = Stack(share)
            kept.build(rest, shapes, covers)
            val again = kept.build(edited, shapes, covers)
            assertTrue(assertNotNull(again.surface.mesh.change).kept > 0, "at $share the edit keeps vertices the leg does not reach")
            val fresh = Stack(share).build(edited, shapes, covers)
            assertSameSurface(fresh.surface, again.surface)
            assertEquals(fresh.paint.layers.size, again.paint.layers.size, "paint layers at $share")
            fresh.paint.layers.zip(again.paint.layers).forEach { (a, b) -> assertSameLayer(a, b) }
            assertEquals(fresh.hiding.worn.size, again.hiding.worn.size, "garments at $share")
            fresh.hiding.worn.zip(again.hiding.worn).forEach { (a, b) -> assertSameGarment(a, b) }
            assertContentEquals(assertNotNull(fresh.hiding.skin).triangles, assertNotNull(again.hiding.skin).triangles, "hidden skin at $share")
        }
    }

    /** The builders a session keeps, at [share]. */
    private class Stack(share: Float) {
        private val surfaces = SurfaceBuilder(share)
        private val garments = GarmentBuilder()
        private val memo = PaintMemo()

        suspend fun build(placed: AssembledCharacter, shapes: Map<PartId, HeldShapes>, covers: List<GarmentCover>): Built {
            val surface = assertNotNull(surfaces.build(placed))
            val dress = garments.start(surface.field, surface.mesh, placed, covers)
            while (true) (dress.next() ?: break).forEach { it() }
            return Built(surface, SurfacePaint.of(surface, shapes, memo = memo), HiddenSkin(surface, dress.garments).run())
        }
    }

    private class Built(val surface: CharacterSurface, val paint: SurfacePaint, val hiding: HiddenSkin)
}
