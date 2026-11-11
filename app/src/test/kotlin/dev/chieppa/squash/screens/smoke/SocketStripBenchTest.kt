package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.withStripsPosed
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.woman.womanDressedDocument
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/**
 * What building the woman's strips again in her display pose costs, a socket at a time, in every outfit: every strip of the body and
 * of the garments over it, from the posed parts, as a frame of a pose drag builds those it moved. The median goes to the test's output.
 */
@Tag("slow")
class SocketStripBenchTest {
    private val work = TestWork()

    @Test
    fun theWomansStripsBuildUnderFiveMillisecondsASocket() {
        val document = womanDressedDocument()
        val session = CharacterSession(document, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        SessionPose(session)
        work.settle(session)
        for (outfit in document.character.wardrobe.outfits.map { it.id }) {
            session.send(ShowOutfit(outfit))
            work.settle(session)
            val shown = assertNotNull(session.posedBody.output.value)
            val strips = shown.parts.sumOf { it.strips.size }
            assertTrue(strips > 0, "the woman has strips")
            val times = (0 until WARM + RUNS).map {
                val started = System.nanoTime()
                shown.withStripsPosed(NOTHING)
                (System.nanoTime() - started) / 1e6 / strips
            }.drop(WARM).sorted()
            val median = times[times.size / 2]
            println("${outfit.value}: $strips strips, median ${"%.2f".format(median)} ms a socket, fastest ${"%.2f".format(times.first())} ms")
            assertTrue(median < BUDGET_MS, "$median ms a socket")
        }
    }

    private companion object {
        /** A character with no parts, against which every strip has moved and is built again. */
        val NOTHING = AssembledCharacter(emptyList(), emptyList())

        const val WARM = 10
        const val RUNS = 30
        const val BUDGET_MS = 5.0
    }
}
