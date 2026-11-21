package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.screens.TestWork

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.shownJoints
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MouthShapeName
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.document.ShowExpression
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.screens.fennec.EARS_BACK
import dev.chieppa.squash.screens.fennec.EARS_FORWARD
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.HAPPY
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.MUZZLE_PATCH
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.screens.sheet.FullView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private val MOUTH = MarkGroupId("mouth")
private const val PIXELS = 400

@Tag("integration")
class FaceWorkTest {
    private val work = TestWork()
    private val session: CharacterSession by lazy { openSession(fennecDocument(), work) }

    private fun faces(): FaceWork {
        work.settle(session)
        val whole = assertNotNull(session.assembly.output.value)
        val work: BodySurfaceWork = assertNotNull(session.bodySurfaceWork.output.value)
        val character = session.document.value.character
        val input = FaceInput(whole.parts.mapNotNull { part -> part.id?.let { it to part } }.toMap(), work, session.bodyLines.output.value, character.palette, character.shownExpression)
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
    fun aSmileMovesTheLineTheViewDrawsAndAPreviewSaysAShapeWithoutAnEdit() {
        val rest = faces().draws(HEAD, side)
        session.send(SetMarkControls(HEAD, MOUTH, MarkControls(smile = 1f)))
        val smiling = faces().draws(HEAD, side)
        assertNotEquals(rest.ribbons, smiling.ribbons, "the smile bends the lip line drawn")
        session.history.undo()
        session.mouthPreview.value = MouthShapeName.AI
        val muzzle = faces().draws(HEAD, side)
        assertEquals(rest.layers.size, muzzle.layers.size, "a muzzle's style says no shape")
        assertEquals(rest.ribbons.size, muzzle.ribbons.size)
        session.mouthPreview.value = null
        val group = assertNotNull(session.document.value.character.resolved(HEAD)).surface.groups.first { it.id == MOUTH }
        session.send(SetMarkGroup(HEAD, MOUTH, group.copy(style = MouthStyle.LIPS)))
        val lips = faces().draws(HEAD, side)
        val saved = session.document.value
        session.mouthPreview.value = MouthShapeName.AI
        val saying = faces().draws(HEAD, side)
        assertTrue(saying.layers.size > lips.layers.size, "saying ai opens a mouth in lips on the view")
        assertEquals(saved, session.document.value, "a preview is no edit")
        session.mouthPreview.value = null
    }

    @Test
    fun anExpressionsPoseTurnsTheEars() {
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
        session.send(ShowExpression(HAPPY))
        assertEquals(setOf("jaw"), shownJoints(session.document.value.character, HEAD, assertNotNull(session.templates.find(assertNotNull(session.document.value.character.part(HEAD)).template))).keys)
    }

    @Test
    fun theFaceStageWorksOnTheHead() {
        assertEquals(HEAD, session.document.value.character.faceHead(session.templates))
    }

    @Test
    fun drawingARoleWritesItsVersionAndInTheExpressionShownTheExpressionsOwnMark() {
        val character = { session.document.value.character }
        val picked = PickedGroup(HEAD, MOUTH)
        val stroke = listOf(Anchor(HEAD, MUZZLE_PATCH, 0.4f, 0.2f), Anchor(HEAD, MUZZLE_PATCH, 0.5f, 0.15f))
        session.send(assertNotNull(drawnMarkCommand(character(), picked, MarkRole.CORNERS, stroke, FACE_SIDE)))
        val drawn = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.CORNERS, HEAD))
        assertEquals(stroke, drawn.versions[FACE_SIDE]?.points)
        session.history.undo()
        assertEquals(null, character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.CORNERS, HEAD), "one undo takes it")

        session.send(ShowExpression(HAPPY))
        session.send(assertNotNull(drawnMarkCommand(character(), picked, MarkRole.CORNERS, stroke, FACE_FRONT)))
        val written = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.CORNERS, HEAD))
        assertEquals(stroke, written.versions[FACE_FRONT]?.points)
        assertTrue(HAPPY.value in written.id.value, "it is the expression's own mark")
    }

    @Test
    fun aClosedRoleClosesAndMovingItsEndMovesBoth() {
        val character = { session.document.value.character }
        val picked = PickedGroup(HEAD, MOUTH)
        val stroke = listOf(Anchor(HEAD, MUZZLE_PATCH, 0.4f, 0.2f), Anchor(HEAD, MUZZLE_PATCH, 0.5f, 0.15f), Anchor(HEAD, MUZZLE_PATCH, 0.5f, 0.25f))
        session.send(assertNotNull(drawnMarkCommand(character(), picked, MarkRole.TEETH, stroke, FACE_SIDE)))
        val points = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.TEETH, HEAD)?.versions?.get(FACE_SIDE)).points
        assertEquals(points.first(), points.last())
        val to = Anchor(HEAD, MUZZLE_PATCH, 0.42f, 0.21f)
        session.send(assertNotNull(movedPlaceCommand(character(), picked, MarkRole.TEETH, FACE_SIDE, 0, to)))
        val moved = assertNotNull(character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.TEETH, HEAD)?.versions?.get(FACE_SIDE)).points
        assertEquals(to, moved.first())
        assertEquals(to, moved.last())
        session.send(assertNotNull(erasedMarkCommand(character(), picked, MarkRole.TEETH, FACE_SIDE)))
        assertEquals(null, character().markIn(assertNotNull(character().groupOf(picked)), MarkRole.TEETH, HEAD)?.versions?.get(FACE_SIDE), "that version goes")
    }
}
