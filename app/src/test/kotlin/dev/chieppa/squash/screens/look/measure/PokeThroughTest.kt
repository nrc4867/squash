package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.CloseUps
import dev.chieppa.squash.screens.smoke.shownState
import dev.chieppa.squash.screens.woman.womanDressedDocument
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("integration")
class PokeThroughTest {
    private val work = TestWork()
    private val session = CharacterSession(womanDressedDocument(), CloseUps.shippedLibrary(), work.scope, work.dispatcher).also { SessionPose(it) }

    private fun pokes(outfit: String): List<PokeThrough> {
        session.send(ShowOutfit(OutfitId(outfit)))
        work.settle(session)
        val state = session.shownState()
        return pokeThrough(assertNotNull(state.dress), state.surface)
    }

    @Test
    fun `a band sunk into the neck shows the neck through it, and clothes that stand clear show nothing`() {
        assertEquals(emptyList(), pokes("beach"))
        val choker = pokes("goth").single { it.outer == "choker" }
        assertEquals("body", choker.inner)
        assertTrue(choker.vertices > 0 && choker.deepest > 0f)
        assertEquals(setOf("neck"), choker.parts.keys)
    }
}
