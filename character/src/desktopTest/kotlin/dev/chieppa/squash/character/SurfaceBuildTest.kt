package dev.chieppa.squash.character

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.COARSE_FACTOR
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.golden.GoldenFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Points sampled along the line from the torso's origin to the leg's. */
private const val SAMPLES = 16

/** Fixture values are written as whole numbers of this step. */
private const val JOIN_FIXTURE_STEP = 1e-4f

/** A torso and a front leg built as one surface: the field and the coordinates its vertices hold, and the coarse cell while dragging. */
@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceBuildTest {
    private suspend fun TestScope.sessionSurface() = surfaceSession(torsoPart, leftLeg).also {
        it.settle()
    }

    private fun dev.chieppa.squash.character.session.CharacterSession.surface(): CharacterSurface = assertNotNull(characterSurface.output.value?.surface)

    @Test
    fun theFieldAndTheCoordinatesAtTheJoinMatchTheirFixture() = runTest {
        val session = sessionSurface()
        val surface = session.surface()
        val placed = assertNotNull(session.placed.output.value)
        val from = assertNotNull(placed.part(torsoPart.id)).origin
        val to = assertNotNull(placed.part(leftLeg.id)).origin
        val text = buildString {
            for (i in 0..SAMPLES) {
                val p = from + (to - from) * (i.toFloat() / SAMPLES)
                val v = nearestVertex(surface, p)
                val mesh = surface.mesh
                append("${step(surface.field.field.value(p))} vertex ${mesh.winners[v]}/${mesh.parents[v]} share ${step(mesh.shares[v])}")
                for (k in 0 until HELD) {
                    val member = mesh.held[v * HELD + k]
                    if (member >= 0) append(" | $member ${step(mesh.heldShares[v * HELD + k])} ${step(mesh.heldU[v * HELD + k])} ${step(mesh.heldV[v * HELD + k])}")
                }
                append('\n')
            }
        }
        GoldenFixture.check(Path.of("src/desktopTest/golden/one-surface-join.txt"), text, "character")
    }

    @Test
    fun theSurfaceIsClosedAndInkedAtItsJoin() = runTest {
        val surface = sessionSurface().surface()
        val uses = HashMap<Long, Int>()
        val t = surface.mesh.triangles
        for (tri in 0 until t.size / 3) for (c in 0 until 3) {
            val a = t[tri * 3 + c]
            val b = t[tri * 3 + (c + 1) % 3]
            val key = minOf(a, b).toLong() shl 32 or maxOf(a, b).toLong()
            uses[key] = (uses[key] ?: 0) + 1
        }
        assertTrue(uses.values.all { it == 2 }, "every edge lies on two triangles")
        assertEquals(setOf(torsoPart.id, leftLeg.id), surface.parts)
        assertTrue(surface.joins.isNotEmpty(), "the leg's join is inked")
    }

    @Test
    fun anEditMeshedAgainWhereItReachesMatchesTheWholeSurfaceBuiltAfresh() = runTest {
        val session = sessionSurface()
        session.send(SetParameter(leftLeg.id, "size", 1.02f))
        session.settle()
        val edited = session.surface().mesh
        val fresh = assertNotNull(SurfaceBuilder().build(assertNotNull(session.placed.output.value))).mesh
        assertEquals(fresh.vertexCount, edited.vertexCount)
        assertContentEquals(fresh.triangles, edited.triangles)
        for (i in fresh.positions.indices) assertEquals(fresh.positions[i], edited.positions[i], 1e-5f)
    }

    @Test
    fun theCellFollowsTheRootAloneSoAPartOnASocketChangesNoCell() = runTest {
        val session = sessionSurface()
        val before = session.surface().mesh.cell
        session.send(SetParameter(leftLeg.id, "size", 1.5f))
        session.settle()
        assertEquals(before, assertNotNull(SurfaceBuilder().build(assertNotNull(session.placed.output.value))).mesh.cell)
        session.send(SetParameter(torsoPart.id, "size", 1.5f))
        session.settle()
        assertTrue(assertNotNull(SurfaceBuilder().build(assertNotNull(session.placed.output.value))).mesh.cell > before)
    }

    @Test
    fun aDragMeshesAtTheCoarseCellAndItsCommitMeshesAtTheFullCell() = runTest {
        val session = sessionSurface()
        val cell = session.surface().mesh.cell
        val gesture = session.history.begin()
        gesture.update(SetParameter(leftLeg.id, "size", 1.02f))
        session.settle()
        assertEquals(cell * COARSE_FACTOR, session.surface().mesh.cell, 1e-6f)
        gesture.commit()
        session.settle()
        assertEquals(cell, session.surface().mesh.cell, 1e-6f)
    }

    private fun nearestVertex(surface: CharacterSurface, p: Vec3): Int =
        (0 until surface.mesh.vertexCount).minBy { (surface.mesh.position(it) - p).length }

    private fun step(value: Float): Int = (value / JOIN_FIXTURE_STEP).roundToInt()
}
