package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.rig.curled
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.FRONT_PAW_L
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.person.EMPTY_PERSON
import dev.chieppa.squash.screens.person.HAND_L
import dev.chieppa.squash.screens.person.personCommands
import dev.chieppa.squash.screens.toucan.EMPTY_TOUCAN
import dev.chieppa.squash.screens.toucan.FOOT_L as TOUCAN_FOOT_L
import dev.chieppa.squash.screens.toucan.toucanCommands
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

/** How far the curled renders close the digits. */
private const val CURL = 0.7f

/**
 * Close views of the person's hand, the fennec's front paw and the toucan's foot, at rest and curled, two-tone with lines, through
 * the real graph, rig and scene, left for a person to judge whether they read as hands, paws and feet.
 */
@Tag("slow")
class DigitCloseUpSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val work = TestWork()
    private val closeUps = CloseUps(work, errors)

    private fun closeUps(empty: SquashDocument, commands: List<DocumentCommand>, part: PartId, name: String) {
        val session = closeUps.session(empty, commands)
        val pose = SessionPose(session)
        work.settle(session)
        closeUps.keep(session, part, "digits-$name-rest")
        val rig = assertNotNull(pose.rig.rig.output.value)
        session.send(SetPose(rig.curled(Pose(), listOf(part), CURL)))
        closeUps.keep(session, part, "digits-$name-curled")
        assertEquals(emptyList(), errors)
    }

    @Test
    fun thePersonsHand() = closeUps(EMPTY_PERSON, personCommands(), HAND_L, "hand")

    @Test
    fun theFennecsPaw() = closeUps(EMPTY_FENNEC, fennecCommands(), FRONT_PAW_L, "paw")

    @Test
    fun theToucansFoot() = closeUps(EMPTY_TOUCAN, toucanCommands(), TOUCAN_FOOT_L, "foot-bird")
}
