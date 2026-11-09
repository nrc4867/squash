package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.hiddenAgain
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import dev.chieppa.squash.screens.person.ARM_L
import dev.chieppa.squash.screens.person.personDressedDocument
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

/**
 * What one step of a pose drag costs on the dressed fennec and the dressed person: a bone turned a little further each step, the
 * session settled after each, the posed body, surface work, lines and fur with it, and of that what deciding again what the garments
 * hide takes. The medians over the steps go to the test's output.
 */
@Tag("slow")
class DressedPoseStepBenchTest {
    private val work = TestWork()

    @Test
    fun theDressedFennec() {
        measure("dressed fennec, knee", fennecDressedDocument(), FRONT_LEG_L, "knee")
        measure("dressed fennec, neck", fennecDressedDocument(), HEAD, "neck")
    }

    @Test
    fun theDressedPerson() = measure("dressed person, shoulder", personDressedDocument(), ARM_L, "shoulder")

    private fun measure(name: String, document: SquashDocument, part: PartId, joint: String) {
        val session = CharacterSession(document, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        SessionPose(session)
        work.settle(session)
        val times = (0 until WARM + STEPS).map { step ->
            val started = System.nanoTime()
            session.send(SetPose(Pose(listOf(BonePose(part, joint, step.toFloat() * STEP_DEGREES)))))
            work.settle(session)
            (System.nanoTime() - started) / 1e6
        }.drop(WARM).sorted()
        val rest = assertNotNull(session.assembly.output.value)
        val posed = assertNotNull(session.posedBody.output.value)
        val hiding = (0 until STEPS).map {
            val started = System.nanoTime()
            posed.hiddenAgain(rest)
            (System.nanoTime() - started) / 1e6
        }.sorted()
        println("$name: deciding again what the garments hide takes ${"%.1f".format(hiding[hiding.size / 2])} ms a step")
        println("$name: median ${"%.1f".format(times[times.size / 2])} ms a step, fastest ${"%.1f".format(times.first())} ms, over $STEPS steps")
    }

    private companion object {
        const val WARM = 15
        const val STEPS = 40
        const val STEP_DEGREES = 1.5f
    }
}
