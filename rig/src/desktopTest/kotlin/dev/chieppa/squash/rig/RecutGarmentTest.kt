package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.GarmentBuilder
import dev.chieppa.squash.character.surface.HiddenSkin
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.rig.StackFixture.assertSameGarment
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A sweater down the whole leg, whose end the paw comes out through: an edit to the paw alone leaves the sweater's surface as it was and
 * cuts it again round the paw, as the sweater built afresh is cut.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecutGarmentTest {
    @Test
    fun aGarmentCutAgainRoundAnEditedPartMatchesTheGarmentBuiltAfresh() = runTest {
        val session = stackSession(legTo = 1f, withPaw = true)
        val before = assertNotNull(session.oneSurface.dress.output.value?.dress).garments.first { StackFixture.leg.id in it.cover.branches }
        assertTrue(StackFixture.paw.id in before.cover.outThroughEnds, "the paw comes out through the leg's end")
        session.send(SetParameter(StackFixture.paw.id, "size", EDITED_SIZE))
        runCurrent()
        check(session.graph.settled.value)
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        val placed = assertNotNull(session.placed.output.value)
        val garments = GarmentBuilder().start(surface.field, surface.mesh, placed, surfaceCovers(session.document.value.character))
        while (true) (garments.next() ?: break).forEach { it() }
        val fresh = HiddenSkin(surface, garments.garments).run()
        val dress = assertNotNull(session.oneSurface.dress.output.value?.dress)
        assertEquals(fresh.worn.size, dress.garments.size)
        fresh.worn.zip(dress.garments).forEach { (a, b) -> assertSameGarment(a, b) }
        assertContentEquals(assertNotNull(fresh.skin).triangles, assertNotNull(dress.skin).triangles)
    }
}
