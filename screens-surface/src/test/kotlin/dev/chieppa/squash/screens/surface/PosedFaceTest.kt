package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.sheet.FullView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How far the head turns toward the viewer, in degrees: well past the side version's edge. */
private const val TURN = 50f

/** A mark's point on the turned head has moved at least this far, in body units. */
private const val MOVED = 0.02f

class PosedFaceTest {
    private val session = openSession(fennecDocument()).also { SessionPose(it); it.settle() }

    private fun turnHead() {
        session.send(SetPose(Pose(listOf(BonePose(HEAD, "neck", lean = TURN, mode = JointMode.FREE)))))
        session.settle()
    }

    @Test
    fun theFacesMarksMoveWithTheTurnedHead() {
        val rest = assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(HEAD)).marks
        turnHead()
        val posed = assertNotNull(session.posedSurfaceWork.output.value?.parts?.get(HEAD)).marks.associateBy { it.id }
        assertTrue(rest.isNotEmpty())
        for (mark in rest) {
            val turned = posed.getValue(mark.id).points
            assertTrue(turned.zip(mark.points).all { (a, b) -> (a.position - b.position).length > MOVED }, "${mark.id} stays where it rests")
        }
        val lines = assertNotNull(session.posedLines.output.value)
        val head = assertNotNull(session.posedBody.output.value?.part(HEAD))
        assertTrue(lines.parts.getValue(HEAD).part === head, "The face's lines lie on another head than the posed one")
    }

    @Test
    fun theVersionShownFollowsThePosedHead() {
        val restHead = assertNotNull(session.assembly.output.value?.part(HEAD))
        val center = assertNotNull(restHead.box).center
        val sight = FullView({ sideCamera(center) }, VIEW_PIXELS, VIEW_PIXELS)
        assertEquals(FACE_SIDE, FaceBinsHeld().binOf(restHead, sight))
        turnHead()
        val turned = assertNotNull(session.posedBody.output.value?.part(HEAD))
        val angle = headAngle(turned, sight.toViewer(assertNotNull(turned.box).center))
        assertTrue(angle > FACE_BINS.edges.first(), "The turned head reads $angle degrees")
        assertEquals(FACE_BINS.binFor(angle), FaceBinsHeld().binOf(turned, sight))
        assertTrue(FaceBinsHeld().binOf(turned, sight) != FACE_SIDE)
    }
}
