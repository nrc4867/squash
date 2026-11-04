package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LockedCameraTest {
    private val paw = PartAxes.of(Vec3(1f, 0f, 0f))
    private val leg = PartAxes.of(Vec3(0f, -1f, 0f))
    private val tail = PartAxes.of(Vec3(-0.8f, 0.6f, 0f))
    private val ear = PartAxes.of(Vec3(0f, 0.96f, -0.28f))
    private val box = BodyBox(Vec3(0f, -0.25f, -0.5f), Vec3(1f, 0.25f, 0.5f))
    private val rects = sheetPaneRects(1000, 800, 2)
    private val fit = fitSheet(box, paw, rects)
    private val socket = Vec3(0.3f, -0.1f, -0.2f)

    private fun camera(view: View, axes: PartAxes = paw, origin: Vec3 = Vec3.ZERO) = LockedCamera(view, axes, fit, 499, 399, origin)

    private fun near(a: Float, b: Float, slack: Float = 1e-3f) = abs(a - b) <= slack

    private fun assertNear(expected: Vec3, actual: Vec3, slack: Float = 1e-3f) =
        assertTrue((expected - actual).length <= slack, "expected $expected, got $actual")

    @Test
    fun axesAreOrthonormalAndAVerticalAxisTakesUpFromX() {
        assertEquals(Vec3(1f, 0f, 0f), leg.up)
        assertEquals(Vec3(0f, 1f, 0f), paw.up)
        assertNear(Vec3(0f, 0f, -1f), paw.side)
        assertNear(Vec3(0f, 0f, -1f), leg.side)
    }

    @Test
    fun aHorizontalPartHoldsItsViewsPlainlyAndAVerticalOneSwapsTopAndFront() {
        assertEquals(mapOf(BodyView.TOP to View.TOP, BodyView.SIDE to View.SIDE, BodyView.FRONT to View.FRONT), paw.framing)
        assertEquals(mapOf(BodyView.TOP to View.FRONT, BodyView.SIDE to View.SIDE, BodyView.FRONT to View.TOP), leg.framing)
        assertEquals(leg.framing, ear.framing)
        assertEquals(paw.framing, tail.framing, "a tail tilted under 45 degrees stays a horizontal part")
    }

    @Test
    fun aPaneKeepsBodyUpOnScreenWithForwardToTheLeft() {
        for (axes in listOf(paw, leg, tail, ear)) {
            val side = camera(View.SIDE, axes)
            assertTrue(side.toPixel(Vec3(0.1f, 0f, 0f)).x < side.toPixel(Vec3.ZERO).x, "forward is left")
            assertTrue(side.toPixel(Vec3(0f, 0.1f, 0f)).y < side.toPixel(Vec3.ZERO).y, "up is up")
        }
    }

    @Test
    fun aLegRunsDownSideAndItsBodyForwardRunsLeft() {
        val camera = camera(View.SIDE, leg)
        val top = camera.toPixel(CurvePoint(0f, 0f))
        val down = camera.toPixel(CurvePoint(0.5f, 0f))
        val forward = camera.toPixel(CurvePoint(0f, 0.25f))
        assertTrue(near(top.x, down.x) && down.y > top.y, "along runs down the screen: $top to $down")
        assertTrue(near(top.y, forward.y) && forward.x < top.x, "up, body forward, runs left: $top to $forward")
    }

    @Test
    fun anOutlinePointRoundTripsForHorizontalVerticalTiltedAndMirroredParts() {
        val mirrored = PartAxes.of(Vec3(0f, -1f, 0f), mirrored = true)
        for (axes in listOf(paw, leg, tail, ear, mirrored)) {
            for (view in View.entries) {
                val camera = camera(view, axes, socket)
                val pixel = Offset(120f, 310f)
                val plane = camera.toPlane(pixel)
                val back = camera.toPixel(plane)
                assertTrue(near(pixel.x, back.x, 0.02f) && near(pixel.y, back.y, 0.02f), "$axes $view: $pixel came back as $back")
                val point = CurvePoint(0.4f, -0.15f)
                val again = camera.toPlane(camera.toPixel(point))
                assertTrue(near(point.x, again.x) && near(point.y, again.y), "$axes $view: $point came back as $again")
            }
        }
    }

    @Test
    fun aPlanePointLiesAlongTheViewsAxesFromTheSocket() {
        val camera = camera(View.FRONT, leg, socket)
        assertNear(socket + leg.side * 0.2f + leg.up * 0.1f, camera.toWorld(CurvePoint(0.2f, 0.1f)))
        assertEquals(camera.toPixel(socket), camera.toPixel(CurvePoint(0f, 0f)))
    }

    @Test
    fun aMirroredPartsTopIsReflectedOnScreen() {
        val left = PartAxes.of(Vec3(1f, 0f, 0f))
        val right = PartAxes.of(Vec3(1f, 0f, 0f), mirrored = true)
        val point = CurvePoint(0.2f, 0.3f)
        val onLeft = camera(View.TOP, left).toPixel(point)
        val onRight = camera(View.TOP, right).toPixel(point)
        assertTrue(near(onLeft.x, onRight.x))
        assertTrue(near((onLeft.y + onRight.y) / 2f, camera(View.TOP, left).toPixel(CurvePoint(0.2f, 0f)).y), "$onLeft and $onRight")
    }

    @Test
    fun thePartsMiddleIsAtEveryPanesMiddle() {
        for (view in View.entries) {
            val middle = camera(view).toPixel(box.center)
            assertTrue(near(middle.x, 499 / 2f) && near(middle.y, 399 / 2f), "$view: $middle")
        }
    }

    @Test
    fun onePartUnitIsTheSameInEveryPane() {
        val lengths = View.entries.map { view ->
            val camera = camera(view)
            (camera.toPixel(CurvePoint(1f, 0f)) - camera.toPixel(CurvePoint(0f, 0f))).getDistance()
        }
        assertTrue(lengths.all { near(it, fit.pixelsPerUnit) }, "$lengths at ${fit.pixelsPerUnit}")
    }

    @Test
    fun theShapeFitsTheTightestPaneDirectionWithRoomAroundIt() {
        val tight = fitSheet(box, paw, sheetPaneRects(2000, 200, 0))
        assertTrue(near(tight.pixelsPerUnit, 100 * 0.6f / 1f), "height limits it: ${tight.pixelsPerUnit}")
    }

    @Test
    fun sideLinesUpWithTopInItsColumnAndFrontInItsRowForAPartSquareToTheBody() {
        val point = Vec3(0.8f, 0.2f, 0.1f)
        for (axes in listOf(paw, leg)) {
            val side = camera(View.SIDE, axes)
            val above = camera(axes.framing.getValue(BodyView.TOP), axes)
            val beside = camera(axes.framing.getValue(BodyView.FRONT), axes)
            assertTrue(near(side.toPixel(point).x, above.toPixel(point).x), "same column")
            assertTrue(near(side.toPixel(point).y, beside.toPixel(point).y), "same row")
        }
    }

    @Test
    fun theMatrixAgreesWithThePixelMappingForEveryPart() {
        for (axes in listOf(paw, leg, tail, ear, PartAxes.of(Vec3(0f, 0.96f, -0.28f), mirrored = true))) {
            for (view in View.entries) {
                val camera = camera(view, axes)
                val point = Vec3(0.7f, 0.1f, -0.2f)
                val clip = camera.viewProjection().project(point.toGpu())!!
                val x = (clip.x + 1f) / 2f * camera.width
                val y = (1f - clip.y) / 2f * camera.height
                val pixel = camera.toPixel(point)
                assertTrue(near(x, pixel.x, 0.05f) && near(y, pixel.y, 0.05f), "$view: $x, $y and $pixel")
                assertTrue(clip.z in 0f..1f, "$view: inside the depth range: ${clip.z}")
            }
        }
    }

    private val tilted = listOf(
        tail,
        ear,
        PartAxes.of(Vec3(-0.8f, 0.6f, 0f), mirrored = true),
        PartAxes.of(Vec3(0f, 0.96f, -0.28f), mirrored = true),
    )

    @Test
    fun everyPaneLooksStraightAlongItsViewsNormalSoTheMapIsARotationAndAScale() {
        for (axes in tilted + listOf(paw, leg)) {
            for (view in View.entries) {
                val camera = camera(view, axes, socket)
                val basis = camera.basis
                val normal = basis.xAxis cross basis.yAxis
                assertNear(basis.back * (basis.back dot normal), normal)
                assertTrue((basis.back dot axes.bodyViewOf(view).toViewer) > 0f, "$axes $view: seen from its pane's side")
                val zero = camera.toPixel(CurvePoint(0f, 0f))
                val x = camera.toPixel(CurvePoint(1f, 0f)) - zero
                val y = camera.toPixel(CurvePoint(0f, 1f)) - zero
                val scale = fit.pixelsPerUnit
                assertTrue(near(x.getDistance(), scale, 0.02f) && near(y.getDistance(), scale, 0.02f), "$axes $view: $x, $y at $scale")
                assertTrue(near(x.x * y.x + x.y * y.y, 0f, 0.5f), "$axes $view: square: $x, $y")
            }
        }
    }

    @Test
    fun aTiltedPaneKeepsBodyUpAsUpAsThePlaneLetsItBe() {
        for (axes in tilted) {
            for (view in View.entries) {
                val pane = axes.bodyViewOf(view)
                val basis = axes.basis(view)
                assertTrue(near(basis.screenRight dot pane.up, 0f), "$axes $view: up tips neither way: ${basis.screenRight}")
                assertTrue((basis.screenUp dot pane.up) > 0.5f, "$axes $view: ${basis.screenUp}")
                assertTrue((basis.screenRight dot pane.right) > 0.5f, "$axes $view: ${basis.screenRight}")
            }
        }
    }

    @Test
    fun aPartSquareToTheBodyHasThePlainBodyViews() {
        for (axes in listOf(paw, leg)) {
            for (view in View.entries) {
                val pane = axes.bodyViewOf(view)
                val basis = axes.basis(view)
                assertNear(pane.right, basis.screenRight)
                assertNear(pane.up, basis.screenUp)
                assertNear(pane.toViewer, basis.back)
            }
        }
    }

    @Test
    fun theTailRunsDiagonallyInSideAndStraightAcrossInItsOtherPlane() {
        val side = camera(View.SIDE, tail).basis
        assertNear(BodyView.SIDE.right, side.screenRight)
        assertNear(BodyView.SIDE.up, side.screenUp)
        val top = camera(View.TOP, tail).basis
        assertNear(tail.along, top.screenRight)
        assertNear(BodyView.TOP.up, top.screenUp)
        val front = camera(View.FRONT, tail).basis
        assertNear(tail.up, front.screenUp)
    }

    @Test
    fun theEarTiltsInItsSideAndFrontAndItsTopRunsDiagonally() {
        val side = camera(View.SIDE, ear).basis
        assertNear(ear.along, side.screenUp)
        assertNear(BodyView.SIDE.right, side.screenRight)
        val top = camera(View.TOP, ear).basis
        assertNear(BodyView.FRONT.up, top.screenUp)
        assertNear(BodyView.FRONT.right, top.screenRight)
        val front = camera(View.FRONT, ear).basis
        assertNear(BodyView.TOP.right, front.screenRight)
        assertNear(ear.side, front.screenUp)
    }

    @Test
    fun aTiltedPartsPanesShowItsMiddleAndAllOfItsBox() {
        for (axes in tilted) {
            val own = fitSheet(box, axes, rects)
            for (pane in SheetPane.entries) {
                val body = pane.body ?: continue
                val rect = rects[pane.ordinal]
                val camera = LockedCamera(axes.framing.getValue(body), axes, own, rect.width, rect.height)
                val middle = camera.toPixel(box.center)
                assertTrue(near(middle.x, rect.width / 2f) && near(middle.y, rect.height / 2f), "$axes $pane: $middle")
                for (corner in box.corners) {
                    val at = camera.toPixel(corner)
                    assertTrue(at.x in 0f..rect.width.toFloat() && at.y in 0f..rect.height.toFloat(), "$axes $pane: $corner at $at")
                }
            }
            assertSame(own, keepFit(own, box, axes, rects))
        }
    }

    @Test
    fun theDepthRangeHoldsTheWholeCharacterHoweverCloseThePaneIs() {
        val close = SheetFit(Vec3.ZERO, 2000f, reach = 3f)
        val camera = LockedCamera(View.SIDE, paw, close, 500, 400)
        for (z in listOf(-3f, 3f)) {
            val clip = camera.viewProjection().project(Vec3(0f, 0f, z).toGpu())!!
            assertTrue(clip.z in 0f..1f, "$z: ${clip.z}")
        }
    }

    @Test
    fun theFitHoldsWhileThePartStaysInView() {
        val kept = keepFit(fit, BodyBox(Vec3(0.1f, -0.2f, -0.4f), Vec3(0.9f, 0.2f, 0.4f)), paw, rects)
        assertSame(fit, kept)
        val moved = keepFit(fit, BodyBox(Vec3(-3f, -0.25f, -0.5f), Vec3(5f, 0.25f, 0.5f)), paw, rects)
        assertNotSame(fit, moved)
        assertTrue(moved.pixelsPerUnit < fit.pixelsPerUnit)
    }

    @Test
    fun theFitMovesOnWhenTheCharacterOutgrowsItsDepth() {
        assertNotSame(fit, keepFit(fit, box, paw, rects, reach = fit.reach + 1f))
    }

    @Test
    fun aShortenedPartShowsItsStoredOutlineOnItsMesh() {
        val short = LockedCamera(View.SIDE, leg, fit, 499, 399, socket, drawingScale = 2f, lengthFactor = 0.5f)
        assertNear(socket + leg.along * 0.4f + leg.up * 0.2f, short.toWorld(CurvePoint(0.4f, 0.1f)))
        val pixel = short.toPixel(CurvePoint(0.31f, -0.07f))
        assertTrue(near(0.31f, short.toPlane(pixel).x, 1e-4f) && near(-0.07f, short.toPlane(pixel).y, 1e-4f), "there and back")
        assertTrue(near(short.planePixel * 2f, short.planePixelX, 1e-6f), "a pixel holds more of x than of y")
        val front = LockedCamera(View.FRONT, leg, fit, 499, 399, socket, lengthFactor = 0.5f)
        assertNear(socket + leg.side * 0.4f + leg.up * 0.1f, front.toWorld(CurvePoint(0.4f, 0.1f)))
    }
}
