package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.screens.TestWork

import dev.chieppa.squash.character.face.eyeLandmark
import dev.chieppa.squash.character.face.newEyesCommand
import dev.chieppa.squash.character.face.newMouthCommand

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.screens.fennec.CREAM
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.lines.PickedGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Add eyes on a head that has none: two starter eyes that draw at once, the second mirroring the first, as one undo step. */
class StarterEyesTest {
    private val work = TestWork()
    private val session: CharacterSession = openSession(fennecDocument(), work)
    private val character get() = session.document.value.character

    private fun withoutEyes() {
        val eyes = assertNotNull(character.part(HEAD)).let { character.resolved(it) }.surface.groups.filter { it.isEye }
        session.send(Together(eyes.map { SetMarkGroup(HEAD, it.id, null) }))
    }

    @Test
    fun theFaceStageWorksOnTheHead() {
        assertEquals(HEAD, character.faceHead(session.templates))
    }

    @Test
    fun addEyesMakesTwoStarterEyesThatDraw() {
        withoutEyes()
        session.send(assertNotNull(newEyesCommand(character, HEAD)))
        val left = assertNotNull(character.groupOf(PickedGroup(HEAD, MarkGroupId("eye-l"))))
        val right = assertNotNull(character.groupOf(PickedGroup(HEAD, MarkGroupId("eye-r"))))
        listOf(MarkRole.LID, MarkRole.WHITE, MarkRole.PUPIL).forEach { role ->
            assertNotNull(character.markIn(left, role, HEAD), "the left eye draws its $role")
            assertNotNull(character.markIn(right, role, HEAD), "the right eye draws its $role")
        }
        assertEquals(CREAM, left.roles.first { it.role == MarkRole.WHITE }.fill)
        val leftLid = assertNotNull(character.markIn(left, MarkRole.LID, HEAD)).points
        val rightLid = assertNotNull(character.markIn(right, MarkRole.LID, HEAD)).points
        leftLid.zip(rightLid).forEach { (l, r) -> assertEquals(1f - l.v, r.v, 1e-5f, "the right eye mirrors the left") }
        work.settle(session)
        val work = assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(HEAD))
        val lid = assertNotNull(character.markIn(left, MarkRole.LID, HEAD)).id
        assertTrue(work.marks.any { it.id == lid && it.points.isNotEmpty() }, "the starter lid lands on the head")

        session.history.undo()
        assertTrue(assertNotNull(character.part(HEAD)).let { character.resolved(it) }.surface.groups.none { it.isEye }, "one undo takes both eyes")
    }

    @Test
    fun aHeadWithEyesGetsNoMore() {
        assertNull(newEyesCommand(character, HEAD))
    }
}
