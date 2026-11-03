package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneViewport
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.stretch.test.setStretchContent
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val TAG = "pane"
private const val PANE_WIDTH = 600
private const val PANE_HEIGHT = 400
private const val SCALE = 100f

@OptIn(ExperimentalTestApi::class)
class DrawOutlineToolTest {
    private val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))
    private val look = StrokeLook(Color.Black, 7f, Color.Yellow, 8f, 3f)
    private val id = PartId("p")

    private class Bench(val state: AppState, val session: CharacterSession, val tool: DrawOutlineTool)

    private fun bench(template: String): Bench {
        val state = AppState(folder)
        val document = SquashDocument(Character(CharacterId("c"), "Fennec", BodyPlanRef(BodyPlanId("p"), 1)))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = CharacterSession(document, TemplateLibrary(folder.parts), scope)
        val found = folder.parts.first { it.id.value == template }
        session.send(AddPart(PartInstance(id, found.ref)))
        state.session.value = session
        state.selectedPart.value = id
        return Bench(state, session, DrawOutlineTool(state, look))
    }

    private fun camera(view: View) = LockedCamera(view, PartAxes.of(Vec3.X), SheetFit(Vec3(1f, 0f, 0f), SCALE), PANE_WIDTH, PANE_HEIGHT)

    private fun ComposeUiTest.pane(tool: DrawOutlineTool, camera: LockedCamera) {
        setStretchContent {
            with(density) {
                Box(Modifier.size(PANE_WIDTH.toDp(), PANE_HEIGHT.toDp())) {
                    PaneViewport(SheetPane.SIDE, null, tool.pointerInput(camera.view, camera).testTag(TAG))
                }
            }
        }
    }

    /** A loop around the plane point ([cx], 0), as pane pixels. */
    private fun loopPixels(camera: LockedCamera, cx: Float, count: Int = 120): List<Offset> = List(count) {
        val angle = 2.0 * PI * it / (count - 1)
        camera.toPixel(CurvePoint(cx + cos(angle).toFloat(), 0.8f * sin(angle).toFloat()))
    }

    private fun ComposeUiTest.mouseStroke(path: List<Offset>) {
        onNodeWithTag(TAG).performMouseInput {
            moveTo(path.first())
            press()
            path.drop(1).forEach { moveTo(it) }
            release()
        }
        waitForIdle()
    }

    private fun Bench.outline(view: View): OutlineCurve? = session.document.value.character.part(id)?.outlines?.get(view)

    private fun Bench.undoSteps() = session.history.steps.value.done.size

    @Test
    fun aMouseStrokeBecomesOneOutlineAndOneUndoStep() = runComposeUiTest {
        val bench = bench("leg")
        val camera = camera(View.SIDE)
        pane(bench.tool, camera)
        val before = bench.undoSteps()
        mouseStroke(loopPixels(camera, cx = 0.6f))
        val curve = assertNotNull(bench.outline(View.SIDE))
        assertEquals(before + 1, bench.undoSteps())
        assertEquals(0, (curve.points.size - 1) % 3)
        assertEquals(0f, curve.points.first().x, "the leg opens at the socket plane")
        assertEquals(0f, curve.points.last().x)
        assertTrue(curve.points.all { it.x >= -0.05f }, "nothing behind the plane")
        assertNull(bench.tool.liveStroke)
        bench.session.history.undo()
        assertNull(bench.outline(View.SIDE))
    }

    @Test
    fun aSecondStrokeReplacesTheFirstAndUndoesBackToIt() = runComposeUiTest {
        val bench = bench("leg")
        val camera = camera(View.SIDE)
        pane(bench.tool, camera)
        mouseStroke(loopPixels(camera, cx = 0.6f))
        val first = assertNotNull(bench.outline(View.SIDE))
        mouseStroke(loopPixels(camera, cx = 0.2f))
        assertTrue(first != bench.outline(View.SIDE))
        bench.session.history.undo()
        assertEquals(first, bench.outline(View.SIDE))
    }

    @Test
    fun frontClosesBecauseItNeverMeetsTheSocketPlane() = runComposeUiTest {
        val bench = bench("leg")
        val camera = camera(View.FRONT)
        pane(bench.tool, camera)
        mouseStroke(loopPixels(camera, cx = 1f))
        val curve = assertNotNull(bench.outline(View.FRONT))
        assertEquals(curve.points.first(), curve.points.last())
    }

    @Test
    fun aClosedPartStaysClosedInSide() = runComposeUiTest {
        val bench = bench("head")
        val camera = camera(View.SIDE)
        pane(bench.tool, camera)
        mouseStroke(loopPixels(camera, cx = 1f))
        val curve = assertNotNull(bench.outline(View.SIDE))
        assertEquals(curve.points.first(), curve.points.last())
    }

    @Test
    fun aStrokeShowsLiveWithoutTouchingTheDocumentUntilItEnds() = runComposeUiTest {
        val bench = bench("leg")
        val camera = camera(View.SIDE)
        pane(bench.tool, camera)
        val path = loopPixels(camera, cx = 0.6f)
        val before = bench.undoSteps()
        onNodeWithTag(TAG).performMouseInput {
            moveTo(path[0])
            press()
            moveTo(path[1])
        }
        waitForIdle()
        assertEquals(View.SIDE, bench.tool.drawingIn)
        val early = assertNotNull(bench.tool.liveStroke).points.size
        val overlay = bench.tool.overlay(View.SIDE, camera)
        assertTrue(overlay.lines.isNotEmpty() && overlay.dots.isNotEmpty(), "the stroke and its tip")
        assertTrue(bench.tool.overlay(View.TOP, camera(View.TOP)).isEmpty, "only in its own pane")
        val first = assertNotNull(bench.tool.liveStroke).points.first()
        assertEquals(camera.toPlane(path[0]).x, first.x, 1e-3f, "in the view's plane")

        onNodeWithTag(TAG).performMouseInput { moveTo(path[2]) }
        waitForIdle()
        assertTrue(assertNotNull(bench.tool.liveStroke).points.size > early, "it grows with the pointer")
        assertEquals(before, bench.undoSteps())
        assertNull(bench.outline(View.SIDE))

        onNodeWithTag(TAG).performMouseInput {
            path.drop(3).forEach { moveTo(it) }
            release()
        }
        waitForIdle()
        assertNull(bench.tool.drawingIn)
        assertEquals(before + 1, bench.undoSteps())
    }

    @Test
    fun aClickIsNoStroke() = runComposeUiTest {
        val bench = bench("leg")
        val camera = camera(View.SIDE)
        pane(bench.tool, camera)
        val before = bench.undoSteps()
        mouseStroke(listOf(Offset(100f, 100f), Offset(101f, 100f)))
        assertEquals(before, bench.undoSteps())
        assertNull(bench.tool.liveStroke)
    }

    @Test
    fun theRightButtonDoesNotDraw() = runComposeUiTest {
        val bench = bench("leg")
        val camera = camera(View.SIDE)
        pane(bench.tool, camera)
        val path = loopPixels(camera, cx = 0.6f)
        val before = bench.undoSteps()
        onNodeWithTag(TAG).performMouseInput {
            moveTo(path.first())
            press(MouseButton.Secondary)
            path.drop(1).forEach { moveTo(it) }
            release(MouseButton.Secondary)
        }
        waitForIdle()
        assertEquals(before, bench.undoSteps())
    }

    @Test
    fun aPointerThatIsNotAMouseDrawsToo() = runComposeUiTest {
        val bench = bench("leg")
        val camera = camera(View.SIDE)
        pane(bench.tool, camera)
        val path = loopPixels(camera, cx = 0.6f)
        onNodeWithTag(TAG).performTouchInput {
            down(path.first())
            path.drop(1).forEach { moveTo(it) }
            up()
        }
        waitForIdle()
        assertNotNull(bench.outline(View.SIDE))
    }

    @Test
    fun aStrokeThePaneLosesMidwaySendsNothing() = runComposeUiTest {
        val bench = bench("leg")
        val first = camera(View.SIDE)
        var camera by mutableStateOf(first)
        setStretchContent {
            with(density) {
                Box(Modifier.size(PANE_WIDTH.toDp(), PANE_HEIGHT.toDp())) {
                    PaneViewport(SheetPane.SIDE, null, bench.tool.pointerInput(camera.view, camera).testTag(TAG))
                }
            }
        }
        val path = loopPixels(first, cx = 0.6f)
        val before = bench.undoSteps()
        onNodeWithTag(TAG).performMouseInput {
            moveTo(path[0])
            press()
            moveTo(path[1])
        }
        waitForIdle()
        assertNotNull(bench.tool.liveStroke)
        camera = camera(View.SIDE)
        waitForIdle()
        assertNull(bench.tool.liveStroke)
        assertEquals(before, bench.undoSteps())
    }
}
