package dev.chieppa.squash.rig

import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.SetGarmentFit
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A sleeve down a whole limb at fit 0 whose end a downward-pointing part comes out through: the garment's field holds that part's run
 * beside the limb's, grown over by the join as the body blends into it, so the hem follows the blend and no skin stands out through it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnkleHemJoinTest {
    @Test
    fun aPartComingOutOfADownwardEndIsGrownOverAtFitZero() = runTest {
        val session = stackSession(legTo = 1f, withPaw = true)
        session.send(SetGarmentFit(GarmentId("sweater"), 0f))
        runCurrent()
        val garment = assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.single()
        assertTrue(StackFixture.paw.id in garment.cover.outThroughEnds, "the paw comes out through the leg's end")
        assertTrue(garment.surface.field.runs.any { it.partId == StackFixture.paw.id }, "the garment grows over the paw's first stretch")
    }
}
