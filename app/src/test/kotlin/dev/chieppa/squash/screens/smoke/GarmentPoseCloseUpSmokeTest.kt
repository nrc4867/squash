package dev.chieppa.squash.screens.smoke

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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Tag

/** Close views of the dressed fennec and person in deep poses, where a garment meets what comes out of it: the turned neck, the bent knee and the raised arm. */
@Tag("slow")
class GarmentPoseCloseUpSmokeTest {
    private val work = TestWork()
    private val errors = CopyOnWriteArrayList<String>()
    private val closeUps = CloseUps(work, errors)

    private fun keep(document: SquashDocument, pose: BonePose, framed: PartId, name: String) {
        val session = closeUps.session(document, emptyList())
        SessionPose(session)
        session.send(SetPose(Pose(listOf(pose))))
        closeUps.keepAround(session, framed, name)
        assertEquals(emptyList(), errors)
    }

    @Test
    fun theFennecsTurnedNeck() = keep(fennecDressedDocument(), BonePose(HEAD, "neck", 40f), HEAD, "garment-close-neck")

    @Test
    fun theFennecsBentKnee() = keep(fennecDressedDocument(), BonePose(FRONT_LEG_L, "knee", 110f), FRONT_LEG_L, "garment-close-knee")

    @Test
    fun thePersonsRaisedArm() = keep(personDressedDocument(), BonePose(ARM_L, "shoulder", 80f), ARM_L, "garment-close-shoulder")
}
