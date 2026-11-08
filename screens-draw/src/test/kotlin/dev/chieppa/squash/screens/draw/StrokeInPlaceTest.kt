package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.screens.draw.StrokeParts.leftLeg
import dev.chieppa.squash.screens.draw.StrokeParts.rightLeg
import dev.chieppa.squash.screens.draw.StrokeParts.torso
import dev.chieppa.squash.screens.sheet.LockedCamera
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

private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun boundsOf(points: List<Offset>) = Bounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

/** The test that matters: a stroke through the pointer in a real character's pane, ending as a mesh under that stroke on screen. */
@OptIn(ExperimentalTestApi::class)
class StrokeInPlaceTest {
    private fun ellipse(center: Offset, radiusX: Float, radiusY: Float, points: Int = 160): List<Offset> = List(points) {
        val angle = 2.0 * PI * it / (points - 1)
        center + Offset(radiusX * cos(angle).toFloat(), radiusY * sin(angle).toFloat())
    }

    private fun assertLandsOn(stroke: Bounds, mesh: Bounds) {
        assertTrue(abs(stroke.left - mesh.left) < BENCH_SLACK_PIXELS, "left: stroke $stroke, mesh $mesh")
        assertTrue(abs(stroke.right - mesh.right) < BENCH_SLACK_PIXELS, "right: stroke $stroke, mesh $mesh")
        assertTrue(abs(stroke.top - mesh.top) < BENCH_SLACK_PIXELS, "top: stroke $stroke, mesh $mesh")
        assertTrue(abs(stroke.bottom - mesh.bottom) < BENCH_SLACK_PIXELS, "bottom: stroke $stroke, mesh $mesh")
    }

    @Test
    fun aStrokeInAnUprightSideBuildsTheLegUnderIt() = runComposeUiTest {
        val bench = StrokeBench()
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
    }

    @Test
    fun theStrokeCutsAtTheSocketPlaneOnTheSideTheLegHangsFrom() = runComposeUiTest {
        val bench = StrokeBench()
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
    }

    @Test
    fun aStrokeInTheLegsTopBesideSideSetsItsWidthWhereItWasDrawn() = runComposeUiTest {
        val bench = StrokeBench()
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
    }

    @Test
    fun aStrokeOnTheBodyBuildsUnderItWithForwardToTheLeft() = runComposeUiTest {
        val bench = StrokeBench()
        with(bench) {
            settled()
            state.selectedPart.value = torso.id
            val body = part(torso.id)
            val camera = camera(body, View.SIDE)
            show(camera)
            val middle = Offset(BENCH_WIDTH / 2f, BENCH_HEIGHT / 2f)
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
    }

    @Test
    fun aStrokeLandsUnderItAtAnySize() = runComposeUiTest {
        val bench = StrokeBench()
        with(bench) {
            session.send(SetParameter(leftLeg.id, SIZE_PARAM, 1.5f))
            settled()
            state.selectedPart.value = leftLeg.id
            val leg = part(leftLeg.id)
            val plain = camera(leg, View.SIDE)
            val camera = LockedCamera(View.SIDE, leg.axes, plain.fit, BENCH_WIDTH, BENCH_HEIGHT, leg.origin, drawingScale = 1.5f)
            val socket = camera.toPixel(leg.origin)
            show(camera)
            val path = ellipse(socket + Offset(0f, 110f), 60f, 150f)
            stroke(path)
            settled()
            val all = boundsOf(path)
            assertLandsOn(Bounds(all.left, socket.y, all.right, all.bottom), boundsOf(onScreen(part(leftLeg.id), camera)))
        }
    }

    @Test
    fun aStrokeOnTheFarSideLandsUnderItToo() = runComposeUiTest {
        val bench = StrokeBench()
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
            assertTrue(abs(mesh.top - socket.y) < BENCH_SLACK_PIXELS, "${mesh.top} and ${socket.y}")
            assertTrue(abs(mesh.bottom - all.bottom) < BENCH_SLACK_PIXELS, "${mesh.bottom} and ${all.bottom}")
            assertTrue(abs(max(mesh.left, all.left) - min(mesh.left, all.left)) < 60f, "$mesh and $all")
        }
    }

}
