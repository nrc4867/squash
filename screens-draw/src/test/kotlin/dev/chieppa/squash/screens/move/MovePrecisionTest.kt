package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.edit.PANE_TAG
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.showPane
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_R
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.SheetPart
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PANE_WIDTH = 600
private const val PANE_HEIGHT = 400
private const val PIXELS_PER_UNIT = 400f

/** How far off a snap line, in pixels, a drag is let go: inside the snap's reach, and far enough that a miss shows. */
private const val OFF_LINE = 4f

/** The shipped fennec in the Workspace, its torso shown in a locked pane, and its left front leg's socket dragged with Move. */
@OptIn(ExperimentalTestApi::class)
class MovePrecisionTest {
    private val work = TestWork()
    private val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))
    private val state = AppState(folder, work).also {
        it.open(savedFennec())
        it.selectedPart.value = TORSO
        it.screen.value = Screen.WORKSPACE
    }
    private val session get() = assertNotNull(state.session.value)
    private val character: AssembledCharacter get() = assertNotNull(session.assembly.output.value)
    private val tool = MoveTool(state, SMOKE_LOOK, TEST_CURVE_LOOK, TEST_DRAG_GUIDE_LOOK)

    private fun ComposeUiTest.settled() = settle(work, state) { session.assembly.output.value?.part(FRONT_LEG_L) != null }

    private fun ComposeUiTest.shown(view: View = View.SIDE, around: PartId = TORSO): LockedCamera {
        settled()
        val part = assertNotNull(character.part(around))
        val camera = LockedCamera(view, SheetPart(part).axes, SheetFit(assertNotNull(part.box).center, PIXELS_PER_UNIT, 4f), PANE_WIDTH, PANE_HEIGHT, part.origin)
        if (around == TORSO) {
            val facing = listOf(FRONT_LEG_L, FRONT_LEG_R).maxBy { assertNotNull(character.part(it)?.anchor) dot camera.basis.back }
            dragged = facing
        }
        showPane(tool, camera)
        return camera
    }

    /** The front leg whose socket the pane sees, on the torso's side toward the viewer. */
    private var dragged = FRONT_LEG_L

    private fun leg(): PlacedPart = assertNotNull(character.part(dragged))
    private fun anchor(): Vec3 = assertNotNull(leg().anchor)
    private fun socket(): SocketHandle = moveHandles(character, null).filterIsInstance<SocketHandle>().first { it.child == dragged }
    private fun steps() = session.history.steps.value.done.size

    private fun torsoParam(name: String): Float? = session.document.value.character.resolved(TORSO)?.params?.let { it[name] }
    private fun storedU(): Float? = torsoParam(PlacementParams.socketU("front-leg", leg().side)) ?: torsoParam(PlacementParams.socketU("front-leg"))

    /** Presses at the first of [path] and moves through the rest holding [keys], runs [midway] halfway, then lets go. */
    private fun ComposeUiTest.drag(path: List<Offset>, keys: List<Key> = emptyList(), midway: () -> Unit = {}) {
        val node = onNodeWithTag(PANE_TAG)
        val half = path.size / 2
        fun moves(rest: List<Key>) {
            if (rest.isNotEmpty()) {
                node.performKeyInput { withKeyDown(rest.first()) { moves(rest.drop(1)) } }
                return
            }
            node.performMouseInput {
                moveTo(path.first())
                press()
                path.subList(1, half).forEach { moveTo(it) }
            }
            midway()
            node.performMouseInput {
                path.drop(half).forEach { moveTo(it) }
                release()
            }
        }
        moves(keys)
        waitForIdle()
        settled()
    }

    /** From [start] by [by] in [count] even steps, swerving [swerve] pixels square to it each other step. */
    private fun path(start: Offset, by: Offset, count: Int = 10, swerve: Float = 0f): List<Offset> {
        val across = Offset(-by.y, by.x).let { if (it.getDistance() == 0f) it else it / it.getDistance() }
        return (0..count).map { i -> start + by * (i.toFloat() / count) + across * (if (i % 2 == 1 && i < count) swerve else 0f) }
    }

    @Test
    fun shiftHoldsTheDragToTheAxisItMovesAlongMostAndTheOtherStaysExactly() = runComposeUiTest {
        val camera = shown()
        val before = anchor()
        val beforeU = socket().place?.u
        val dot = camera.toPixel(before)
        val steps = steps()
        drag(path(dot, Offset(3f, -40f), swerve = 5f), listOf(Key.ShiftLeft))
        assertEquals(steps + 1, steps(), "one undo step")
        val after = anchor()
        assertNotEquals(before.y, after.y, "the socket went down the torso's side")
        assertEquals(beforeU, storedU(), "the place along the torso is written back as it was")
        assertEquals(before.x, after.x, "and the body's x has not moved at all")

        val height = after.y
        val from = camera.toPixel(after)
        drag(path(from, Offset(-25f, 3f), swerve = 5f), listOf(Key.ShiftLeft))
        val across = anchor()
        assertTrue(abs(across.x - after.x) > 0.04f, "the socket went along the torso: $across")
        assertEquals(height, across.y, 1e-5f, "at the height it had")
        assertTrue(abs(camera.toPixel(across).y - from.y) < 0.01f, "on the pane's line it started on")
    }

    @Test
    fun xYAndZHoldTheDragToTheBodysAxesAsThePaneShowsThem() = runComposeUiTest {
        val camera = shown()
        val before = anchor()
        val dot = camera.toPixel(before)
        drag(path(dot, Offset(30f, -30f)), midway = { state.move.holdAxis(BodyAxis.Y) })
        val upright = anchor()
        assertEquals(before.x, upright.x, "Y keeps x exactly")
        assertNotEquals(before.y, upright.y)
        assertNull(state.move.axis.value, "the axis goes with the drag")

        val from = camera.toPixel(upright)
        drag(path(from, Offset(-20f, -25f)), midway = { state.move.holdAxis(BodyAxis.X) })
        val forward = anchor()
        assertEquals(upright.y, forward.y, 1e-5f, "X keeps the height")
        assertTrue(abs(forward.x - upright.x) > 0.03f)

        val last = camera.toPixel(forward)
        drag(path(last, Offset(20f, 20f)), midway = { state.move.holdAxis(BodyAxis.Z) })
        val free = anchor()
        assertTrue(abs(free.x - forward.x) > 1e-3f && abs(free.y - forward.y) > 1e-3f, "Z runs at the viewer in Side, so the drag stays free: $free")
    }

    @Test
    fun aSocketSnapsToItsParentsMidlineMiddleAndEndsUnlessAltIsHeld() = runComposeUiTest {
        val camera = shown()
        val torso = assertNotNull(character.part(TORSO))
        val view = LockedHandleView(camera)
        val dot = camera.toPixel(anchor())

        val onMidline = camera.toPixel(assertNotNull(axisMiddle(torso, 0.65f))) + Offset(0f, OFF_LINE)
        drag(path(dot, onMidline - dot))
        val landed = camera.toPixel(anchor())
        val midline = (0..200).mapNotNull { axisMiddle(torso, it / 200f)?.let(camera::toPixel) }
        assertTrue(distanceTo(midline, landed) < 0.5f, "on the midline: ${distanceTo(midline, landed)} px")

        session.history.undo()
        settled()
        val start = assertNotNull(socket().startPlace(view))
        val nearMiddle = camera.toPixel(assertNotNull(torso.pointOn(0.5f, start.v))) + Offset(OFF_LINE, 0f)
        drag(path(dot, nearMiddle - dot))
        assertEquals(0.5f, storedU(), "on the torso's middle ring exactly")

        session.history.undo()
        settled()
        val nearEnd = camera.toPixel(assertNotNull(torso.pointOn(1f, start.v))) + Offset(-OFF_LINE, 0f)
        drag(path(dot, nearEnd - dot))
        assertEquals(1f, storedU(), "on the torso's end ring exactly")

        session.history.undo()
        settled()
        drag(path(dot, nearMiddle - dot), listOf(Key.AltLeft))
        val free = assertNotNull(storedU())
        assertNotEquals(0.5f, free, "Alt leaves the snaps out")
        assertTrue(abs(camera.toPixel(anchor()).x - nearMiddle.x) < 1f, "and the socket lands where it was let go")
    }

    @Test
    fun ctrlDragsAtAFifthOfThePointersSpeed() = runComposeUiTest {
        val camera = shown()
        val dot = camera.toPixel(anchor())
        drag(path(dot, Offset(-50f, 0f), count = 20), listOf(Key.CtrlLeft, Key.AltLeft))
        val moved = camera.toPixel(anchor()) - dot
        assertEquals(-50f * FINE_DRAG_SPEED, moved.x, 0.5f, "a fifth of the way: $moved")
        assertEquals(0f, moved.y, 0.5f)
    }

    @Test
    fun addJointDragsTheKneeAndSnapsItToTheLegsMiddle() = runComposeUiTest {
        state.selectedPart.value = FRONT_LEG_L
        state.move.mode.value = MoveMode.ADD_JOINT
        val camera = shown(around = FRONT_LEG_L)
        val knee = moveHandles(character, FRONT_LEG_L).filterIsInstance<JointHandle>().single()
        val middle = camera.toPixel(assertNotNull(axisMiddle(leg(), 0.5f)))
        val dot = camera.toPixel(knee.position)
        val along = assertNotNull(axisMiddle(leg(), 0.9f)).let(camera::toPixel).minus(middle).let { it / it.getDistance() }
        val away = middle + along * (OFF_LINE * 10f)
        drag(path(dot, away - dot, count = 5) + path(away, middle + along * OFF_LINE - away, count = 5).drop(1))
        assertEquals(JointRef(FRONT_LEG_L, knee.name), state.move.pickedJoint.value, "the press picks it")
        assertEquals(0.5f, session.document.value.character.resolved(FRONT_LEG_L)?.params?.get(PlacementParams.joint(knee.name)), "and the drag lands it on the middle")
    }

    @Test
    fun arrowNudgesMoveTheSelectedPartAStepAcrossThePaneEachOneUndoStep() = runComposeUiTest {
        settled()
        state.selectedPart.value = FRONT_LEG_L
        val before = anchor()
        val right = SheetPart(leg()).axes.basis(View.SIDE).screenRight
        val steps = steps()
        assertTrue(state.nudgeSelected(View.SIDE, NUDGE_STEP, 0f))
        settled()
        val small = anchor()
        assertEquals(steps + 1, steps())
        assertEquals(NUDGE_STEP, (small - before) dot right, NUDGE_STEP * 0.05f, "one step across: ${small - before}")
        assertTrue(state.nudgeSelected(View.SIDE, NUDGE_STEP_LARGE, 0f))
        settled()
        val far = anchor()
        assertEquals(steps + 2, steps())
        assertEquals(NUDGE_STEP_LARGE, (far - small) dot right, NUDGE_STEP_LARGE * 0.05f, "a large step across")
        session.history.undo()
        settled()
        assertEquals(small, anchor(), "undo takes back the large step alone")
        session.history.undo()
        settled()
        assertEquals(before, anchor(), "and the small one")
    }

    private fun distanceTo(polyline: List<Offset>, point: Offset): Float = polyline.zipWithNext().minOf { (a, b) ->
        val run = b - a
        val length = run.x * run.x + run.y * run.y
        val t = if (length == 0f) 0f else (((point - a).x * run.x + (point - a).y * run.y) / length).coerceIn(0f, 1f)
        (a + run * t - point).getDistance()
    }
}
