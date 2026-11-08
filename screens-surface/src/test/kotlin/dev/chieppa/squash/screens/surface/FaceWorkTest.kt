package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.screens.TestWork

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.EyeFrame
import dev.chieppa.squash.character.build.solveSurfaceGaze
import dev.chieppa.squash.character.build.shownJoints
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.GazeCheats
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MouthShape
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.ShowExpression
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.screens.fennec.EARS_BACK
import dev.chieppa.squash.screens.fennec.EARS_FORWARD
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.HAPPY
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.SLEEPY
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.screens.sheet.FullView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val EYE_L = MarkGroupId("eye-l")
private val EYE_R = MarkGroupId("eye-r")
private val MOUTH = MarkGroupId("mouth")
private const val PIXELS = 400

class FaceWorkTest {
    private val work = TestWork()
    private val session: CharacterSession = openSession(fennecDocument(), work)

    private fun faces(target: Vec3? = null): FaceWork {
        work.settle(session)
        val whole = assertNotNull(session.assembly.output.value)
        val work: BodySurfaceWork = assertNotNull(session.bodySurfaceWork.output.value)
        val character = session.document.value.character
        val input = FaceInput(whole.parts.mapNotNull { part -> part.id?.let { it to part } }.toMap(), work, session.bodyLines.output.value, character.palette, Vec3(0.4f, 0.8f, -0.4f), character.shownExpression, target, character.gazeCheats)
        return FaceWork(input, LineRule(2f), Color.Black, 0.5f, FaceBinsHeld()::binOf)
    }

    private fun view(toViewer: Vec3): FullView {
        work.settle(session)
        val whole = assertNotNull(session.assembly.output.value)
        val camera = framedCamera(listOfNotNull(whole.part(HEAD)), toViewer, PIXELS, PIXELS, 0.1f).camera
        return FullView({ camera }, PIXELS, PIXELS)
    }

    private val side get() = view(Vec3(0f, 0f, -1f))

    @Test
    fun aHeadsVersionFollowsTheAngleAndHoldsPastItsEdge() {
        assertEquals(FACE_SIDE, FACE_BINS.binFor(0f))
        assertEquals(FACE_THREE_QUARTER, FACE_BINS.binFor(50f))
        assertEquals(FACE_FRONT, FACE_BINS.binFor(80f))
        assertEquals(FACE_SIDE, FACE_BINS.binFor(38f, FACE_SIDE), "it holds past the edge")
        assertEquals(FACE_THREE_QUARTER, FACE_BINS.binFor(38f, FACE_THREE_QUARTER))
        assertEquals(FACE_THREE_QUARTER, FACE_BINS.binFor(42f, FACE_SIDE))
    }

    @Test
    fun aShutEyeFillsNothingButItsLidStaysALine() {
        val before = faces().draws(HEAD, side)
        session.send(SetMarkControls(HEAD, EYE_L, MarkControls(open = 0f)))
        session.send(SetMarkControls(HEAD, EYE_R, MarkControls(open = 0f)))
        val shut = faces().draws(HEAD, side)
        assertTrue(before.layers.size > shut.layers.size, "the white, iris, pupil and highlight go")
        assertEquals(before.ribbons.size, shut.ribbons.size, "the lid, brow and lashes stay")
    }

    @Test
    fun aLidOpensBetweenItsCornersWithNothingJumping() {
        val lid = listOf(Vec2(0f, 0f), Vec2(1f, 1f), Vec2(2f, 0f), Vec2(1f, -0.5f), Vec2(0f, 0f))
        assertEquals(lid, openedLid(lid, 1f))
        val shut = openedLid(lid, 0f)
        shut.forEach { assertEquals(0f, it.y, 1e-5f, "it lies on the corners' line") }
        val half = openedLid(lid, 0.5f)
        assertEquals(0.5f, half[1].y, 1e-5f)
        assertEquals(-0.25f, half[3].y, 1e-5f)
    }

    @Test
    fun aPupilStaysInsideItsLid() {
        val lid = List(13) { i ->
            val angle = 2.0 * Math.PI * i / 12
            Vec2(0.5f + 0.075f * kotlin.math.cos(angle).toFloat(), 0.2f + 0.05f * kotlin.math.sin(angle).toFloat())
        }
        val from = Vec2(0.5f, 0.2f)
        assertEquals(Vec2(0.01f, 0f), heldInLid(from, Vec2(0.01f, 0f), lid), "a small look is left alone")
        val held = heldInLid(from, Vec2(1f, 1f), lid)
        assertTrue(kotlin.math.abs(held.x) < 0.075f && kotlin.math.abs(held.y) < 0.05f, "$held stays in")
    }

    @Test
    fun theGazeLooksAtItsTargetAndConvergesNearbyWithinALimit() {
        val left = EyeFrame(Vec3(0f, 0f, -0.2f), Vec3(0f, 0f, -1f), Vec3.X, Vec3.Y, Vec2(0.1f, 0.1f))
        val right = EyeFrame(Vec3(0f, 0f, 0.2f), Vec3(0f, 0f, 1f), Vec3.X, Vec3.Y, Vec2(0.1f, 0.1f))
        val plain = GazeCheats(cameraBias = 0f, sizeCompensation = false)
        val ahead = solveSurfaceGaze(listOf(left, right), Vec3(5f, 0f, 0f), Vec3.X, plain)
        assertTrue(ahead.all { it.offset.x > 0.09f }, "both look forward along u")
        val up = solveSurfaceGaze(listOf(left, right), Vec3(0f, 5f, 0f), Vec3.X, plain)
        assertTrue(up.all { it.offset.y > 0.09f }, "both look up along v")
        val biased = solveSurfaceGaze(listOf(left, right), Vec3(0f, 5f, 0f), Vec3.X, GazeCheats(0.5f, false))
        assertTrue(biased.all { it.offset.x > 0f }, "the bias pulls them toward the camera")
        val far = solveSurfaceGaze(listOf(left, right), Vec3(5f, 0f, 0f), Vec3(0.2f, 0f, -1f).normalized(), GazeCheats(0f, true))
        assertTrue(far[1].scale > far[0].scale, "the eye turned from the camera draws larger")
    }

    @Test
    fun aTargetMovesThePupilsOnTheFaceAndChangesNoPoints() {
        val still = faces().draws(HEAD, side)
        val looking = faces(Vec3(3f, 2f, 0f)).draws(HEAD, side)
        assertEquals(still.layers.size, looking.layers.size)
        assertTrue(still.layers.indices.any { !still.layers[it].mesh.vertices.contentEquals(looking.layers[it].mesh.vertices) }, "something of the eye moved")
    }

    @Test
    fun aMouthOpensItsLipsItsColorAndItsJaw() {
        val closed = faces().draws(HEAD, side)
        val chin = { assertNotNull(session.assembly.output.value?.part(HEAD)).surface.patches.first { it.name == "muzzle" } }
        work.settle(session)
        val before = chin().points.copyOf()
        session.send(SetMarkControls(HEAD, MOUTH, MarkControls(open = 0.6f)))
        val open = faces().draws(HEAD, side)
        assertTrue(open.layers.size > closed.layers.size, "the inner color and the tongue show")
        assertTrue(open.ribbons.size > closed.ribbons.size, "a lower lip parts from each line")
        assertEquals(mapOf("jaw" to 0.6f * 35f), shownJoints(session.document.value.character, HEAD, assertNotNull(session.templates.find(assertNotNull(session.document.value.character.part(HEAD)).template))))
        assertNotEquals(before.toList(), chin().points.toList(), "the jaw turns the muzzle's underside")
    }

    @Test
    fun aMouthsShapeAndWidthMoveItsLine() {
        val lip = listOf(Vec2(0.98f, 0.4f), Vec2(0.9f, 0.34f), Vec2(0.78f, 0.31f), Vec2(0.68f, 0.28f))
        assertEquals(lip, shapedLip(lip, MarkControls()), "with no shape it is as drawn")
        assertTrue(shapedLip(lip, MarkControls(mouthShape = MouthShape.SMILE)).last().y < lip.last().y, "a smile lifts the corner")
        assertTrue(shapedLip(lip, MarkControls(mouthShape = MouthShape.FROWN)).last().y > lip.last().y, "a frown drops it")
        assertTrue(shapedLip(lip, MarkControls(width = 0.5f)).last().x > lip.last().x, "narrower pulls the corner toward the front")
        val lower = lowerLip(lip, 1f)
        assertEquals(0.5f, lower.first().y, 1e-5f, "at the front it parts to the underside")
        assertEquals(lip.last(), lower.last(), "the corner does not part")
    }

    @Test
    fun anExpressionsPoseTurnsTheEarsAndItsMarksReplaceTheRole() {
        work.settle(session)
        fun earTip() = assertNotNull(session.assembly.output.value?.part(EAR_L)).let { it.pointOn(0.9f, 0f) }
        val rest = assertNotNull(earTip())
        session.send(ShowExpression(EARS_BACK))
        work.settle(session)
        val back = assertNotNull(earTip())
        assertTrue(back.x < rest.x - 0.05f, "back moves the ear's tip rearward, from ${rest.x} to ${back.x}")
        session.send(ShowExpression(EARS_FORWARD))
        work.settle(session)
        assertTrue(assertNotNull(earTip()).x > rest.x + 0.02f, "forward moves it ahead")
        session.send(ShowExpression(null))
        work.settle(session)
        assertEquals(rest, assertNotNull(earTip()), "neutral stands it back up")
        val plain = faces().draws(HEAD, side)
        session.send(ShowExpression(SLEEPY))
        val sleepy = faces().draws(HEAD, side)
        assertFalse(plain.layers.indices.all { plain.layers[it].mesh.vertices.contentEquals(sleepy.layers[it].mesh.vertices) }, "its lid clips the eye differently")
        session.send(ShowExpression(HAPPY))
        assertEquals(setOf("jaw"), shownJoints(session.document.value.character, HEAD, assertNotNull(session.templates.find(assertNotNull(session.document.value.character.part(HEAD)).template))).keys)
    }

    @Test
    fun drawingARoleWritesItsVersionInTheExpressionShownAndAcrossToTheOtherEye() {
        val character = { session.document.value.character }
        val picked = PickedGroup(HEAD, EYE_L)
        val stroke = listOf(Anchor(HEAD, BODY_PATCH, 0.4f, 0.2f), Anchor(HEAD, BODY_PATCH, 0.5f, 0.15f))
        session.send(assertNotNull(drawnMarkCommand(character(), picked, MarkRole.BROW, stroke, FACE_SIDE, mirror = true)))
        val left = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.BROW, HEAD))
        assertEquals(stroke, left.versions[FACE_SIDE]?.points)
        val right = assertNotNull(character().markIn(assertNotNull(character().groupOf(PickedGroup(HEAD, EYE_R))), MarkRole.BROW, HEAD))
        assertEquals(0.8f, assertNotNull(right.versions[FACE_SIDE]).points.first().v, 1e-5f, "reflected across the middle")
        session.history.undo()
        assertNotEquals(stroke, character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.BROW, HEAD)?.versions?.get(FACE_SIDE)?.points, "one undo takes both")

        session.send(ShowExpression(HAPPY))
        val brow = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.BROW, HEAD)).id
        session.send(assertNotNull(drawnMarkCommand(character(), picked, MarkRole.BROW, stroke, FACE_FRONT, mirror = false)))
        val written = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.BROW, HEAD))
        assertEquals(brow, written.id, "it writes the expression's own mark")
        assertEquals(stroke, written.versions[FACE_FRONT]?.points)
    }

    @Test
    fun aClosedRoleClosesAndMovingItsEndMovesBoth() {
        val character = { session.document.value.character }
        val picked = PickedGroup(HEAD, EYE_L)
        val stroke = listOf(Anchor(HEAD, BODY_PATCH, 0.4f, 0.2f), Anchor(HEAD, BODY_PATCH, 0.5f, 0.15f), Anchor(HEAD, BODY_PATCH, 0.5f, 0.25f))
        session.send(assertNotNull(drawnMarkCommand(character(), picked, MarkRole.IRIS, stroke, FACE_SIDE, mirror = false)))
        val points = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.IRIS, HEAD)?.versions?.get(FACE_SIDE)).points
        assertEquals(points.first(), points.last())
        val to = Anchor(HEAD, BODY_PATCH, 0.42f, 0.21f)
        session.send(assertNotNull(movedPlaceCommand(character(), picked, MarkRole.IRIS, FACE_SIDE, 0, to, mirror = false)))
        val moved = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.IRIS, HEAD)?.versions?.get(FACE_SIDE)).points
        assertEquals(to, moved.first())
        assertEquals(to, moved.last())
        session.send(assertNotNull(erasedMarkCommand(character(), picked, MarkRole.IRIS, FACE_SIDE)))
        assertEquals(null, character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.IRIS, HEAD)?.versions?.get(FACE_SIDE), "that version goes")
    }

    private fun assertFalse(condition: Boolean, message: String) = assertTrue(!condition, message)
}
