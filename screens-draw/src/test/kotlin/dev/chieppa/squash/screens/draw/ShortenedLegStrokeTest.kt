package dev.chieppa.squash.screens.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.draw.StrokeParts.leftLeg
import dev.chieppa.squash.screens.draw.StrokeParts.rightLeg
import dev.chieppa.squash.screens.edit.EditCurvesTool
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.lineAcross
import dev.chieppa.squash.screens.sheet.outlineSamples
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val LEAN_DEGREES = 25f
private const val STORED_SLACK = 0.015f

/** A pane on a leg its twin's lean has shortened: strokes land under the pointer and store the unshortened coordinates. */
@OptIn(ExperimentalTestApi::class)
class ShortenedLegStrokeTest {
    /** The left leg shortened by its right twin leaning alone, and a pane on it. */
    private fun ComposeUiTest.shortened(bench: StrokeBench, view: View): LockedCamera = with(bench) {
        session.send(SetParameter(rightLeg.id, PlacementParams.OUTWARD, LEAN_DEGREES, thisSide = true))
        settled()
        state.selectedPart.value = leftLeg.id
        val leg = part(leftLeg.id)
        assertTrue(leg.lengthFactor < 1f)
        camera(leg, view)
    }

    private fun ellipse(center: Offset, radiusX: Float, radiusY: Float, points: Int = 160): List<Offset> = List(points) {
        val angle = 2.0 * PI * it / (points - 1)
        center + Offset(radiusX * cos(angle).toFloat(), radiusY * sin(angle).toFloat())
    }

    @Test
    fun aStrokeInSideLandsUnderThePointerAndStoresUnshortened() = runComposeUiTest {
        val bench = StrokeBench()
        with(bench) {
            val camera = shortened(bench, View.SIDE)
            val socket = camera.toPixel(part(leftLeg.id).origin)
            show(camera)
            val path = ellipse(socket + Offset(0f, 110f), 60f, 150f)
            stroke(path)
            settled()
            val tipPixels = path.maxOf { it.y } - socket.y
            val stored = outlineSamples(outline(leftLeg.id, View.SIDE))
            assertEquals(tipPixels / BENCH_SCALE / camera.lengthFactor, stored.maxOf { it.x }, STORED_SLACK, "the outline holds the leg's full length")
            val mesh = onScreen(part(leftLeg.id), camera)
            assertTrue(abs(mesh.maxOf { it.y } - path.maxOf { it.y }) < BENCH_SLACK_PIXELS, "the leg ends under the pointer")
            assertTrue(abs(mesh.minOf { it.x } - path.minOf { it.x }) < BENCH_SLACK_PIXELS, "and is as wide as it was drawn")
            val shown = stored.map(camera::toPixel)
            assertTrue(abs(shown.maxOf { it.y } - path.maxOf { it.y }) < BENCH_SLACK_PIXELS, "the stored outline reads back under the stroke")
        }
    }

    @Test
    fun aStrokeInTheFrontOutlinesPaneStoresWhatItShows() = runComposeUiTest {
        val bench = StrokeBench()
        with(bench) {
            val camera = shortened(bench, View.FRONT)
            val path = ellipse(Offset(BENCH_WIDTH / 2f, BENCH_HEIGHT / 2f), 50f, 40f)
            show(camera)
            stroke(path)
            settled()
            val stored = outlineSamples(outline(leftLeg.id, View.FRONT))
            val spans = listOf(stored.maxOf { it.x } - stored.minOf { it.x }, stored.maxOf { it.y } - stored.minOf { it.y }).sorted()
            assertEquals(80f / BENCH_SCALE, spans[0], STORED_SLACK, "a Front outline has no length along the axis to shorten")
            assertEquals(100f / BENCH_SCALE, spans[1], STORED_SLACK)
        }
    }

    @Test
    fun aHandleDragFollowsThePointerOnAShortenedLeg() = runComposeUiTest {
        val bench = StrokeBench()
        with(bench) {
            val camera = shortened(bench, View.SIDE)
            val socket = camera.toPixel(part(leftLeg.id).origin)
            showPanes(camera)
            stroke(ellipse(socket + Offset(0f, 110f), 60f, 150f))
            settled()
            val before = outline(leftLeg.id, View.SIDE)
            val tip = before.points.maxBy { it.x }
            val from = camera.toPixel(tip)
            working = EditCurvesTool(state, TEST_CURVE_LOOK)
            waitForIdle()
            val to = from + Offset(0f, 30f)
            stroke(listOf(from, from + Offset(0f, 10f), to))
            settled()
            val moved = outline(leftLeg.id, View.SIDE).points.maxBy { it.x }
            assertEquals(to.y, camera.toPixel(moved).y, 1.5f, "the point stays under the pointer")
            assertEquals(30f / BENCH_SCALE / camera.lengthFactor, moved.x - tip.x, 1e-3f, "and moves further in stored coordinates than on screen")
        }
    }

    @Test
    fun guidesAndTheSocketPlaneSitWhereTheShortenedLegDoes() = runComposeUiTest {
        val bench = StrokeBench()
        with(bench) {
            val camera = shortened(bench, View.SIDE)
            val reach = 0.4f
            val guide = camera.lineAcross(camera.axes.along, reach * camera.alongScale)
            val point = camera.toPixel(CurvePoint(reach, 0f))
            assertEquals(point.y, guide.start.y, 1e-2f, "a guide at a stored reach crosses the axis where that reach shows")
            val back = camera.toPlane(camera.toPixel(CurvePoint(0.31f, -0.07f)))
            assertEquals(0.31f, back.x, 1e-4f, "pixel and plane are one round trip")
            assertEquals(-0.07f, back.y, 1e-4f)
        }
    }
}
