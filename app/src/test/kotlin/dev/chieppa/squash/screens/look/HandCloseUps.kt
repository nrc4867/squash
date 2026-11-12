package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.rig.curled
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.CloseUps
import kotlin.test.assertNotNull

/** How far the fist closes the digits, as a share of each joint's whole curl. */
private const val FIST = 0.85f

/** How far the open hand's digits stand curled: all but straight, which a pose would drop for the display pose's curl. */
private const val OPEN = 0.01f

/**
 * The bare hand from six sides (front, back, both sides, above and below) in three states: as shown (`hand-rest`), its digits curled
 * into a fist (`hand-curl`) and straight open (`hand-open`), each over the pose the example shows.
 */
class HandCloseUps(private val closeUps: CloseUps, private val work: TestWork) {
    fun keep(session: CharacterSession, pose: SessionPose, hand: PartId) {
        val shown = session.document.value.character.pose
        closeUps.keep(session, hand, "hand-rest")
        val display = session.document.value.character.displayPose
        session.send(dev.chieppa.squash.document.SetDisplayPose(dev.chieppa.squash.document.Pose()))
        work.settle(session)
        closeUps.keep(session, hand, "hand-built")
        session.send(dev.chieppa.squash.document.SetDisplayPose(display))
        work.settle(session)
        val rig = assertNotNull(pose.rig.rig.output.value)
        session.send(SetPose(rig.curled(shown, listOf(hand), FIST)))
        work.settle(session)
        closeUps.keep(session, hand, "hand-curl")
        session.send(SetPose(rig.curled(shown, listOf(hand), OPEN)))
        work.settle(session)
        closeUps.keep(session, hand, "hand-open")
        session.send(SetPose(shown))
        work.settle(session)
    }
}
