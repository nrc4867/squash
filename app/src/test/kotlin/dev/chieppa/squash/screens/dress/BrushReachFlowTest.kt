package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.dress.chartPlaces
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.woman.ARM_L
import dev.chieppa.squash.screens.woman.TORSO
import dev.chieppa.squash.screens.woman.savedWoman
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** A Paint stroke keeps off the parts above the one it starts on till it clearly goes onto them. */
@Tag("integration")
class BrushReachFlowTest {
    private val work = TestWork()

    @AfterTest
    fun close() = work.close()

    private val AppState.open: CharacterSession get() = assertNotNull(session.value)
    private val AppState.character: Character get() = open.document.value.character
    private val AppState.body: AssembledCharacter get() = assertNotNull(open.assembly.output.value)

    @Test
    fun aBrushStartedOnASleeveKeepsOffTheTorsoTillItClearlyGoesOntoIt() {
        val state = AppState(TemplateFolder.shipped(), work)
        work.settle(state.open(savedWoman()))
        val places = chartPlaces(state.character, state.body)
        val sleeve = brushPlaces(state.character, state.body, ARM_L, 0f, 0.3f)
        val size = halfDepth(state.character, state.body, ARM_L, 0.1f) * ROUND_BRUSH * 2f
        assertTrue(places.within(sleeve, size).count(TORSO) > 0, "a round brush at the shoulder reaches the torso round the socket")
        val reach = BrushReach(places, ARM_L)
        val laid = reach.stroke(sleeve.map { it to ARM_L }, size)
        assertEquals(0, laid.count(TORSO), "the stroke keeps to the arm")
        assertTrue(laid.count(ARM_L) > 0)
        val chest = brushPlaces(state.character, state.body, TORSO, 0.5f, 0.6f, listOf(0f))
        val onward = reach.stroke(chest.map { it to TORSO }, size)
        assertEquals(setOf<PartId>(TORSO), reach.crossed, "dabs on the torso clear of the arm take it in")
        assertTrue(onward.count(TORSO) > 0)
    }
}
