package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.rig.curled
import dev.chieppa.squash.rig.spread
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.CloseUps
import kotlin.test.assertNotNull

/** How far the fist closes the digits, as a share of each joint's whole curl. */
private const val FIST = 0.85f

/** How far the open hand's digits stand curled: all but straight, which a pose would drop for the display pose's curl. */
private const val OPEN = 0.01f

/** How far each digit leans from the row's middle at its root when the row spreads, in degrees. */
private const val SPREAD = 14f

/**
 * A bare hand, foot or paw from six sides (front, back, both sides, above and below) in its states, each image named [prefix] and the
 * state: as shown (`rest`), as built with no display pose (`built`), its digits curled into a fist (`curl`), straight open (`open`)
 * and, with [spreads], straight and fanned apart at their roots (`spread`), each over the pose the example shows.
 */
class DigitCloseUps(private val closeUps: CloseUps, private val work: TestWork, private val prefix: String, private val spreads: Boolean = false) {
    fun keep(session: CharacterSession, pose: SessionPose, part: PartId) {
        val shown = session.document.value.character.pose
        closeUps.keep(session, part, "$prefix-rest")
        val display = session.document.value.character.displayPose
        session.send(SetDisplayPose(Pose()))
        work.settle(session)
        closeUps.keep(session, part, "$prefix-built")
        session.send(SetDisplayPose(display))
        work.settle(session)
        val rig = assertNotNull(pose.rig.rig.output.value)
        session.send(SetPose(rig.curled(shown, listOf(part), FIST)))
        work.settle(session)
        closeUps.keep(session, part, "$prefix-curl")
        val open = rig.curled(shown, listOf(part), OPEN)
        session.send(SetPose(open))
        work.settle(session)
        closeUps.keep(session, part, "$prefix-open")
        if (spreads) {
            session.send(SetPose(rig.spread(open, listOf(part), SPREAD)))
            work.settle(session)
            closeUps.keep(session, part, "$prefix-spread")
        }
        session.send(SetPose(shown))
        work.settle(session)
    }
}
