package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.withSocketFillets
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.person.EMPTY_PERSON
import dev.chieppa.squash.screens.person.personCommands
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Tag

private const val ROUNDS = 5

/**
 * What the implicit blend adds to a character's build, with the test-only blended hand, paw and leg against the shipped ones: the
 * whole session settled from its commands, and the blend steps alone. Numbers go to the test's output.
 */
@Tag("slow")
class BlendBenchTest {
    private val work = TestWork()

    private fun session(empty: SquashDocument, commands: List<DocumentCommand>, on: Boolean): CharacterSession {
        val session = CharacterSession(empty, blendLibrary(on), work.scope, work.dispatcher)
        commands.forEach(session::send)
        work.settle(session)
        return session
    }

    private fun average(block: () -> Unit): Duration {
        block()
        return (0 until ROUNDS).map { measureTime(block) }.reduce(Duration::plus) / ROUNDS
    }

    private fun bench(name: String, empty: SquashDocument, commands: List<DocumentCommand>) {
        val off = average { session(empty, commands, on = false) }
        val on = average { session(empty, commands, on = true) }
        println("$name: whole build off $off, on $on")
        val session = session(empty, commands, on = true)
        val character = session.document.value.character
        for (part in character.parts) {
            val template = session.builtTemplate(part)
            val data = character.resolved(part).shape
            if (PartBuilder.blendOf(template, data) == null) continue
            val built = average { PartBuilder.build(template, data) }
            val blended = average { PartBuilder.blended(template, data) }
            println("$name ${part.id.value}: slices $built, slices and blend $blended")
        }
        val assembled = assertNotNull(session.assembly.output.value)
        val fillets = average { AssembledCharacter(assembled.parts, assembled.sockets).withSocketFillets() }
        println("$name: socket fillets $fillets")
    }

    @Test
    fun thePerson() = bench("person", EMPTY_PERSON, personCommands())

    @Test
    fun theFennec() = bench("fennec", EMPTY_FENNEC, fennecCommands())
}
