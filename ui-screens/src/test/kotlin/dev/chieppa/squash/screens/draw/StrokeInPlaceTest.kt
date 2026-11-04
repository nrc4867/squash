package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.TEST_STROKE_LOOK
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneViewport
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.SheetPart
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.test.setStretchContent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val TAG = "pane"
private const val WIDTH = 800
private const val HEIGHT = 700
private const val SCALE = 500f
private const val SETTLE_MILLIS = 20_000L

/** How far the mesh may stray from the stroke on screen: the smoothing, and rings a span apart. */
private const val SLACK_PIXELS = 6f

private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun boundsOf(points: List<Offset>) = Bounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

/** The test that matters: a stroke through the pointer in a real character's pane, ending as a mesh under that stroke on screen. */
@OptIn(ExperimentalTestApi::class)
class StrokeInPlaceTest {
    private val torso = PartInstance(PartId("torso"), ref("torso"))
    private val legs = PairId("legs")
    private val leftLeg = PartInstance(PartId("left"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = legs, side = Side.LEFT)
    private val rightLeg = PartInstance(PartId("right"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = legs, side = Side.RIGHT)

    private fun ref(id: String) = shipped.parts.first { it.id == TemplateId(id) }.ref

    private inner class Bench {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val plan = shipped.plans.first()
        val session = CharacterSession(
            SquashDocument(Character(CharacterId("dog"), "Dog", BodyPlanRef(plan.id, plan.version))),
            TemplateLibrary(shipped.parts, shipped.plans),
            scope,
        ).also {
            it.send(AddPart(torso))
            it.send(AddParts(listOf(leftLeg, rightLeg)))
        }
        val state = AppState(shipped).also { it.session.value = session }
        val tool = DrawOutlineTool(state, TEST_STROKE_LOOK, TEST_CURVE_LOOK)

        fun ComposeUiTest.settled() = waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && session.assembly.output.value != null }

        fun part(id: PartId) = SheetPart(assertNotNull(session.assembly.output.value?.part(id)))

        fun camera(part: SheetPart, view: View): LockedCamera {
            val fit = SheetFit(part.box.center, SCALE, part.box.radius * 4f)
            return LockedCamera(view, part.axes, fit, WIDTH, HEIGHT, part.origin)
        }

        fun ComposeUiTest.show(camera: LockedCamera) {
            setStretchContent {
                with(density) {
                    Box(Modifier.size(WIDTH.toDp(), HEIGHT.toDp())) {
                        PaneViewport(camera.view, null, tool.pointerInput(camera.view, camera).testTag(TAG))
                    }
                }
            }
        }

        fun ComposeUiTest.stroke(path: List<Offset>) {
            onNodeWithTag(TAG).performMouseInput {
                moveTo(path.first())
                press()
                path.drop(1).forEach { moveTo(it) }
                release()
            }
            waitForIdle()
        }

        /** Every vertex of [part] as [camera] shows it. */
        fun onScreen(part: SheetPart, camera: LockedCamera): List<Offset> =
            (0 until part.mesh.vertexCount).map { camera.toPixel(part.origin + part.mesh.position(it)) }

        fun close() = scope.cancel()
    }

    private fun ellipse(center: Offset, radiusX: Float, radiusY: Float, points: Int = 160): List<Offset> = List(points) {
        val angle = 2.0 * PI * it / (points - 1)
        center + Offset(radiusX * cos(angle).toFloat(), radiusY * sin(angle).toFloat())
    }

    private fun assertLandsOn(stroke: Bounds, mesh: Bounds) {
        assertTrue(abs(stroke.left - mesh.left) < SLACK_PIXELS, "left: stroke $stroke, mesh $mesh")
        assertTrue(abs(stroke.right - mesh.right) < SLACK_PIXELS, "right: stroke $stroke, mesh $mesh")
        assertTrue(abs(stroke.top - mesh.top) < SLACK_PIXELS, "top: stroke $stroke, mesh $mesh")
        assertTrue(abs(stroke.bottom - mesh.bottom) < SLACK_PIXELS, "bottom: stroke $stroke, mesh $mesh")
    }

    @Test
    fun aStrokeInAnUprightSideBuildsTheLegUnderIt() = runComposeUiTest {
        val bench = Bench()
        with(bench) {
            settled()
            state.selectedPart.value = leftLeg.id
            val leg = part(leftLeg.id)
            val camera = camera(leg, View.SIDE)
            assertEquals(BodyView.SIDE, leg.axes.bodyViewOf(View.SIDE))
            val socket = camera.toPixel(leg.origin)
            show(camera)
            // Off to the leg's forward side, and crossing the socket plane near its top.
            val path = ellipse(socket + Offset(-40f, 110f), 60f, 150f)
            stroke(path)
            settled()
            assertNotNull(session.document.value.character.resolved(leftLeg)?.outlines?.get(View.SIDE))

            val drawn = part(leftLeg.id)
            assertEquals(leg.origin, drawn.origin, "the stroke's plane held still")
            val mesh = boundsOf(onScreen(drawn, camera))
            val all = boundsOf(path)
            assertLandsOn(Bounds(all.left, socket.y, all.right, all.bottom), mesh)
        }
        bench.close()
    }

    @Test
    fun theStrokeCutsAtTheSocketPlaneOnTheSideTheLegHangsFrom() = runComposeUiTest {
        val bench = Bench()
        with(bench) {
            settled()
            state.selectedPart.value = leftLeg.id
            val leg = part(leftLeg.id)
            val camera = camera(leg, View.SIDE)
            val socket = camera.toPixel(leg.origin)
            show(camera)
            stroke(ellipse(socket + Offset(0f, 60f), 50f, 150f))
            settled()
            val outline = assertNotNull(session.document.value.character.resolved(leftLeg)?.outlines?.get(View.SIDE))
            assertTrue(outline.points.all { it.x >= -1e-3f }, "all of it runs down the leg from the plane")
            val onPlane = listOf(outline.points.first(), outline.points.last())
            assertTrue(onPlane.all { abs(it.x) < 1e-3f }, "both ends on the plane: $onPlane")
            assertTrue(outline.points.maxOf { it.x } > 0.3f, "the part below the plane is what stays")
        }
        bench.close()
    }

    @Test
    fun aStrokeInTheLegsTopBesideSideSetsItsWidthWhereItWasDrawn() = runComposeUiTest {
        val bench = Bench()
        with(bench) {
            settled()
            state.selectedPart.value = leftLeg.id
            val leg = part(leftLeg.id)
            assertEquals(BodyView.FRONT, leg.axes.bodyViewOf(View.TOP), "a vertical part's Top sits beside Side")
            val camera = camera(leg, View.TOP)
            val socket = camera.toPixel(leg.origin)
            show(camera)
            val path = ellipse(socket + Offset(0f, 110f), 55f, 150f)
            stroke(path)
            settled()
            val mesh = boundsOf(onScreen(part(leftLeg.id), camera))
            val all = boundsOf(path)
            assertLandsOn(Bounds(all.left, socket.y, all.right, all.bottom), mesh)
        }
        bench.close()
    }

    @Test
    fun aStrokeOnTheBodyBuildsUnderItWithForwardToTheLeft() = runComposeUiTest {
        val bench = Bench()
        with(bench) {
            settled()
            state.selectedPart.value = torso.id
            val body = part(torso.id)
            val camera = camera(body, View.SIDE)
            show(camera)
            val middle = Offset(WIDTH / 2f, HEIGHT / 2f)
            val path = ellipse(middle, 300f, 110f)
            stroke(path)
            settled()
            assertLandsOn(boundsOf(path), boundsOf(onScreen(part(torso.id), camera)))
            val leg = part(leftLeg.id)
            val neck = camera.toPixel(assertNotNull(session.assembly.output.value?.sockets?.first { it.ref.socket == "neck" }).position)
            val tail = camera.toPixel(assertNotNull(session.assembly.output.value?.sockets?.first { it.ref.socket == "tail" }).position)
            assertTrue(neck.x < tail.x, "the neck is toward the left of the pane")
            assertTrue(camera.toPixel(leg.origin).y > middle.y, "the legs moved with the drawn body and hang under it")
        }
        bench.close()
    }

    @Test
    fun aStrokeLandsUnderItAtAnySize() = runComposeUiTest {
        val bench = Bench()
        with(bench) {
            session.send(SetParameter(leftLeg.id, SIZE_PARAM, 1.5f))
            settled()
            state.selectedPart.value = leftLeg.id
            val leg = part(leftLeg.id)
            val plain = camera(leg, View.SIDE)
            val camera = LockedCamera(View.SIDE, leg.axes, plain.fit, WIDTH, HEIGHT, leg.origin, drawingScale = 1.5f)
            val socket = camera.toPixel(leg.origin)
            show(camera)
            val path = ellipse(socket + Offset(0f, 110f), 60f, 150f)
            stroke(path)
            settled()
            val all = boundsOf(path)
            assertLandsOn(Bounds(all.left, socket.y, all.right, all.bottom), boundsOf(onScreen(part(leftLeg.id), camera)))
        }
        bench.close()
    }

    @Test
    fun aStrokeOnTheFarSideLandsUnderItToo() = runComposeUiTest {
        val bench = Bench()
        with(bench) {
            settled()
            state.selectedPart.value = rightLeg.id
            val leg = part(rightLeg.id)
            val camera = camera(leg, View.TOP)
            val socket = camera.toPixel(leg.origin)
            show(camera)
            val path = ellipse(socket + Offset(35f, 110f), 55f, 150f)
            stroke(path)
            settled()
            val mesh = boundsOf(onScreen(part(rightLeg.id), camera))
            val all = boundsOf(path)
            assertTrue(abs(mesh.top - socket.y) < SLACK_PIXELS, "${mesh.top} and ${socket.y}")
            assertTrue(abs(mesh.bottom - all.bottom) < SLACK_PIXELS, "${mesh.bottom} and ${all.bottom}")
            assertTrue(abs(max(mesh.left, all.left) - min(mesh.left, all.left)) < 60f, "$mesh and $all")
        }
        bench.close()
    }

}
