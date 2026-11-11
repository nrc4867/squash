package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.woman.EMPTY_WOMAN
import dev.chieppa.squash.screens.woman.HEAD
import dev.chieppa.squash.screens.woman.FOOT_L
import dev.chieppa.squash.screens.woman.HAIR
import dev.chieppa.squash.screens.woman.HAND_L
import dev.chieppa.squash.screens.woman.OUTFITS
import dev.chieppa.squash.screens.woman.womanCommands
import dev.chieppa.squash.screens.woman.womanDressedCommands
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.screens.woman.ARM_L
import dev.chieppa.squash.screens.woman.ARM_R
import dev.chieppa.squash.screens.woman.CASUAL
import dev.chieppa.squash.screens.woman.LEG_L
import dev.chieppa.squash.screens.woman.LEG_R
import dev.chieppa.squash.screens.woman.NECK
import dev.chieppa.squash.screens.woman.OFFICE
import dev.chieppa.squash.screens.woman.WINTER
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Tag

/** The woman through her commands, the real graph, rig and scene, from the front round to her side, left for a person to judge. */
/** Her hand-made bones by name: a tube's own bone at its root, the arm's shoulder and elbow, the leg's knee, and the round head's neck. */
private const val TUBE_BASE = "base"
private const val ARM_SHOULDER = "joint.1"
private const val ARM_ELBOW = "joint.2"
private const val KNEE = "joint.1"
private const val NECK_TURN = "neck"

@Tag("slow")
class WomanSmokeTest {
    private val work = TestWork()
    private val errors = CopyOnWriteArrayList<String>()
    private val closeUps = CloseUps(work, errors)
    private val views = WomanViews(work, errors)

    @Test
    fun bare() {
        val session = closeUps.session(EMPTY_WOMAN, womanCommands())
        SessionPose(session)
        views.keep(session, "woman-bare")
        views.keep(session, "woman-face", setOf(HEAD, HAIR), listOf(0f, 30f, 70f), tile = 600, height = 600)
        closeUps.keep(session, HAND_L, "woman-hand")
        closeUps.keep(session, FOOT_L, "woman-foot")
        assertEquals(emptyList(), errors)
    }

    @Test
    fun everyOutfit() {
        val session = closeUps.session(EMPTY_WOMAN, womanDressedCommands())
        SessionPose(session)
        for ((id, _, _) in OUTFITS) {
            session.send(ShowOutfit(id))
            views.keep(session, "woman-${id.value}")
        }
        session.send(ShowOutfit(WINTER))
        closeUps.keepAround(session, NECK, "woman-winter-close")
        assertEquals(emptyList(), errors)
    }

    @Test
    fun poses() {
        val session = closeUps.session(EMPTY_WOMAN, womanDressedCommands())
        SessionPose(session)
        session.send(ShowOutfit(CASUAL))
        session.send(SetPose(Pose(listOf(
            BonePose(ARM_L, ARM_SHOULDER, 35f), BonePose(ARM_R, ARM_SHOULDER, -30f), BonePose(ARM_R, ARM_ELBOW, -40f),
            BonePose(LEG_L, KNEE, 45f), BonePose(LEG_L, TUBE_BASE, 25f), BonePose(LEG_R, TUBE_BASE, -12f), BonePose(HEAD, NECK_TURN, 8f),
        ))))
        views.keep(session, "woman-pose-walk")
        session.send(ShowOutfit(OFFICE))
        session.send(SetPose(Pose(listOf(
            BonePose(ARM_L, ARM_SHOULDER, -95f), BonePose(ARM_L, ARM_ELBOW, -85f), BonePose(ARM_R, ARM_ELBOW, -95f),
            BonePose(HEAD, NECK_TURN, -12f),
        ))))
        views.keep(session, "woman-pose-salute")
        assertEquals(emptyList(), errors)
    }
}
