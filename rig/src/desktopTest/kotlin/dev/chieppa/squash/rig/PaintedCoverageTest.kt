package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.runsOfCells
import dev.chieppa.squash.character.surface.GarmentBuilder
import dev.chieppa.squash.character.surface.HiddenSkin
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.StackFixture.assertSameGarment
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** How many rings in from the leg's end its way out is read from. */
private const val AXIS_RINGS = 3

/**
 * A sweater painted over the torso and the whole leg, whose end a paw comes out through: its coverage reads the painted cells, the end
 * still cuts the cloth painted over it, and painting less of the leg grows it over its painted rows' step only, keeping the grown
 * surface while the step holds and cutting it again as a fresh build does.
 */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class PaintedCoverageTest {
    private fun rows(range: IntRange): List<Int> = runsOfCells(BooleanArray(CHART_CELLS) { it / CHART_COLUMNS in range })

    /** Paints the sweater: the torso's rows 6 to 37, its branch's range, and the leg's [legRows]. */
    private fun TestScope.paint(session: CharacterSession, legRows: IntRange, legTo: Float) {
        val wardrobe = session.document.value.character.wardrobe
        val sweater = wardrobe.garments.single()
        val area = GarmentArea(GarmentAreaId("body"), "Body", mapOf(StackFixture.torso.id to rows(6..37), StackFixture.leg.id to rows(legRows)))
        val branches = listOf(GarmentBranch(StackFixture.torso.id, 0.1f, 0.6f), GarmentBranch(StackFixture.leg.id, 0f, legTo))
        session.send(SetWardrobe(wardrobe.copy(garments = listOf(sweater.copy(branches = branches, areas = listOf(area))))))
        runCurrent()
        check(session.graph.settled.value)
    }

    private fun CharacterSession.sweater(): SurfaceGarment = assertNotNull(sessionSurface.dress.output.value?.dress).garments.single()

    /** The garments and skin built afresh from [session]'s settled state, matched against what the session holds. */
    private fun assertSameAsFresh(session: CharacterSession) {
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        val placed = assertNotNull(session.placed.output.value)
        val garments = GarmentBuilder().start(surface.field, surface.mesh, placed, surfaceCovers(session.document.value.character))
        while (true) (garments.next() ?: break).forEach { it() }
        val fresh = HiddenSkin(surface, garments.garments).run()
        val dress = assertNotNull(session.sessionSurface.dress.output.value?.dress)
        assertEquals(fresh.worn.size, dress.garments.size)
        fresh.worn.zip(dress.garments).forEach { (a, b) -> assertSameGarment(a, b) }
        assertContentEquals(assertNotNull(fresh.skin).triangles, assertNotNull(dress.skin).triangles)
    }

    @Test
    fun anEndAPartComesOutThroughCutsTheClothPaintedOverIt() = runTest {
        val session = stackSession(legTo = 1f, withPaw = true)
        paint(session, 0..63, 1f)
        val garment = session.sweater()
        assertNotNull(garment.cover.clothOf(StackFixture.leg.id), "the leg reads its painted cells")
        assertEquals(true, garment.cover.openEnds[StackFixture.leg.id]?.high, "the paw comes out through the leg's end")
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        val run = surface.field.runs.indexOfFirst { it.partId == StackFixture.leg.id && it.first }
        val patch = surface.field.runs[run].patch
        fun middle(ring: Int) = (0 until patch.nv).fold(Vec3.ZERO) { sum, c -> sum + patch.point(patch.index(ring, c)) } * (1f / patch.nv)
        val end = middle(patch.nu - 1)
        val out = (end - middle(patch.nu - 1 - AXIS_RINGS)).normalized()
        val member = surface.field.field.members[run]
        val mesh = garment.surface.mesh
        var covered = 0
        for (v in 0 until mesh.vertexCount) {
            if (garment.coverage[v] < 0f) continue
            covered++
            val past = (member.local(mesh.position(v)) - end) dot out
            assertTrue(past <= 0f, "covered vertex $v lies $past past the leg's end ring, though the cells there are painted")
        }
        assertTrue(covered > 0)
        session.send(SetParameter(StackFixture.paw.id, "size", EDITED_SIZE))
        runCurrent()
        check(session.graph.settled.value)
        assertSameAsFresh(session)
    }

    @Test
    fun paintingLessOfAPartWithinAStepKeepsTheGrownSurfaceAndCutsItAsAFreshBuildDoes() = runTest {
        val session = stackSession(legTo = 1f)
        paint(session, 0..63, 1f)
        val whole = session.sweater()
        paint(session, 0..24, 25f / 64f)
        val shorter = session.sweater()
        assertNotSame(whole.surface, shorter.surface, "the leg grows over its painted rows' steps, so painting less of it past a step grows it again")
        paint(session, 0..21, 22f / 64f)
        val within = session.sweater()
        assertSame(shorter.surface, within.surface, "rows 0 to 21 and 0 to 24 grow over the same step, so the second only cuts it")
        assertNotEquals(shorter.coverage.count { it >= 0f }, within.coverage.count { it >= 0f })
        assertSameAsFresh(session)
        session.history.undo()
        runCurrent()
        check(session.graph.settled.value)
        assertSameAsFresh(session)
        session.history.undo()
        runCurrent()
        check(session.graph.settled.value)
        assertSameAsFresh(session)
    }
}
