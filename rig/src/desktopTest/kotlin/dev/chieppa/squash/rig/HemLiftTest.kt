package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.SetGarmentFit
import dev.chieppa.squash.document.SetGarmentHemRoll
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The least stand-off a hem keeps off the skin, in the body's cells, as the build lifts it. */
private const val LEAST_LIFT_CELLS = 0.15f

/** How far inside the cloth, in the body's cells, a vertex lies past every lift. */
private const val DEEP_CELLS = 4f

/** A hem roll, in body units. */
private const val ROLL = 0.01f

/**
 * A sweater at fit 0 stands off the skin along its hems by the least lift and lies on the skin deep inside; a hem roll lifts its hems
 * by the roll; the surface as grown is kept beside the lifted one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HemLiftTest {
    private val sweater = GarmentId("sweater")

    @Test
    fun aGarmentAtFitZeroStandsOffItsHemsAndARollLiftsThemFurther() = runTest {
        val session = stackSession()
        val cell = assertNotNull(session.characterSurface.output.value?.surface).mesh.cell
        session.send(SetGarmentFit(sweater, 0f))
        val tight = garment(session)
        val lifts = liftsOf(tight)
        var nearHem = 0
        for (v in lifts.indices) {
            val inside = tight.coverage[v]
            if (inside in 0f..cell * 0.5f) {
                nearHem++
                assertEquals(cell * LEAST_LIFT_CELLS, lifts[v], cell * 0.01f, "vertex $v by the hem")
            }
            if (inside > cell * DEEP_CELLS) assertEquals(0f, lifts[v], 1e-6f, "vertex $v deep inside")
        }
        assertTrue(nearHem > 0)

        session.send(SetGarmentHemRoll(sweater, ROLL))
        val rolled = garment(session)
        val most = liftsOf(rolled).withIndex().filter { rolled.coverage[it.index] in 0f..cell * 0.5f }.minOf { it.value }
        assertTrue(most >= ROLL, "a hem rolls out by at least the roll, here $most")
    }

    private fun TestScope.garment(session: CharacterSession): SurfaceGarment {
        runCurrent()
        return assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.first()
    }

    /** How far each vertex of [garment]'s surface lies from where it was grown. */
    private fun liftsOf(garment: SurfaceGarment): FloatArray {
        val lifted = garment.surface.positions
        val grown = garment.grown.positions
        return FloatArray(lifted.size / 3) { v ->
            val dx = lifted[v * 3] - grown[v * 3]
            val dy = lifted[v * 3 + 1] - grown[v * 3 + 1]
            val dz = lifted[v * 3 + 2] - grown[v * 3 + 2]
            sqrt(dx * dx + dy * dy + dz * dz)
        }
    }
}
