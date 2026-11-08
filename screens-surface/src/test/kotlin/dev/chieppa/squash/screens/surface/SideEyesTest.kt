package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.EyeFrame
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.solveSurfaceGaze
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.GazeCheats
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.person.personDocument
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.toucan.toucanDocument
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val HEAD = PartId("head")

/** Side-set eyes, the toucan's, read their angle versions from their own facing and lean their gaze out along it. See HLD: parts, Side-set eyes. */
class SideEyesTest {
    private val work = TestWork()

    private class Face(val head: PlacedPart, val work: ResolvedSurfaceWork)

    private fun faceOf(session: CharacterSession): Face {
        work.settle(session)
        val head = assertNotNull(session.assembly.output.value?.part(HEAD))
        return Face(head, assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(HEAD)))
    }

    /** The degrees between each eye's facing and its head's. */
    private fun eyeTurns(face: Face): List<Float> {
        val marks = face.work.marks.associateBy { it.id }
        return face.work.groups.filter { it.isEye }.map { group -> degreesBetween(face.head.facing, assertNotNull(eyeFrame(group, marks, face.head, FACE_SIDE)).normal) }
    }

    private fun sight(face: Face, toViewer: Vec3): FullView {
        val center = assertNotNull(face.head.box).center
        return FullView({ sideCamera(center, toViewer * 5f) }, VIEW_PIXELS, VIEW_PIXELS)
    }

    @Test
    fun theFennecsAndThePersonsEyesAreSetToTheFrontAndTheToucansToTheSide() {
        val fennec = faceOf(openSession(fennecDocument(), work))
        val person = faceOf(openSession(personDocument(), work))
        val toucan = faceOf(openSession(toucanDocument(), work))
        println("eyes turned from the head: fennec ${eyeTurns(fennec)}, person ${eyeTurns(person)}, toucan ${eyeTurns(toucan)}")
        assertEquals(emptyList(), sideEyes(fennec.head, fennec.work, FACE_SIDE), "the fennec's face reads the head's angle")
        assertEquals(emptyList(), sideEyes(person.head, person.work, FACE_SIDE), "the person's face reads the head's angle")
        assertEquals(2, sideEyes(toucan.head, toucan.work, FACE_SIDE).size, "both of the toucan's eyes are set to the side")
    }

    @Test
    fun aToucansEyeShowsItsFrontVersionSquareOnFromTheSide() {
        val toucan = faceOf(openSession(toucanDocument(), work))
        val eyes = sideEyes(toucan.head, toucan.work, FACE_SIDE)
        val near = eyes.maxBy { it.facing dot Vec3(0f, 0f, -1f) }
        val fromSide = sight(toucan, Vec3(0f, 0f, -1f))
        assertEquals(FACE_SIDE, FaceBinsHeld().binOf(toucan.head, fromSide), "the head is side on")
        assertEquals(FACE_FRONT, FaceBinsHeld().binOf(toucan.head, fromSide, near), "the near eye faces the viewer")
        val ahead = assertNotNull(toucan.head.facing).normalized()
        val fromAhead = sight(toucan, ahead)
        assertEquals(FACE_FRONT, FaceBinsHeld().binOf(toucan.head, fromAhead), "the head faces the viewer")
        assertTrue(eyes.all { FaceBinsHeld().binOf(toucan.head, fromAhead, it) == FACE_SIDE }, "each eye is edge on to a viewer ahead")
    }

    @Test
    fun aSideSetEyeLooksAheadWithoutPinningItsPupilToTheLid() {
        val travel = Vec2(0.1f, 0.1f)
        val plain = GazeCheats(cameraBias = 0f, sizeCompensation = false)
        val left = EyeFrame(Vec3(0f, 0f, -0.2f), Vec3(0f, 0f, -1f), Vec3.X, Vec3.Y, travel)
        val right = EyeFrame(Vec3(0f, 0f, 0.2f), Vec3(0f, 0f, 1f), Vec3.X, Vec3.Y, travel)
        val ahead = Vec3(5f, 0f, 0f)
        val front = solveSurfaceGaze(listOf(left, right), ahead, Vec3.X, plain)
        val side = solveSurfaceGaze(listOf(left.copy(sideSet = true), right.copy(sideSet = true)), ahead, Vec3.X, plain)
        assertTrue(front.all { it.offset.x > 0.099f }, "an eye set to the front looks all the way along its surface")
        assertTrue(side.all { it.offset.x > 0.03f && it.offset.x < 0.09f }, "a side-set eye looks forward short of its lid: ${side.map { it.offset }}")
        val out = solveSurfaceGaze(listOf(left.copy(sideSet = true)), Vec3(0f, 0f, -5f), Vec3.X, plain)
        assertTrue(abs(out.single().offset.x) < 1e-4f && abs(out.single().offset.y) < 1e-4f, "looking straight out it stays in the middle")
    }

    @Test
    fun aToucanLookingAheadKeepsItsPupilsOffTheLid() {
        val session = openSession(toucanDocument(), work)
        val toucan = faceOf(session)
        val marks = toucan.work.marks.associateBy { it.id }
        val frames = toucan.work.groups.filter { it.isEye }.mapNotNull { eyeFrame(it, marks, toucan.head, FACE_SIDE) }
        val center = assertNotNull(toucan.head.box).center
        val target = center + toucan.head.facing.normalized() * 3f
        val plain = GazeCheats(cameraBias = 0f, sizeCompensation = false)
        val looks = solveSurfaceGaze(frames.map { it.copy(sideSet = true) }, target, Vec3(0f, 0f, -1f), plain)
        val unbiased = solveSurfaceGaze(frames, target, Vec3(0f, 0f, -1f), plain)
        frames.indices.forEach { i ->
            val reach = abs(looks[i].offset.x) / frames[i].travel.x
            val square = abs(unbiased[i].offset.x) / frames[i].travel.x
            assertTrue(reach > 0.3f && reach < 0.9f && reach < square, "the pupil moves toward the target and stays clear of the lid: $reach of its travel, $square looking square along the surface")
        }
    }
}
